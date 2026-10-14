package com.email.bulk.service;

import com.email.bulk.api.dto.RecipientImportErrorPageResponse;
import com.email.bulk.api.dto.RecipientImportErrorResponse;
import com.email.bulk.repository.RecipientImportErrorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RecipientImportErrorService {

    private final RecipientImportStatusService statusService;
    private final RecipientImportErrorRepository errorRepository;

    public RecipientImportErrorService(
            RecipientImportStatusService statusService,
            RecipientImportErrorRepository errorRepository) {

        this.statusService = statusService;
        this.errorRepository = errorRepository;
    }

    public RecipientImportErrorPageResponse getErrors(
            long campaignId,
            long importId,
            long afterRecord,
            int limit) {

        if (afterRecord < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "afterRecord must be zero or greater"
            );
        }

        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and 100"
            );
        }

        // Verify the import exists under the requested campaign.
        // Reuses our existing 404 handling.
        statusService.getStatus(campaignId, importId);

        List<RecipientImportErrorResponse> fetched =
                errorRepository.findAfterRecord(
                        campaignId,
                        importId,
                        afterRecord,
                        limit + 1
                );

        boolean hasMore = fetched.size() > limit;

        List<RecipientImportErrorResponse> errors =
                List.copyOf(
                        fetched.subList(
                                0,
                                Math.min(fetched.size(), limit)
                        )
                );

        long nextAfterRecord = errors.isEmpty()
                ? afterRecord
                : errors.getLast().recordNumber();

        return new RecipientImportErrorPageResponse(
                importId,
                errors,
                hasMore,
                nextAfterRecord
        );
    }
}