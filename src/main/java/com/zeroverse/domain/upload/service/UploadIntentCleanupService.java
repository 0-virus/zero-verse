package com.zeroverse.domain.upload.service;

import com.zeroverse.domain.upload.UploadIntentStatus;
import com.zeroverse.domain.upload.entity.UploadIntent;
import com.zeroverse.domain.upload.repository.ImageUploadRepository;
import com.zeroverse.domain.upload.repository.UploadIntentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded, explicit cleanup of expired uncompleted S3 intents. */
@Service
@ConditionalOnProperty(prefix = "zeroverse.upload", name = "backend", havingValue = "s3")
public class UploadIntentCleanupService {

    public static final int MAX_BATCH_SIZE = 100;

    private final UploadIntentRepository uploadIntentRepository;
    private final ImageUploadRepository imageUploadRepository;
    private final S3ObjectStore objectStore;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public UploadIntentCleanupService(
            UploadIntentRepository uploadIntentRepository,
            ImageUploadRepository imageUploadRepository,
            S3ObjectStore objectStore,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.uploadIntentRepository = uploadIntentRepository;
        this.imageUploadRepository = imageUploadRepository;
        this.objectStore = objectStore;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public CleanupResult cleanup(boolean apply) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<UploadIntent> candidates = uploadIntentRepository
                .findByStatusInAndExpiresAtLessThanEqualAndUploadExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                        List.of(UploadIntentStatus.PENDING, UploadIntentStatus.DELETING),
                        now,
                        now,
                        PageRequest.of(0, MAX_BATCH_SIZE));
        if (!apply) {
            return new CleanupResult(candidates.size(), 0, 0, 0, 0);
        }

        int transitioned = 0;
        int deleted = 0;
        int retried = 0;
        int skipped = 0;
        for (UploadIntent candidate : candidates) {
            ProcessTarget target = transactionTemplate.execute(status -> transition(candidate.getId(), now));
            if (target == null) {
                skipped++;
                continue;
            }
            transitioned++;
            try {
                objectStore.delete(target.region(), target.bucket(), target.key());
                Boolean marked = transactionTemplate.execute(status -> markDeleted(target.id(), now));
                if (Boolean.TRUE.equals(marked)) {
                    deleted++;
                } else {
                    retried++;
                }
            } catch (RuntimeException e) {
                Boolean recorded = transactionTemplate.execute(status -> recordFailure(target.id(), now));
                if (Boolean.TRUE.equals(recorded)) {
                    retried++;
                } else {
                    skipped++;
                }
            }
        }
        return new CleanupResult(candidates.size(), transitioned, deleted, retried, skipped);
    }

    private ProcessTarget transition(UUID id, LocalDateTime now) {
        UploadIntent intent = uploadIntentRepository.findByIdForUpdate(id).orElse(null);
        if (intent == null
                || (intent.getStatus() != UploadIntentStatus.PENDING
                        && intent.getStatus() != UploadIntentStatus.DELETING)
                || now.isBefore(intent.getExpiresAt())
                || now.isBefore(intent.getUploadExpiresAt())
                || imageUploadRepository.existsById(id)) {
            return null;
        }
        intent.markDeleting(now);
        uploadIntentRepository.saveAndFlush(intent);
        return new ProcessTarget(id, intent.getObjectRegion(), intent.getObjectBucket(), intent.getObjectKey());
    }

    private Boolean markDeleted(UUID id, LocalDateTime now) {
        UploadIntent intent = uploadIntentRepository.findByIdForUpdate(id).orElse(null);
        if (intent == null || intent.getStatus() != UploadIntentStatus.DELETING) {
            return false;
        }
        intent.markDeleted(now);
        uploadIntentRepository.saveAndFlush(intent);
        return true;
    }

    private Boolean recordFailure(UUID id, LocalDateTime now) {
        UploadIntent intent = uploadIntentRepository.findByIdForUpdate(id).orElse(null);
        if (intent == null || intent.getStatus() != UploadIntentStatus.DELETING) {
            return false;
        }
        intent.recordCleanupFailure(now);
        uploadIntentRepository.saveAndFlush(intent);
        return true;
    }

    public record CleanupResult(int candidates, int transitioned, int deleted, int retried, int skipped) {}

    private record ProcessTarget(UUID id, String region, String bucket, String key) {}
}
