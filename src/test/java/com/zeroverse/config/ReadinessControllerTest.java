package com.zeroverse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class ReadinessControllerTest {

    private JdbcTemplate jdbcTemplate;
    private ApplicationAvailability applicationAvailability;
    private ReadinessController controller;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        applicationAvailability = mock(ApplicationAvailability.class);
        controller = new ReadinessController(jdbcTemplate, applicationAvailability);
    }

    @Test
    void remainsDownBeforeApplicationAcceptsTraffic() {
        when(applicationAvailability.getReadinessState())
                .thenReturn(ReadinessState.REFUSING_TRAFFIC);

        ResponseEntity<?> response = controller.readiness();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("status", "DOWN"));
        verify(jdbcTemplate, never()).queryForObject("select 1", Integer.class);
    }

    @Test
    void checksDatabaseAfterApplicationAcceptsTraffic() {
        when(applicationAvailability.getReadinessState())
                .thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);

        ResponseEntity<?> response = controller.readiness();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("status", "UP"));
        verify(jdbcTemplate).queryForObject("select 1", Integer.class);
    }
}
