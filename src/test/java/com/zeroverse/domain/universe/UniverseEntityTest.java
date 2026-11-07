package com.zeroverse.domain.universe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.universe.entity.Universe;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import com.zeroverse.domain.user.entity.User;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UniverseEntityTest {

    @Test
    @DisplayName("PENDING 관계는 ACCEPTED로 한 번만 전이한다")
    void pendingAcceptsOnce() {
        User from = User.register("from@test.invalid", "hash", "From", "from-user", LocalDate.of(1990, 1, 1));
        User to = User.register("to@test.invalid", "hash", "To", "to-user", LocalDate.of(1990, 1, 1));

        Universe relation = Universe.pending(from, to);
        relation.accept();

        assertThat(relation.getStatus()).isEqualTo(UniverseStatus.ACCEPTED);
        assertThatThrownBy(relation::accept)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNI_005);
    }

    @Test
    @DisplayName("차단 관계 factory는 BLOCKED 상태를 만든다")
    void blockedFactory() {
        User from = User.register("from2@test.invalid", "hash", "From", "from-user-2", LocalDate.of(1990, 1, 1));
        User to = User.register("to2@test.invalid", "hash", "To", "to-user-2", LocalDate.of(1990, 1, 1));

        assertThat(Universe.blocked(from, to).getStatus()).isEqualTo(UniverseStatus.BLOCKED);
    }
}
