package com.zeroverse.common.entity;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.data.auditing.DateTimeProvider;

/** Align audit writes with existing DATETIME(0) columns without MySQL rounding into the future. */
public final class SecondPrecisionAuditTimeProvider implements DateTimeProvider {
    private final Supplier<LocalDateTime> localNow;

    public SecondPrecisionAuditTimeProvider() {
        this(LocalDateTime::now);
    }

    SecondPrecisionAuditTimeProvider(Supplier<LocalDateTime> localNow) {
        this.localNow = localNow;
    }

    @Override
    public Optional<TemporalAccessor> getNow() {
        return Optional.of(localNow.get().truncatedTo(ChronoUnit.SECONDS));
    }
}
