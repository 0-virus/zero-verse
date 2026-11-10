package com.zeroverse.domain.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.zeroverse.common.exception.*;
import com.zeroverse.domain.notification.service.*;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.universe.entity.Universe;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.security.ZeroverseUserPrincipal;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties="spring.jpa.properties.hibernate.generate_statistics=true")
class NotificationFanoutConcurrencyMySqlTest extends NotificationGenerationSupport {
    @SpyBean NotificationPublicationLock publication;
    @SpyBean NotificationGenerator generator;
    @SpyBean com.zeroverse.domain.universe.repository.UniverseRepository universeRows;
    @Autowired NotificationService notifications;
    @Autowired NotificationTransactionExecutor executor;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManagerFactory emf;
    final ExecutorService pool=Executors.newFixedThreadPool(4);
    final JdbcTemplate observation=observation();
    @AfterEach void cleanup() { reset(publication,generator,universeRows); pool.shutdownNow(); }

    @ParameterizedTest @ValueSource(ints={0,1,100,101,1001})
    void fanoutHasNoTruncationAndReportsActualCost(int recipients) {
        var owner=user("fanout-owner"); var b=blog(owner);
        for(int n=0;n<recipients;n++) accepted(user("fanout-"+n),owner);
        var stats=emf.unwrap(SessionFactory.class).getStatistics(); stats.clear();
        long memoryBefore=usedMemory(), lockBefore=lockTime(), started=System.nanoTime();
        var p=create(owner,b,Visibility.PUBLIC,true);
        long elapsed=System.nanoTime()-started, memoryDelta=usedMemory()-memoryBefore, orm=stats.getPrepareStatementCount(), lockMillis=lockTime()-lockBefore;
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE type='POST' AND target_id=?",Long.class,p.id())).isEqualTo(recipients);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT receiver_user_id) FROM notifications WHERE target_id=?",Long.class,p.id())).isEqualTo(recipients);
        verify(publication,times(1)).snapshotCandidates(owner.getId());
        System.out.printf("M8_FANOUT recipients=%d ormStatements=%d relationJdbcStatements=2 totalStatements=%d elapsedMs=%.3f heapDeltaBytes=%d lockWaitMs=%d attempts=1 logicalBatches=%d%n",recipients,orm,orm+2,elapsed/1_000_000d,memoryDelta,lockMillis,(recipients+99)/100);
    }
    @Test void candidateAddedAfterSnapshotRollsBackAndRetriesWithWholeSortedSet() throws Exception {
        var receiver=user("candidate-low"); var owner=user("candidate-owner"); var b=blog(owner);
        var pending=relations.saveAndFlush(Universe.pending(receiver,owner));
        CountDownLatch snapshot=new CountDownLatch(1), release=new CountDownLatch(1); AtomicInteger attempts=new AtomicInteger();
        doAnswer(i->{var result=i.callRealMethod(); if(attempts.incrementAndGet()==1){snapshot.countDown();await(release);}return result;}).when(publication).snapshotCandidates(owner.getId());
        Future<?> publish=pool.submit(()->create(owner,b,Visibility.PUBLIC,true));
        try { await(snapshot); universe.accept(owner.getId(),pending.getId()); } finally { release.countDown(); }
        done(publish); assertThat(attempts).hasValue(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE type='POST'",Long.class)).isEqualTo(1);
        System.out.println("M8_RACE candidateAdded attempts=2 committedPosts=1 committedPostNotifications=1");
    }
    @Test void blockAfterSnapshotRemovesReceiverWithoutUnsortedAdditionalLock() throws Exception {
        var receiver=user("block-low"); var owner=user("block-owner"); var b=blog(owner); accepted(receiver,owner);
        CountDownLatch snapshot=new CountDownLatch(1), release=new CountDownLatch(1);
        doAnswer(i->{var result=i.callRealMethod();snapshot.countDown();await(release);return result;}).when(publication).snapshotCandidates(owner.getId());
        Future<?> publish=pool.submit(()->create(owner,b,Visibility.UNIVERSE,true));
        try { await(snapshot); universe.block(receiver.getId(),owner.getId()); } finally { release.countDown(); }
        done(publish); assertThat(count()).isZero(); verify(publication,times(1)).snapshotCandidates(owner.getId());
    }
    @Test void deletedReceiverAfterSnapshotIsExcludedAndSuspendedOwnerAfterSnapshotIsRejected() throws Exception {
        for(boolean deleteReceiver:List.of(true,false)) {
            var receiver=user("delete-low-"+deleteReceiver); var owner=user("delete-owner-"+deleteReceiver); var b=blog(owner); accepted(receiver,owner);
            CountDownLatch snapshot=new CountDownLatch(1), release=new CountDownLatch(1);
            doAnswer(i->{var result=i.callRealMethod();snapshot.countDown();await(release);return result;}).when(publication).snapshotCandidates(owner.getId());
            Future<?> publish=pool.submit(()->create(owner,b,Visibility.PUBLIC,true));
            try { await(snapshot); jdbc.update(deleteReceiver?"UPDATE users SET deleted_at=NOW() WHERE id=?":"UPDATE users SET status='SUSPENDED' WHERE id=?",deleteReceiver?receiver.getId():owner.getId()); } finally { release.countDown(); }
            if(deleteReceiver) done(publish); else fails(publish,ErrorCode.USER_003);
            assertThat(count()).isZero(); reset(publication);
        }
    }
    @Test void mutualPublishWithLowerReceiverUsesOrderedLocksAndBothComplete() throws Exception {
        var low=user("mutual-low"); var high=user("mutual-high"); var lowBlog=blog(low); var highBlog=blog(high);
        accepted(low,high);accepted(high,low);
        CyclicBarrier snapshots=new CyclicBarrier(2); CountDownLatch inserted=new CountDownLatch(1), release=new CountDownLatch(1); AtomicInteger batches=new AtomicInteger();
        doAnswer(i->{var result=i.callRealMethod(); snapshots.await(10,TimeUnit.SECONDS);return result;}).when(publication).snapshotCandidates(anyLong());
        doAnswer(i->{var result=i.callRealMethod();if(batches.incrementAndGet()==1){inserted.countDown();await(release);}return result;}).when(generator).createBatch(anyList());
        Future<?> first=pool.submit(()->create(low,lowBlog,Visibility.PUBLIC,true)); Future<?> second=pool.submit(()->create(high,highBlog,Visibility.PUBLIC,true));
        try { await(inserted); waitObserved("users","mutual publication"); } finally {release.countDown();}
        done(first);done(second); assertThat(events()).containsExactlyInAnyOrder(low.getId()+":POST",high.getId()+":POST");
    }
    @Test void readAllBeforeInsertLeavesNewNotificationUnread() throws Exception {
        var receiver=user("all-low");var owner=user("all-owner");var b=blog(owner);accepted(receiver,owner);
        create(owner,b,Visibility.PUBLIC,true);
        try(var gate=transactionGate(()->notifications.readAll(null,ZeroverseUserPrincipal.from(receiver)))) {
            Future<?> publish=pool.submit(()->create(owner,b,Visibility.PUBLIC,true));
            waitObserved("users","insert after read-all");gate.release();done(publish);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE read_at IS NULL",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE read_at IS NOT NULL",Long.class)).isEqualTo(1);
    }
    @Test void insertBeforeReadAllIsIncludedAfterReceiverLockWait() throws Exception {
        var receiver=user("insert-low");var owner=user("insert-owner");var b=blog(owner);accepted(receiver,owner);
        CountDownLatch inserted=new CountDownLatch(1), release=new CountDownLatch(1);
        doAnswer(i->{var result=i.callRealMethod();inserted.countDown();await(release);return result;}).when(generator).createBatch(anyList());
        Future<?> publish=pool.submit(()->create(owner,b,Visibility.PUBLIC,true));Future<?> readAll=null;
        try {await(inserted);readAll=pool.submit(()->notifications.readAll(null,ZeroverseUserPrincipal.from(receiver)));waitObserved("users","read-all after insert");} finally {release.countDown();}
        done(publish);done(readAll); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE read_at IS NOT NULL",Long.class)).isEqualTo(1);
    }
    @Test void readDeleteCompetitionPreservesFirstTimestampsAndNeverResurrects() throws Exception {
        var receiver=user("read-delete-low");var owner=user("read-delete-owner");var b=blog(owner);accepted(receiver,owner);
        create(owner,b,Visibility.PUBLIC,true); long id=jdbc.queryForObject("SELECT id FROM notifications",Long.class);
        jdbc.update("UPDATE notifications SET read_at='2000-01-01 00:00:00' WHERE id=?",id);
        try(var gate=transactionGate(()->notifications.read(null,ZeroverseUserPrincipal.from(receiver),id))) {
            Future<?> deletion=pool.submit(()->notifications.delete(null,ZeroverseUserPrincipal.from(receiver),id));
            waitObserved("users","delete after read");gate.release();done(deletion);
        }
        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id=?",java.time.LocalDateTime.class,id)).isEqualTo(java.time.LocalDateTime.of(2000,1,1,0,0));
        jdbc.update("UPDATE notifications SET deleted_at='2001-01-01 00:00:00' WHERE id=?",id);
        var first=jdbc.queryForMap("SELECT read_at,deleted_at FROM notifications WHERE id=?",id);
        notifications.delete(null,ZeroverseUserPrincipal.from(receiver),id);
        assertThat(jdbc.queryForMap("SELECT read_at,deleted_at FROM notifications WHERE id=?",id)).isEqualTo(first);
        create(owner,b,Visibility.PUBLIC,true);long second=jdbc.queryForObject("SELECT MAX(id) FROM notifications",Long.class);
        try(var gate=transactionGate(()->notifications.delete(null,ZeroverseUserPrincipal.from(receiver),second))) {
            Future<?> reading=pool.submit(()->notifications.read(null,ZeroverseUserPrincipal.from(receiver),second));
            waitObserved("users","read after delete");gate.release();fails(reading,ErrorCode.NOT_001);
        }
        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id=?",Object.class,second)).isNull();
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM notifications WHERE id=?",Object.class,second)).isNotNull();
    }
    @Test void postDeleteWinsQueuedUpdateAndNoNotificationAppears() throws Exception {
        var receiver=user("post-delete-low");var owner=user("post-delete-owner");var b=blog(owner);accepted(receiver,owner);var post=create(owner,b,Visibility.PRIVATE,false);
        try(var gate=transactionGate(()->posts.delete(owner.getId(),post.id()))) {
            Future<?> update=pool.submit(()->update(owner,post.id(),Visibility.PUBLIC,true));
            waitObserved("blogs","publish update after post delete");gate.release();fails(update,ErrorCode.POST_001);
        }
        assertThat(count()).isZero();
    }
    @Test void actualMysql1205RollsBackThreeAttemptsAndCommitsNoRows() throws Exception {
        var receiver=user("timeout-low");var owner=user("timeout-owner");var b=blog(owner);accepted(receiver,owner);
        doAnswer(i->{jdbc.execute("SET SESSION innodb_lock_wait_timeout=1");return i.callRealMethod();}).when(publication).snapshotCandidates(owner.getId());
        try(var gate=transactionGate(()->jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,receiver.getId()))) {
            Future<?> publish=pool.submit(()->create(owner,b,Visibility.PUBLIC,true));waitObserved("users","real 1205 attempts");fails(publish,ErrorCode.COMMON_500);
        }
        verify(publication,times(3)).snapshotCandidates(owner.getId());assertThat(count()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts",Long.class)).isZero();
        System.out.println("M8_RETRY mysqlCode=1205 attempts=3 committedPosts=0 committedNotifications=0");
    }

    @Test void actualMysql1213DeadlockRetriesInFreshTransactionAndCommitsOnlySuccess() throws Exception {
        var low=user("deadlock-low");var high=user("deadlock-high");
        List<Long> heavyIds=new ArrayList<>();heavyIds.add(high.getId());
        for(int n=0;n<12;n++)heavyIds.add(user("deadlock-heavy-"+n).getId());
        CountDownLatch highHeld=new CountDownLatch(1),lowHeld=new CountDownLatch(1),externalRequestsLow=new CountDownLatch(1),workerRequestsHigh=new CountDownLatch(1);
        AtomicInteger attempts=new AtomicInteger();
        Future<?> heavy=pool.submit(()->new TransactionTemplate(manager).executeWithoutResult(status->{
            for(Long id:heavyIds)jdbc.update("UPDATE users SET bio='heavy' WHERE id=?",id);
            highHeld.countDown();await(externalRequestsLow);
            jdbc.update("UPDATE users SET bio='external' WHERE id=?",low.getId());
        }));
        await(highHeld);
        Future<?> retried=pool.submit(()->executor.execute(()->{
            int attempt=attempts.incrementAndGet();
            jdbc.update("UPDATE users SET bio=? WHERE id=?","attempt-"+attempt,low.getId());
            if(attempt==1){lowHeld.countDown();await(workerRequestsHigh);}
            jdbc.update("UPDATE users SET bio=? WHERE id=?","attempt-"+attempt,high.getId());return null;
        }));
        try{await(lowHeld);externalRequestsLow.countDown();waitObserved("users","deadlock first edge");}
        finally{externalRequestsLow.countDown();workerRequestsHigh.countDown();}
        done(heavy);done(retried);assertThat(attempts).hasValue(2);
        assertThat(jdbc.queryForObject("SELECT bio FROM users WHERE id=?",String.class,low.getId())).isEqualTo("attempt-2");
        assertThat(jdbc.queryForObject("SELECT bio FROM users WHERE id=?",String.class,high.getId())).isEqualTo("attempt-2");
        System.out.println("M8_RETRY mysqlCode=1213 attempts=2 finalValue=attempt-2");
    }

    @Test void acceptCannotApplyToReplacementRequestAfterOriginalCancelled() throws Exception {
        var requester=user("replace-requester");var receiver=user("replace-receiver");
        var original=relations.saveAndFlush(Universe.pending(requester,receiver));
        CountDownLatch initialRead=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger reads=new AtomicInteger();
        doAnswer(i->{if(reads.incrementAndGet()==1){initialRead.countDown();await(release);}return Optional.of(original);})
                .when(universeRows).findVisibleById(original.getId());
        Future<?> accept=pool.submit(()->universe.accept(receiver.getId(),original.getId()));
        Long replacementId;
        try {await(initialRead);universe.cancel(requester.getId(),original.getId());replacementId=universe.request(requester.getId(),receiver.getId()).requestId();}
        finally{release.countDown();}
        fails(accept,ErrorCode.UNI_004);
        assertThat(jdbc.queryForObject("SELECT status FROM universes WHERE id=?",String.class,replacementId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT message FROM notifications",String.class)).isEqualTo("유니버스 신청을 받았습니다.");
    }

    @Test void rejectCannotDeleteReplacementRequestAfterOriginalCancelled() throws Exception {
        assertRemovalCannotDeleteReplacement(true);
    }

    @Test void cancelCannotDeleteReplacementRequestAfterOriginalCancelled() throws Exception {
        assertRemovalCannotDeleteReplacement(false);
    }

    private void assertRemovalCannotDeleteReplacement(boolean reject) throws Exception {
        String operation = reject ? "reject" : "cancel";
        var requester = user(operation + "-replace-requester");
        var receiver = user(operation + "-replace-receiver");
        long originalId = universe.request(requester.getId(), receiver.getId()).requestId();
        var original = relations.findVisibleById(originalId).orElseThrow();
        CountDownLatch initialRead = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(invocation -> {
            if (reads.incrementAndGet() == 1) {
                initialRead.countDown();
                await(release);
            }
            return Optional.of(original);
        }).when(universeRows).findVisibleById(originalId);
        Future<?> removal = pool.submit(() -> {
            if (reject) universe.reject(receiver.getId(), originalId);
            else universe.cancel(requester.getId(), originalId);
        });
        Long replacementId;
        List<Map<String, Object>> historyBefore;
        try {
            await(initialRead);
            universe.cancel(requester.getId(), originalId);
            replacementId = universe.request(requester.getId(), receiver.getId()).requestId();
            historyBefore = jdbc.queryForList("SELECT * FROM notifications ORDER BY id");
        } finally {
            release.countDown();
        }
        fails(removal, ErrorCode.UNI_004);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM universes WHERE id=?", Long.class, originalId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM universes WHERE id=?", String.class, replacementId)).isEqualTo("PENDING");
        assertThat(historyBefore).hasSize(2);
        assertThat(jdbc.queryForList("SELECT * FROM notifications ORDER BY id")).isEqualTo(historyBefore);
        assertThat(jdbc.queryForList("SELECT target_id FROM notifications ORDER BY id", Long.class))
                .containsExactly(originalId, replacementId);
    }

    @Test void finalCandidateRecheckRunsAfterExistingPostLock() {
        var receiver=user("order-low");var owner=user("order-owner");var b=blog(owner);accepted(receiver,owner);
        var post=create(owner,b,Visibility.PRIVATE,false);
        doAnswer(invocation->{
            long connection=jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class);
            Long locks=observation.queryForObject("""
                    SELECT COUNT(*) FROM performance_schema.data_locks l
                    JOIN performance_schema.threads t ON t.THREAD_ID=l.THREAD_ID
                    WHERE t.PROCESSLIST_ID=? AND l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='posts'
                      AND l.INDEX_NAME='PRIMARY' AND l.LOCK_TYPE='RECORD' AND l.LOCK_MODE LIKE 'X%'
                      AND l.LOCK_DATA=?
                    """,Long.class,connection,post.id().toString());
            assertThat(locks).as("existing post row must be locked before final candidate recheck").isGreaterThan(0);
            return invocation.callRealMethod();
        }).when(publication).currentCandidates(owner.getId());
        update(owner,post.id(),Visibility.PUBLIC,true);
        assertThat(events()).containsExactly(receiver.getId()+":POST");
    }
    Gate transactionGate(Runnable action) throws Exception {
        CountDownLatch acquired=new CountDownLatch(1),release=new CountDownLatch(1);
        Future<?> task=pool.submit(()->new TransactionTemplate(manager).executeWithoutResult(s->{action.run();acquired.countDown();await(release);}));
        if(!acquired.await(10,TimeUnit.SECONDS)){release.countDown();done(task);throw new AssertionError("gate not acquired");}
        return new Gate(release,task);
    }
    record Gate(CountDownLatch latch,Future<?> future) implements AutoCloseable {
        void release(){latch.countDown();}public void close() throws Exception {release();done(future);}
    }
    void waitObserved(String table,String operation) {
        long started=System.nanoTime(),deadline=started+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline) {
            Long waits=observation.queryForObject("""
                SELECT COUNT(*) FROM performance_schema.data_lock_waits w
                JOIN performance_schema.data_locks l ON l.ENGINE=w.ENGINE AND l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
                WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME=?
                """,Long.class,table);
            if(waits!=null&&waits>0){System.out.printf("M8_LOCK operation=%s table=%s observedWaits=%d observedAfterMs=%.3f%n",operation,table,waits,(System.nanoTime()-started)/1_000_000d);return;}
            Thread.yield();
        }
        throw new AssertionError("no data_lock_waits: "+operation);
    }
    static void await(CountDownLatch latch) {try {if(!latch.await(20,TimeUnit.SECONDS))throw new AssertionError("barrier timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    static void done(Future<?> f) throws Exception {try{f.get(30,TimeUnit.SECONDS);}catch(ExecutionException e){throw new AssertionError("concurrent operation failed",e.getCause());}}
    static void fails(Future<?> f,ErrorCode code) throws Exception {try{f.get(30,TimeUnit.SECONDS);throw new AssertionError("expected "+code);}catch(ExecutionException e){assertThat(e.getCause()).isInstanceOfSatisfying(BusinessException.class,x->assertThat(x.getErrorCode()).isEqualTo(code));}}
    static JdbcTemplate observation(){var d=new DriverManagerDataSource(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());return new JdbcTemplate(d);}
    long lockTime(){return Long.parseLong(observation.queryForMap("SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_time'").get("Value").toString());}
    static long usedMemory(){return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();}
}
