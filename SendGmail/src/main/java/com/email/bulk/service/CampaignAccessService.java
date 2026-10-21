package com.email.bulk.service;

import com.email.bulk.exception.CampaignNotFoundException;
import com.email.bulk.repository.CampaignRepository;
import org.springframework.stereotype.Service;

@Service
public class CampaignAccessService {

    private final CampaignRepository campaignRepository;
    private final CurrentUserService currentUserService;

    public CampaignAccessService(
            CampaignRepository campaignRepository,
            CurrentUserService currentUserService) {

        this.campaignRepository = campaignRepository;
        this.currentUserService = currentUserService;
    }

    public void requireOwner(long campaignId) {

        long ownerUserId = currentUserService.requireUserId();

        campaignRepository.findOwnedById(campaignId, ownerUserId)
                .orElseThrow(
                        () -> new CampaignNotFoundException(campaignId)
                );
    }
}