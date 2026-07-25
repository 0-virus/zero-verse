package com.zeroverse.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.support.MySqlTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import zeroverse.testsupport.auditing.AuditingProbe;

/**
 * {@link BaseEntity} / {@link BaseSoftDeleteEntity}의 Auditing 동작 검증(REQUIREMENTS NFR-06).
 *
 * <p>운영 스키마를 오염시키지 않기 위해 base package 바깥의 테스트 전용 엔티티와 {@code db/test-migration}의
 * 전용 테이블만 사용한다(심의 필수 변경 #6).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = "spring.flyway.locations=classpath:db/migration,classpath:db/test-migration")
class BaseEntityAuditingTest extends MySqlTestSupport {

    @Autowired private EntityManager entityManager;

    /**
     * 테스트 전용 프로브 엔티티만 등록한다. {@code @EnableJpaAuditing}은
     * {@code ZeroverseServerApplication}에 이미 있으므로 여기서 다시 선언하지 않는다(빈 중복 정의 방지).
     */
    @TestConfiguration
    @EntityScan(basePackageClasses = AuditingProbe.class)
    static class ProbeEntityConfig {}

    @Test
    @DisplayName("저장 시 createdAt·updatedAt이 자동으로 채워진다")
    @Transactional
    void auditingFieldsArePopulatedOnPersist() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(5);

        AuditingProbe probe = new AuditingProbe("생성 확인");
        entityManager.persist(probe);
        entityManager.flush();

        assertThat(probe.getCreatedAt()).isNotNull().isAfter(before);
        assertThat(probe.getUpdatedAt()).isNotNull().isAfter(before);
        assertThat(probe.getDeletedAt()).isNull();
        assertThat(probe.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("수정 시 updatedAt만 갱신되고 createdAt은 유지된다")
    @Transactional
    void updatedAtChangesOnUpdateWhileCreatedAtStays() throws InterruptedException {
        AuditingProbe probe = new AuditingProbe("수정 전");
        entityManager.persist(probe);
        entityManager.flush();

        LocalDateTime createdAt = probe.getCreatedAt();
        LocalDateTime firstUpdatedAt = probe.getUpdatedAt();

        Thread.sleep(20);
        probe.rename("수정 후");
        entityManager.flush();

        assertThat(probe.getCreatedAt()).isEqualTo(createdAt);
        assertThat(probe.getUpdatedAt()).isAfter(firstUpdatedAt);
        assertThat(probe.getName()).isEqualTo("수정 후");
    }

    @Test
    @DisplayName("soft delete는 deletedAt을 채우고 restore는 되돌린다")
    @Transactional
    void softDeleteSetsAndClearsDeletedAt() {
        AuditingProbe probe = new AuditingProbe("삭제 확인");
        entityManager.persist(probe);
        entityManager.flush();

        probe.softDelete();
        entityManager.flush();

        assertThat(probe.getDeletedAt()).isNotNull();
        assertThat(probe.isDeleted()).isTrue();

        probe.restore();
        entityManager.flush();

        assertThat(probe.getDeletedAt()).isNull();
        assertThat(probe.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("테스트 전용 테이블은 운영 마이그레이션에 포함되지 않는다")
    void testTableIsNotPartOfProductionMigration() {
        Long count = entityManager
                .createQuery(
                        "SELECT COUNT(p) FROM AuditingProbe p WHERE p.name = :name", Long.class)
                .setParameter("name", "존재하지 않는 이름")
                .getSingleResult();

        assertThat(count).isZero();
    }
}
