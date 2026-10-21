package com.email.bulk.service;

import com.email.bulk.api.dto.CampaignProgressResponse;
import com.email.bulk.exception.CampaignNotFoundException;
import com.email.bulk.repository.CampaignRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignProgressService {

    private final CampaignRepository campaignRepository;
    private final CampaignAccessService campaignAccessService;

    public CampaignProgressService(
            CampaignRepository campaignRepository,
            CampaignAccessService campaignAccessService) {

        this.campaignRepository = campaignRepository;
        this.campaignAccessService = campaignAccessService;
    }

    @Transactional(readOnly = true)
    public CampaignProgressResponse getProgress(long campaignId) {

        campaignAccessService.requireOwner(campaignId);

        return campaignRepository.findProgress(campaignId)
                .orElseThrow(
                        () -> new CampaignNotFoundException(campaignId)
                );
    }
}