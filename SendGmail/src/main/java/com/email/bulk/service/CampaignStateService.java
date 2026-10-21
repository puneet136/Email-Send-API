package com.email.bulk.service;

import com.email.bulk.exception.CampaignConflictException;
import com.email.bulk.exception.CampaignNotFoundException;
import com.email.bulk.repository.CampaignRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignStateService {

    private final CampaignRepository campaignRepository;
    private final CurrentUserService currentUserService;

    public CampaignStateService(
            CampaignRepository campaignRepository,
            CurrentUserService currentUserService) {

        this.campaignRepository = campaignRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAndRequireDraft(long campaignId) {

        long ownerUserId = currentUserService.requireUserId();

        String status = campaignRepository
                .lockOwnedStatus(campaignId, ownerUserId)
                .orElseThrow(
                        () -> new CampaignNotFoundException(campaignId)
                );

        if (!"DRAFT".equals(status)) {
            throw new CampaignConflictException(
                    "Campaign " + campaignId
                            + " must be DRAFT for this operation"
                            + "; current status is " + status
            );
        }
    }
}