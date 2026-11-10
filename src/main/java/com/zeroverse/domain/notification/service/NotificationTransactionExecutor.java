package com.zeroverse.domain.notification.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/** Only the five M8 source operations use whole-operation, fresh-transaction retries. */
@Component
public class NotificationTransactionExecutor {
    private static final Logger log = LoggerFactory.getLogger(NotificationTransactionExecutor.class);
    private final PlatformTransactionManager transactions;
    private final EntityManager entityManager;

    public NotificationTransactionExecutor(PlatformTransactionManager transactions, EntityManager entityManager) {
        this.transactions = transactions;
        this.entityManager = entityManager;
    }

    public <T> T execute(Supplier<T> action) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
            definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            TransactionStatus status = transactions.getTransaction(definition);
            T result;
            try {
                // OSIV or a caller's prior lookup must never supply stale managed user state.
                entityManager.clear();
                result = action.get();
                entityManager.flush();
            } catch (RuntimeException | Error failure) {
                // A failed/unknown rollback is not evidence that replay is safe.
                transactions.rollback(status);
                if (!(failure instanceof RuntimeException) || !retryable(failure)) throw failure;
                log.info("notification source rollback kind={} attempt={}", kind(failure), attempt);
                if (attempt == 3) throw new BusinessException(ErrorCode.COMMON_500);
                continue;
            }
            // Deliberately outside the retry catch: commit/connection uncertainty must never replay.
            transactions.commit(status);
            return result;
        }
        throw new IllegalStateException("unreachable");
    }

    private static boolean retryable(Throwable failure) {
        if (failure instanceof CandidateChangedException) return true;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof BusinessException) return false;
            if (cause instanceof SQLException sql && (sql.getErrorCode() == 1213 || sql.getErrorCode() == 1205)) return true;
        }
        return false;
    }
    private static String kind(Throwable failure) {
        return failure instanceof CandidateChangedException ? "candidate-change" : "database-lock";
    }
    public static final class CandidateChangedException extends RuntimeException {
        public CandidateChangedException() { super("notification candidates changed"); }
    }
}
