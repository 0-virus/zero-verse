package com.zeroverse.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * UserRepository JPA 테스트(FR-SETTINGS-01).
 *
 * <p>soft delete 조회, self-exclusion 중복 검사, unique 제약, auditing을 MySQL 8.4
 * Testcontainers에서 검증한다.
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest extends MySqlTestSupport {

    @Autowired
    private UserRepository userRepository;

    private User createUser(String email, String nickname) {
        return User.register(email, "$2a$12$hashedPassword", "테스터", nickname, LocalDate.of(1990, 1, 1));
    }

    @Nested
    @DisplayName("existsByNicknameAndDeletedAtIsNull")
    class ExistsByNicknameAndDeletedAtIsNullTests {

        @Test
        @DisplayName("존재하는 사용자의 nickname으로 true를 반환한다")
        void returnsTrueForExistingNickname() {
            User user = createUser("exists@test.com", "existnick");
            userRepository.save(user);

            boolean exists = userRepository.existsByNicknameAndDeletedAtIsNull("existnick");

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("없는 nickname으로 false를 반환한다")
        void returnsFalseForNonExistingNickname() {
            boolean exists = userRepository.existsByNicknameAndDeletedAtIsNull("nonexistnick");

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("soft delete된 사용자의 nickname으로 false를 반환한다")
        void returnsFalseForDeletedUser() {
            User user = createUser("deleted@test.com", "deletednick");
            userRepository.save(user);
            userRepository.flush();

            user.softDelete();
            userRepository.save(user);
            userRepository.flush();

            boolean exists = userRepository.existsByNicknameAndDeletedAtIsNull("deletednick");

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("같은 nickname이 1개일 때 true를 반환한다")
        void returnsTrueForSingleNickname() {
            User user = createUser("single@test.com", "uniquenick");
            userRepository.save(user);

            boolean exists = userRepository.existsByNicknameAndDeletedAtIsNull("uniquenick");

            assertThat(exists).isTrue();
        }
    }

    @Nested
    @DisplayName("existsByNicknameAndIdNotAndDeletedAtIsNull — self-exclusion")
    class ExistsByNicknameAndIdNotAndDeletedAtIsNullTests {

        @Test
        @DisplayName("자신의 nickname으로 조회하면 false를 반환한다 (자신 제외)")
        void returnsFalseForOwnNickname() {
            User user = createUser("self@test.com", "selnick");
            userRepository.save(user);
            Long userId = user.getId();

            boolean exists = userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull("selnick", userId);

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("다른 사용자의 nickname으로 조회하면 true를 반환한다")
        void returnsTrueForOthersNickname() {
            User user1 = createUser("other@test.com", "othernick");
            userRepository.save(user1);

            User user2 = createUser("self@test.com", "selnick");
            userRepository.save(user2);
            Long user2Id = user2.getId();

            boolean exists = userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull("othernick", user2Id);

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("없는 nickname으로 조회하면 false를 반환한다")
        void returnsFalseForNonExistingNickname() {
            User user = createUser("user@test.com", "usernick");
            userRepository.save(user);
            Long userId = user.getId();

            boolean exists = userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull("nonexist", userId);

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("soft delete된 다른 사용자의 nickname으로 조회하면 false를 반환한다")
        void returnsFalseForDeletedUser() {
            User deleted = createUser("deleted@test.com", "deletednick");
            userRepository.save(deleted);
            deleted.softDelete();
            userRepository.save(deleted);
            userRepository.flush();

            User current = createUser("current@test.com", "currentnick");
            userRepository.save(current);
            Long currentId = current.getId();

            boolean exists = userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull("deletednick", currentId);

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("자신이 아닌 다른 사용자가 이미 nickname을 사용 중이면 true를 반환한다")
        void returnsTrueWhenOtherUserOwnsThenickname() {
            User userA = createUser("a@test.com", "shared");
            userRepository.save(userA);

            User userB = createUser("b@test.com", "unique");
            userRepository.save(userB);
            Long userBId = userB.getId();

            boolean exists = userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull("shared", userBId);

            assertThat(exists).isTrue();
        }
    }

    @Nested
    @DisplayName("soft delete 제외와 auditing")
    class SoftDeleteAndAuditingTests {

        @Test
        @DisplayName("저장할 때 createdAt과 updatedAt이 자동으로 채워진다")
        void auditingFieldsArePopulatedOnPersist() {
            User user = createUser("audit@test.com", "auditnick");

            userRepository.save(user);

            assertThat(user.getCreatedAt()).isNotNull();
            assertThat(user.getUpdatedAt()).isNotNull();
            assertThat(user.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("삭제되지 않은 사용자를 조회하면 찾을 수 있다")
        void findsActiveUserByNickname() {
            User user = createUser("active@test.com", "activenick");
            userRepository.save(user);
            Long userId = user.getId();

            User found = userRepository.findByIdAndDeletedAtIsNull(userId).orElse(null);

            assertThat(found).isNotNull().extracting("nickname").isEqualTo("activenick");
        }

        @Test
        @DisplayName("soft delete된 사용자는 조회되지 않는다")
        void doesNotFindDeletedUserById() {
            User user = createUser("todelete@test.com", "tobedeletednick");
            userRepository.save(user);
            Long userId = user.getId();
            userRepository.flush();

            user.softDelete();
            userRepository.save(user);
            userRepository.flush();

            var found = userRepository.findByIdAndDeletedAtIsNull(userId);

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("Repository 쿼리 정확성")
    class RepositoryQueryAccuracyTests {

        @Test
        @DisplayName("soft delete된 사용자는 existsByNicknameAndDeletedAtIsNull에 포함되지 않는다")
        void deletedUserNotIncludedInDeleteAtIsNullQueries() {
            User user = createUser("user@test.com", "testnick");
            userRepository.save(user);
            userRepository.flush();

            boolean existsActive = userRepository.existsByNicknameAndDeletedAtIsNull("testnick");
            assertThat(existsActive).isTrue();

            user.softDelete();
            userRepository.save(user);
            userRepository.flush();

            boolean existsDeleted = userRepository.existsByNicknameAndDeletedAtIsNull("testnick");
            assertThat(existsDeleted).isFalse();
        }

        @Test
        @DisplayName("findByIdAndDeletedAtIsNull은 soft delete된 사용자를 반환하지 않는다")
        void findByIdDoesNotReturnDeletedUser() {
            User user = createUser("user@test.com", "nick");
            userRepository.save(user);
            Long userId = user.getId();
            userRepository.flush();

            var foundActive = userRepository.findByIdAndDeletedAtIsNull(userId);
            assertThat(foundActive).isNotEmpty();

            user.softDelete();
            userRepository.save(user);
            userRepository.flush();

            var foundDeleted = userRepository.findByIdAndDeletedAtIsNull(userId);
            assertThat(foundDeleted).isEmpty();
        }

        @Test
        @DisplayName("자신 제외 중복 검사: 자신의 기존 nickname으로 조회하면 false")
        void selfExclusionWorksProperly() {
            User user = createUser("user@test.com", "myname");
            userRepository.save(user);
            Long userId = user.getId();
            userRepository.flush();

            // 자신의 nickname으로 "다른 사용자의 중복"을 검사하면 false (자신 제외)
            boolean isDuplicate = userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull("myname", userId);
            assertThat(isDuplicate).isFalse();
        }
    }

    @Nested
    @DisplayName("쿼리 조건 상호작용 검증 — soft delete + self-exclusion")
    class QueryConditionInteractionTests {

        @Test
        @DisplayName("soft delete된 사용자는 쿼리 결과에서 제외되고, 활성 사용자는 포함된다")
        void deletedUserExcludedAndActiveUserIncluded() {
            User active = createUser("active@test.com", "activenick");
            userRepository.save(active);
            Long activeId = active.getId();
            userRepository.flush();

            User deleted = createUser("deleted@test.com", "delnick");
            userRepository.save(deleted);
            deleted.softDelete();
            userRepository.save(deleted);
            userRepository.flush();

            // soft delete된 사용자의 nickname으로는 결과가 없어야 함
            boolean existsDeleted = userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull("delnick", activeId);
            assertThat(existsDeleted).isFalse();

            // 다른 활성 사용자 찾기
            User other = createUser("other@test.com", "othernick");
            userRepository.save(other);
            userRepository.flush();

            // 활성 사용자의 nickname으로는 결과가 있어야 함
            boolean existsActive = userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull("othernick", activeId);
            assertThat(existsActive).isTrue();
        }
    }
}
