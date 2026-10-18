package com.email.bulk.service;

import com.email.bulk.api.dto.CampaignDetailResponse;
import com.email.bulk.api.dto.CampaignListItemResponse;
import com.email.bulk.api.dto.CampaignListResponse;
import com.email.bulk.api.dto.CampaignResponse;
import com.email.bulk.api.dto.CreateCampaignRequest;
import com.email.bulk.exception.CampaignNotFoundException;
import com.email.bulk.exception.InvalidCampaignListRequestException;
import com.email.bulk.repository.CampaignRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional(readOnly = true)
    public CampaignListResponse list(long beforeId, int limit) {

        if (beforeId < 0) {
            throw new InvalidCampaignListRequestException(
                    "beforeId must be zero or a positive number"
            );
        }

        if (limit < 1 || limit > 100) {
            throw new InvalidCampaignListRequestException(
                    "limit must be between 1 and 100"
            );
        }

        // Fetch one extra record to determine whether another page exists.
        List<CampaignListItemResponse> fetched =
                campaignRepository.findPage(beforeId, limit + 1);

        boolean hasMore = fetched.size() > limit;

        List<CampaignListItemResponse> campaigns = List.copyOf(
                fetched.subList(
                        0,
                        Math.min(fetched.size(), limit)
                )
        );

        long nextBeforeId = hasMore
                ? campaigns.get(campaigns.size() - 1).id()
                : 0L;

        return new CampaignListResponse(
                campaigns,
                hasMore,
                nextBeforeId
        );
    }
}