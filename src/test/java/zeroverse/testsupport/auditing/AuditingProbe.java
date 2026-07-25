package zeroverse.testsupport.auditing;

import com.zeroverse.common.entity.BaseSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Auditing 동작 검증 전용 엔티티.
 *
 * <p><b>격리</b>: base package {@code com.zeroverse} <i>바깥</i>에 두어 컴포넌트 스캔에 잡히지 않는다.
 * 이 엔티티를 쓰는 테스트만 {@code @EntityScan}으로 명시 등록하고, 대응 테이블은 운영 마이그레이션이 아닌
 * {@code classpath:db/test-migration}에서만 생성한다. 따라서 운영 {@code V1__init.sql}과 다른 테스트의
 * {@code ddl-auto=validate}에 영향을 주지 않는다(심의 필수 변경 #6).
 */
@Entity
@Table(name = "test_auditing_probe")
public class AuditingProbe extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    protected AuditingProbe() {}

    public AuditingProbe(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }
}
