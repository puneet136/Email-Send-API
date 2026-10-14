package com.email.bulk.service;

import com.email.bulk.importing.CsvRecipientRow;
import com.email.bulk.importing.EmailValidationResult;
import com.email.bulk.repository.EmailJobRepository;
import com.email.bulk.repository.RecipientImportRepository;
import com.email.bulk.validation.EmailAddressPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.email.bulk.importing.ImportRowError;
import com.email.bulk.repository.RecipientImportErrorRepository;
import java.util.*;

@Service
public class RecipientImportBatchService {

    private final EmailJobRepository emailJobRepository;
    private final RecipientImportRepository importRepository;
    private final EmailAddressPolicy emailAddressPolicy;
    private final RecipientImportErrorRepository errorRepository;

    public RecipientImportBatchService(
            EmailJobRepository emailJobRepository,
            RecipientImportRepository importRepository,
            EmailAddressPolicy emailAddressPolicy,
            RecipientImportErrorRepository errorRepository) {

        this.emailJobRepository = emailJobRepository;
        this.importRepository = importRepository;
        this.emailAddressPolicy = emailAddressPolicy;
        this.errorRepository = errorRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void processBatch(
            long importId,
            long campaignId,
            long expectedProcessedRows,
            List<CsvRecipientRow> rows) {

        if (rows == null || rows.isEmpty() || rows.size() > 500) {
            throw new IllegalArgumentException(
                    "Batch must contain between 1 and 500 records"
            );
        }

        if (expectedProcessedRows < 0) {
            throw new IllegalArgumentException(
                    "Processed-row count must not be negative"
            );
        }

        List<String> validEmails = new ArrayList<>(rows.size());
        List<ImportRowError> errors = new ArrayList<>();

        for (int index = 0; index < rows.size(); index++) {

            CsvRecipientRow row = rows.get(index);

            long expectedRecordNumber =
                    expectedProcessedRows + index + 2;

            if (row.recordNumber() != expectedRecordNumber) {
                throw new IllegalStateException(
                        "CSV batch does not match import progress"
                );
            }

            if (row.error() != null) {
                errors.add(new ImportRowError(
                        row.recordNumber(),
                        row.email(),
                        row.error()
                ));
                continue;
            }

            EmailValidationResult result =
                    emailAddressPolicy.validateAndNormalize(row.email());

            if (!result.isValid()) {
                errors.add(new ImportRowError(
                        row.recordNumber(),
                        row.email(),
                        result.error()
                ));
                continue;
            }

            validEmails.add(result.normalizedEmail());
        }

        int added = emailJobRepository.insertBatchIfAbsent(
                campaignId,
                validEmails
        );

        int duplicates = validEmails.size() - added;

        errorRepository.insertBatch(importId, errors);

        importRepository.recordBatchProgress(
                importId,
                campaignId,
                expectedProcessedRows,
                rows.size(),
                added,
                duplicates,
                errors.size()
        );
    }
}