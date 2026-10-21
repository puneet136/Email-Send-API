package com.email.bulk.service;

import com.email.bulk.api.dto.RecipientImportResponse;
import com.email.bulk.exception.InvalidImportFileException;
import com.email.bulk.repository.RecipientImportRepository;
import com.email.bulk.storage.ImportFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RecipientImportService {

    private static final Logger log =
            LoggerFactory.getLogger(RecipientImportService.class);

    private final CampaignAccessService campaignAccessService;
    private final RecipientImportRepository importRepository;
    private final ImportFileStorage fileStorage;
    private final TransactionTemplate transactionTemplate;
    private final CampaignStateService campaignStateService;

    public RecipientImportService(
            CampaignAccessService campaignAccessService,
            RecipientImportRepository importRepository,
            ImportFileStorage fileStorage,
            PlatformTransactionManager transactionManager,
            CampaignStateService campaignStateService) {

        this.campaignAccessService = campaignAccessService;
        this.importRepository = importRepository;
        this.fileStorage = fileStorage;
        this.campaignStateService = campaignStateService;

        this.transactionTemplate =
                new TransactionTemplate(transactionManager);

        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    public RecipientImportResponse uploadCsv(
            long campaignId,
            MultipartFile file) throws IOException {

        String originalFilename = validateFile(file);

        campaignAccessService.requireOwner(campaignId);

        String storedFilename = fileStorage.storeCsv(file);

        AtomicBoolean cleanupRegistered = new AtomicBoolean(false);

        try {
            return transactionTemplate.execute(transactionStatus -> {

                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCompletion(int status) {

                                if (status == STATUS_ROLLED_BACK) {
                                    cleanupFile(storedFilename);

                                } else if (status == STATUS_UNKNOWN) {
                                    log.error(
                                            "Transaction outcome unknown; "
                                                    + "retained file {}",
                                            storedFilename
                                    );
                                }
                            }
                        }
                );

                cleanupRegistered.set(true);

                // Authoritative check inside the registration transaction.
                campaignStateService.lockAndRequireDraft(campaignId);

                long importId = importRepository.insert(
                        campaignId,
                        originalFilename,
                        storedFilename,
                        file.getSize()
                );

                return new RecipientImportResponse(
                        importId,
                        campaignId,
                        "QUEUED"
                );
            });

        } catch (RuntimeException exception) {

            if (!cleanupRegistered.get()) {
                cleanupFile(storedFilename);
            }

            throw exception;
        }
    }

    private String validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidImportFileException(
                    "Please upload a non-empty CSV file"
            );
        }

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidImportFileException(
                    "Filename is required"
            );
        }

        // Keep only the filename, removing any supplied directory.
        String filename = originalFilename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1)
                .strip();

        if (filename.isBlank()
                || filename.length() > 255
                || filename.chars().anyMatch(Character::isISOControl)) {

            throw new InvalidImportFileException(
                    "Filename is invalid or exceeds 255 characters"
            );
        }

        if (!filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new InvalidImportFileException(
                    "Only CSV files are supported currently"
            );
        }

        return filename;
    }

    private void cleanupFile(String storedFilename) {

        try {
            fileStorage.deleteStoredFile(storedFilename);

        } catch (IOException | RuntimeException exception) {
            log.error(
                    "Could not clean up stored import file {}",
                    storedFilename,
                    exception
            );
        }
    }
}