package com.email.bulk.service;

import com.email.bulk.api.dto.CampaignStartResponse;
import com.email.bulk.exception.CampaignConflictException;
import com.email.bulk.repository.CampaignRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignStartService {

    private final CampaignRepository campaignRepository;
    private final CampaignStateService campaignStateService;

    public CampaignStartService(
            CampaignRepository campaignRepository,
            CampaignStateService campaignStateService) {

        this.campaignRepository = campaignRepository;
        this.campaignStateService = campaignStateService;
    }

    @Transactional
    public CampaignStartResponse start(long campaignId) {

        campaignStateService.lockAndRequireDraft(campaignId);

        if (campaignRepository.hasActiveImports(campaignId)) {
            throw new CampaignConflictException(
                    "Campaign cannot start while recipient imports "
                            + "are QUEUED or RUNNING"
            );
        }

        if (!campaignRepository.hasPendingRecipients(campaignId)) {
            throw new CampaignConflictException(
                    "Campaign must contain at least one pending recipient"
            );
        }

        campaignRepository.markRunning(campaignId);

        return new CampaignStartResponse(
                campaignId,
                "RUNNING"
        );
    }
}