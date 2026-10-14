package com.email.bulk.service;

import com.email.bulk.api.dto.RecipientImportStatusResponse;
import com.email.bulk.exception.RecipientImportNotFoundException;
import com.email.bulk.repository.RecipientImportRepository;
import org.springframework.stereotype.Service;

@Service
public class RecipientImportStatusService {

    private final RecipientImportRepository importRepository;

    public RecipientImportStatusService(
            RecipientImportRepository importRepository) {

        this.importRepository = importRepository;
    }

    public RecipientImportStatusResponse getStatus(
            long campaignId,
            long importId) {

        return importRepository
                .findStatus(campaignId, importId)
                .orElseThrow(() ->
                        new RecipientImportNotFoundException(
                                campaignId,
                                importId
                        )
                );
    }
}