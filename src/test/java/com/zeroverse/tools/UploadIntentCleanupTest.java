package com.zeroverse.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UploadIntentCleanupTest {

    @Test
    void maintenanceArgumentsForceNoFlywayNoSchemaCreationAndNoHttpListener() {
        String[] args = UploadIntentCleanup.safeArgs(new String[] {
            "--spring.flyway.enabled=true",
            "--spring.jpa.hibernate.ddl-auto=create",
            "--spring.main.web-application-type=servlet",
            "--upload.cleanup.apply=true"
        });

        assertThat(args).contains(
                "--spring.flyway.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.main.web-application-type=none",
                "--upload.cleanup.apply=true");
        assertThat(args).doesNotContain(
                "--spring.flyway.enabled=true",
                "--spring.jpa.hibernate.ddl-auto=create",
                "--spring.main.web-application-type=servlet");
    }
}
