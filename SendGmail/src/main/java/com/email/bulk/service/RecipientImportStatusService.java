package com.email.bulk.service;

import com.email.bulk.api.dto.RecipientImportStatusResponse;
import com.email.bulk.exception.RecipientImportNotFoundException;
import com.email.bulk.repository.RecipientImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipientImportStatusService {

    private final RecipientImportRepository importRepository;
    private final CampaignAccessService campaignAccessService;

    public RecipientImportStatusService(
            RecipientImportRepository importRepository,
            CampaignAccessService campaignAccessService) {

        this.importRepository = importRepository;
        this.campaignAccessService = campaignAccessService;
    }

    @Transactional(readOnly = true)
    public RecipientImportStatusResponse getStatus(
            long campaignId,
            long importId) {

        campaignAccessService.requireOwner(campaignId);

        return importRepository
                .findStatus(campaignId, importId)
                .orElseThrow(
                        () -> new RecipientImportNotFoundException(
                                campaignId,
                                importId
                        )
                );
    }
}