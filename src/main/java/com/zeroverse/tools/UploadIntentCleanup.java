package com.zeroverse.tools;

import com.zeroverse.ZeroverseServerApplication;
import com.zeroverse.domain.upload.service.UploadIntentCleanupService;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.ArrayList;
import java.util.List;

/** Explicit upload-intent maintenance command; dry-run is the default. */
public final class UploadIntentCleanup {

    private UploadIntentCleanup() {}

    public static void main(String[] args) {
        ZeroverseServerApplication.requireUtcJvm();
        boolean apply = hasApplyFlag(args);
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ZeroverseServerApplication.class)
                .web(WebApplicationType.NONE)
                .run(safeArgs(args))) {
            UploadIntentCleanupService.CleanupResult result = context
                    .getBean(UploadIntentCleanupService.class)
                    .cleanup(apply);
            System.out.println("uploadIntentCleanup mode=" + (apply ? "apply" : "dry-run")
                    + " candidates=" + result.candidates()
                    + " transitioned=" + result.transitioned()
                    + " deleted=" + result.deleted()
                    + " retried=" + result.retried()
                    + " skipped=" + result.skipped());
        }
    }

    /** Maintenance must never migrate or create schema, even when a caller supplied conflicting flags. */
    static String[] safeArgs(String[] args) {
        List<String> safe = new ArrayList<>();
        if (args != null) {
            for (String arg : args) {
                if (arg == null
                        || arg.startsWith("--spring.flyway.enabled=")
                        || arg.startsWith("--spring.jpa.hibernate.ddl-auto=")
                        || arg.startsWith("--spring.main.web-application-type=")) {
                    continue;
                }
                safe.add(arg);
            }
        }
        safe.add("--spring.flyway.enabled=false");
        safe.add("--spring.jpa.hibernate.ddl-auto=validate");
        safe.add("--spring.main.web-application-type=none");
        return safe.toArray(String[]::new);
    }

    private static boolean hasApplyFlag(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if ("--upload.cleanup.apply=true".equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
