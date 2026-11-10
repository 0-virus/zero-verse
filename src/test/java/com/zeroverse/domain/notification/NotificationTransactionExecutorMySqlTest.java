package com.zeroverse.domain.notification;

import static org.assertj.core.api.Assertions.*;
import com.zeroverse.common.exception.*;
import com.zeroverse.domain.notification.service.NotificationTransactionExecutor;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.*;

class NotificationTransactionExecutorMySqlTest extends NotificationGenerationSupport {
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @Autowired NotificationTransactionExecutor executor;

    @Test void confirmedRollbackAllowsOnlyThreeFreshAttemptsAndLeavesNoSourceRows() {
        var u=user("retry"); List<TransactionStatus> transactions=new ArrayList<>(); AtomicInteger attempts=new AtomicInteger();
        PlatformTransactionManager observed=new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition d) { var status=manager.getTransaction(d); transactions.add(status); return status; }
            public void rollback(TransactionStatus s) { manager.rollback(s); }
            public void commit(TransactionStatus s) { manager.commit(s); }
        };
        var boundary=new NotificationTransactionExecutor(observed,em);
        assertThatThrownBy(()->boundary.execute(()-> {
            attempts.incrementAndGet();
            assertThat(jdbc.queryForObject("SELECT bio FROM users WHERE id=?",String.class,u.getId())).isNull();
            jdbc.update("UPDATE users SET bio='rolled-back' WHERE id=?",u.getId());
            throw new NotificationTransactionExecutor.CandidateChangedException();
        })).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_500));
        assertThat(attempts).hasValue(3);
        assertThat(jdbc.queryForObject("SELECT bio FROM users WHERE id=?",String.class,u.getId())).isNull();
        // Every attempt got a newly begun transaction, never a rollback-only status reused.
        assertThat(transactions).hasSize(3).doesNotHaveDuplicates().allMatch(TransactionStatus::isCompleted);
    }
    @Test void lockFailuresRollbackBeforeRetryAndSuccessfulAttemptCommitsOnlyOnce() {
        var u=user("lock-retry"); AtomicInteger attempts=new AtomicInteger();
        executor.execute(()-> {
            int n=attempts.incrementAndGet(); jdbc.update("UPDATE users SET bio=? WHERE id=?","attempt-"+n,u.getId());
            if(n<3) throw new RuntimeException(new SQLException("synthetic database lock failure","40001",n==1?1213:1205));
            return null;
        });
        assertThat(attempts).hasValue(3);
        assertThat(jdbc.queryForObject("SELECT bio FROM users WHERE id=?",String.class,u.getId())).isEqualTo("attempt-3");
    }
    @Test void businessAndUnknownConnectionErrorsAreNeverReplayed() {
        for(RuntimeException failure:List.of(new BusinessException(ErrorCode.POST_002),new RuntimeException(new SQLException("connection uncertain","08007",0)))) {
            AtomicInteger attempts=new AtomicInteger();
            assertThatThrownBy(()->executor.execute(()->{ attempts.incrementAndGet(); throw failure; })).isSameAs(failure);
            assertThat(attempts).hasValue(1);
        }
    }
    @Test void committedButLostResponseIsNotReplayedEvenWithLockCodeInCause() {
        var u=user("commit-unknown"); AtomicInteger attempts=new AtomicInteger();
        PlatformTransactionManager uncertain=new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition d) { return manager.getTransaction(d); }
            public void rollback(TransactionStatus s) { manager.rollback(s); }
            public void commit(TransactionStatus s) { manager.commit(s); throw new RuntimeException(new SQLException("commit response lost","08007",1213)); }
        };
        var boundary=new NotificationTransactionExecutor(uncertain,em);
        assertThatThrownBy(()->boundary.execute(()->{attempts.incrementAndGet();jdbc.update("UPDATE users SET bio='committed' WHERE id=?",u.getId());return null;})).isInstanceOf(RuntimeException.class);
        assertThat(attempts).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT bio FROM users WHERE id=?",String.class,u.getId())).isEqualTo("committed");
    }
}
