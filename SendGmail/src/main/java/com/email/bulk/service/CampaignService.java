package com.email.bulk.service;

import com.email.bulk.api.dto.CampaignResponse;
import com.email.bulk.api.dto.CreateCampaignRequest;
import com.email.bulk.repository.CampaignRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.email.bulk.api.dto.CampaignDetailResponse;
import com.email.bulk.exception.CampaignNotFoundException;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;

    public CampaignService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    @Transactional
    public CampaignResponse create(CreateCampaignRequest request) {

        String name = request.name().strip();

        long id = campaignRepository.insert(
                name,
                request.subject(),
                request.message()
        );

        return new CampaignResponse(id, name, "DRAFT");
    }

    @Transactional(readOnly = true)
    public CampaignDetailResponse getById(long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new CampaignNotFoundException(id));
    }
}