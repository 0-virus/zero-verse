package com.zeroverse.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zeroverse.domain.admin.service.AdminService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Real signup auditing and admin UTC interpretation must agree in the supported UTC runtime. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSignupAuditMySqlTest extends MySqlTestSupport {
    @Autowired MockMvc mvc;
    @Autowired AdminService admins;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @Test
    @DisplayName("지원 UTC JVM의 실제 가입 audit는 UTC now와 일치하고 즉시 이번주 가입에 포함된다")
    void realSignupIsImmediatelyIncludedAndAuditInstantAgreesWithUtcNow() throws Exception {
        assertThat(ZoneId.systemDefault().normalized()).as("Gradle test uses supported UTC runtime")
                .isEqualTo(ZoneOffset.UTC);
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        register("audit-admin");
        User actor = users.findByEmailAndDeletedAtIsNull("audit-admin@zeroverse.test").orElseThrow();
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", actor.getId());
        long joinedBefore = admins.list(actor.getId(), null, null, null, 0, 20)
                .summary().joinedThisWeek();

        Instant before = Instant.now().minusSeconds(1); // DATETIME stores whole seconds.
        register("audit-signup");
        Instant after = Instant.now().plusSeconds(1);
        User target = users.findByEmailAndDeletedAtIsNull("audit-signup@zeroverse.test").orElseThrow();
        // Read DATETIME as LocalDateTime, avoiding a Timestamp/default-zone round trip.
        LocalDateTime stored = jdbc.queryForObject("SELECT created_at FROM users WHERE id=?",
                (row, index) -> row.getObject(1, LocalDateTime.class), target.getId());
        LocalDateTime updated = jdbc.queryForObject("SELECT updated_at FROM users WHERE id=?",
                (row, index) -> row.getObject(1, LocalDateTime.class), target.getId());
        var detail = admins.get(actor.getId(), target.getId());
        var listed = admins.list(actor.getId(), null, null, null, 0, 20);

        System.out.printf("M9_SIGNUP_AUDIT jvm=%s clock=%s before=%s after=%s dbCreatedAt=%s "
                        + "adminCreatedAt=%s joinedBefore=%d joinedAfter=%d%n",
                ZoneId.systemDefault(), clock.getZone(), before, after, stored, detail.createdAt(),
                joinedBefore, listed.summary().joinedThisWeek());
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(stored.toInstant(ZoneOffset.UTC)).as("real DB audit interpreted as UTC")
                .isBetween(before, after);
        softly.assertThat(detail.createdAt().toInstant()).as("admin createdAt actual signup instant")
                .isBetween(before, after);
        softly.assertThat(updated.toInstant(ZoneOffset.UTC)).as("real DB updatedAt actual signup instant")
                .isBetween(before, after);
        softly.assertThat(detail.updatedAt().toInstant()).as("admin updatedAt actual signup instant")
                .isBetween(before, after);
        softly.assertThat(listed.summary().joinedThisWeek()).as("new signup immediately joins this week")
                .isEqualTo(joinedBefore + 1);
        softly.assertThat(listed.users().items()).as("new signup is in current list")
                .anyMatch(item -> item.id().equals(target.getId()));
        softly.assertAll();
    }

    private void register(String key) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@zeroverse.test","password":"Password123!",
                                 "name":"Audit fixture","nickname":"%s","birthDate":"1990-01-01"}
                                """.formatted(key, key)))
                .andExpect(status().isCreated());
    }
}
