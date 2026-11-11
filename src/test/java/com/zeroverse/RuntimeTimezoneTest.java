package com.zeroverse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

/** Runtime precondition must reject unsupported local timestamps before Spring/DB startup. */
class RuntimeTimezoneTest {
    @ParameterizedTest
    @ValueSource(strings = {"Asia/Seoul", "America/New_York"})
    void nonUtcMainFailsBeforeSpringStartupAndDoesNotChangeTimezone(String zone) {
        TimeZone original = TimeZone.getDefault();
        TimeZone unsupported = TimeZone.getTimeZone(zone);
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            TimeZone.setDefault(unsupported);
            assertThatThrownBy(() -> ZeroverseServerApplication.main(new String[0]))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("-Duser.timezone=UTC");
            spring.verifyNoInteractions();
            assertThat(TimeZone.getDefault()).isEqualTo(unsupported);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void utcMainStartsSpringWithoutChangingTimezone() {
        TimeZone original = TimeZone.getDefault();
        String[] args = {"--spring.main.banner-mode=off"};
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            ZeroverseServerApplication.main(args);
            spring.verify(() -> SpringApplication.run(ZeroverseServerApplication.class, args));
            assertThat(TimeZone.getDefault().toZoneId().normalized()).isEqualTo(ZoneOffset.UTC);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
