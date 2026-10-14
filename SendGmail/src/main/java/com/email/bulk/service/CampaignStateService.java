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

    public CampaignStateService(
            CampaignRepository campaignRepository) {

        this.campaignRepository = campaignRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAndRequireDraft(long campaignId) {

        String status = campaignRepository.lockStatus(campaignId)
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