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
    private final CurrentUserService currentUserService;

    public CampaignService(
            CampaignRepository campaignRepository,
            CurrentUserService currentUserService) {

        this.campaignRepository = campaignRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public CampaignResponse create(CreateCampaignRequest request) {

        long ownerUserId = currentUserService.requireUserId();

        String name = request.name().strip();

        long id = campaignRepository.insertOwned(
                ownerUserId,
                name,
                request.subject(),
                request.message()
        );

        return new CampaignResponse(id, name, "DRAFT");
    }

    @Transactional(readOnly = true)
    public CampaignDetailResponse getById(long id) {

        long ownerUserId = currentUserService.requireUserId();

        return campaignRepository.findOwnedById(id, ownerUserId)
                .orElseThrow(() -> new CampaignNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public CampaignListResponse list(long beforeId, int limit) {

        long ownerUserId = currentUserService.requireUserId();

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

        List<CampaignListItemResponse> fetched =
                campaignRepository.findOwnedPage(
                        ownerUserId,
                        beforeId,
                        limit + 1
                );

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