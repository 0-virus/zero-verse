package com.zeroverse.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.auth.repository.RefreshTokenRepository;
import com.zeroverse.domain.auth.service.RefreshTokenService;
import com.zeroverse.domain.auth.support.RefreshTokenHasher;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.jwt.JwtProperties;
import com.zeroverse.security.jwt.JwtProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

/**
 * Refresh 파싱 실패의 <b>관측 가능성</b>(심의 필수 변경 #13).
 *
 * <p>응답은 실패 사유와 무관하게 모두 {@code AUTH_003}이다 — 공격자에게 사유를 알려주지 않기
 * 위해서다. 그래서 <b>로그가 유일한 구분 수단</b>이고, 로그가 없거나 등급이 뒤섞이면 운영에서
 * 서명 위조(공격 신호)와 만료(정상 흐름)를 구분할 수 없다. 실제 appender로 확인한다.
 *
 * <p>DB에 닿지 않는다 — 파싱 실패는 저장소 조회 이전에 끝난다.
 */
class RefreshTokenObservabilityTest {

    /** 테스트 전용 키. `application-test.yml`과 같은 값이다. */
    private static final String SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLXplcm92ZXJzZS0yNTZiaXQh";

    /** 이 길이 이상의 토큰 조각이 로그에 남으면 누출로 본다. */
    private static final int FRAGMENT_LENGTH = 8;

    /** 서명 위조를 만들기 위한 <b>다른</b> 키. */
    private static final String FOREIGN_SECRET =
            "Zm9yZWlnbi1rZXktdGhhdC1zaG91bGQtbmV2ZXItdmVyaWZ5LTI1NmJpdA==";

    private static final User USER =
            User.register("obs@zeroverse.test", "hashed", "테스터", "observer", LocalDate.of(1995, 1, 1));

    private Logger serviceLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    private RefreshTokenService service;
    private JwtProvider provider;

    @BeforeEach
    void setUp() {
        provider = providerWith(SECRET, Duration.ofDays(14));
        service = new RefreshTokenService(
                Mockito.mock(RefreshTokenRepository.class),
                Mockito.mock(UserRepository.class),
                provider,
                new RefreshTokenHasher());

        serviceLogger = (Logger) LoggerFactory.getLogger(RefreshTokenService.class);
        originalLevel = serviceLogger.getLevel();
        // 만료는 debug로 남는다. INFO 기본값이면 잡히지 않는다.
        serviceLogger.setLevel(Level.DEBUG);

        appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(appender);
        appender.stop();
        serviceLogger.setLevel(originalLevel);
    }

    @Test
    @DisplayName("만료된 Refresh는 debug로 남는다 — 정상 흐름이라 경보가 되면 안 된다")
    void expiredTokenLogsAtDebug() {
        // TTL이 음수면 발급 즉시 만료된 토큰이 된다.
        String expired = providerWith(SECRET, Duration.ofSeconds(-60))
                .issueRefreshToken(USER, Instant.now())
                .token();

        assertRejected(expired);

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(event.getFormattedMessage()).contains("만료");
    }

    @Test
    @DisplayName("서명이 다른 Refresh는 warn으로 남는다 — 위조 신호다")
    void tamperedSignatureLogsAtWarn() {
        String foreign = providerWith(FOREIGN_SECRET, Duration.ofDays(14))
                .issueRefreshToken(USER, Instant.now())
                .token();

        assertRejected(foreign);

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("서명");
    }

    @Test
    @DisplayName("형식이 깨진 Refresh는 warn + 예외 클래스명만 남는다")
    void malformedTokenLogsKindOnly() {
        assertRejected("not-a-jwt");

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("kind=MalformedJwtException");
    }

    @Test
    @DisplayName("Access Token으로 refresh하면 type 불일치로 warn이 남는다")
    void accessTokenAsRefreshLogsKind() {
        String access = provider.issueAccessToken(USER, Instant.now()).token();

        assertRejected(access);

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("kind=MalformedJwtException");
    }

    @Test
    @DisplayName("쿠키가 없으면 debug로 남는다")
    void missingCookieLogsAtDebug() {
        assertRejected(null);
        assertThat(onlyEvent().getLevel()).isEqualTo(Level.DEBUG);

        appender.list.clear();
        assertRejected("   ");
        assertThat(onlyEvent().getLevel()).isEqualTo(Level.DEBUG);
    }

    /**
     * 어떤 실패 경로에서도 토큰 원문·그 조각·시크릿이 로그에 새지 않아야 한다. JJWT의 예외
     * 메시지는 토큰 조각을 포함할 수 있어서 예외를 그대로 로깅하면 여기서 걸린다.
     */
    @Test
    @DisplayName("실패 로그에 토큰 원문·조각·시크릿이 남지 않는다")
    void logsNeverLeakTokenMaterial() {
        String valid = provider.issueRefreshToken(USER, Instant.now()).token();
        String tampered = valid.substring(0, valid.lastIndexOf('.')) + ".ZmFrZS1zaWduYXR1cmU";

        List<String> candidates = List.of(
                providerWith(SECRET, Duration.ofSeconds(-60)).issueRefreshToken(USER, Instant.now()).token(),
                providerWith(FOREIGN_SECRET, Duration.ofDays(14)).issueRefreshToken(USER, Instant.now()).token(),
                tampered,
                "not-a-jwt");

        for (String token : candidates) {
            appender.list.clear();
            assertRejected(token);

            String logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + "\n" + b);

            assertThat(logged).as("토큰 원문이 로그에 없어야 한다").doesNotContain(token);
            assertThat(logged).as("시크릿이 로그에 없어야 한다").doesNotContain(SECRET);
            assertThat(fragmentsOf(token))
                    .as("토큰의 8자 이상 조각이 로그에 없어야 한다")
                    .noneMatch(logged::contains);
            // 예외 스택을 통째로 실어 나르지 않는다.
            assertThat(appender.list).allSatisfy(e ->
                    assertThat(e.getThrowableProxy()).as("예외 객체를 로그에 싣지 않는다").isNull());
        }
    }

    // --- helpers ---

    /**
     * 토큰의 각 segment에서 <b>8자 슬라이딩 윈도우</b>를 모두 뽑는다.
     *
     * <p>segment 전체만 비교하면 {@code substring(0, 16)} 같은 <b>앞자리 일부 로깅</b>을 잡지
     * 못한다. 어느 위치의 조각이든 8자 이상이 새면 걸리도록 전 구간을 훑는다.
     */
    private static List<String> fragmentsOf(String token) {
        List<String> fragments = new ArrayList<>();
        for (String segment : token.split("\\.")) {
            for (int start = 0; start + FRAGMENT_LENGTH <= segment.length(); start++) {
                fragments.add(segment.substring(start, start + FRAGMENT_LENGTH));
            }
        }
        return fragments;
    }

    private void assertRejected(String rawToken) {
        assertThatThrownBy(() -> service.rotate(rawToken, Instant.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .as("실패 사유와 무관하게 응답은 AUTH_003으로 통일된다")
                .isEqualTo(ErrorCode.AUTH_003);
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).as("실패는 정확히 한 줄로 남는다").hasSize(1);
        return appender.list.get(0);
    }

    private static JwtProvider providerWith(String secret, Duration refreshTtl) {
        return new JwtProvider(new JwtProperties(secret, Duration.ofHours(1), refreshTtl));
    }
}
