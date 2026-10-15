package com.email.bulk.service;

import com.email.bulk.api.dto.CampaignProgressResponse;
import com.email.bulk.exception.CampaignNotFoundException;
import com.email.bulk.repository.CampaignRepository;
import org.springframework.stereotype.Service;

@Service
public class CampaignProgressService {

    private final CampaignRepository campaignRepository;

    public CampaignProgressService(
            CampaignRepository campaignRepository) {

        this.campaignRepository = campaignRepository;
    }

    public CampaignProgressResponse getProgress(long campaignId) {

        return campaignRepository.findProgress(campaignId)
                .orElseThrow(
                        () -> new CampaignNotFoundException(campaignId)
                );
    }
}