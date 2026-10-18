package com.email.bulk.service;

import com.email.bulk.api.dto.RecipientImportHistoryResponse;
import com.email.bulk.api.dto.RecipientImportStatusResponse;
import com.email.bulk.exception.CampaignNotFoundException;
import com.email.bulk.exception.InvalidImportHistoryRequestException;
import com.email.bulk.repository.CampaignRepository;
import com.email.bulk.repository.RecipientImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RecipientImportHistoryService {

    private final CampaignRepository campaignRepository;
    private final RecipientImportRepository importRepository;

    public RecipientImportHistoryService(
            CampaignRepository campaignRepository,
            RecipientImportRepository importRepository) {

        this.campaignRepository = campaignRepository;
        this.importRepository = importRepository;
    }

    @Transactional(readOnly = true)
    public RecipientImportHistoryResponse getHistory(
            long campaignId,
            long beforeId,
            int limit) {

        if (beforeId < 0) {
            throw new InvalidImportHistoryRequestException(
                    "beforeId must be zero or a positive number"
            );
        }

        if (limit < 1 || limit > 100) {
            throw new InvalidImportHistoryRequestException(
                    "limit must be between 1 and 100"
            );
        }

        campaignRepository.findById(campaignId)
                .orElseThrow(
                        () -> new CampaignNotFoundException(campaignId)
                );

        List<RecipientImportStatusResponse> fetched =
                importRepository.findHistory(
                        campaignId,
                        beforeId,
                        limit + 1
                );

        boolean hasMore = fetched.size() > limit;

        List<RecipientImportStatusResponse> imports = List.copyOf(
                fetched.subList(
                        0,
                        Math.min(fetched.size(), limit)
                )
        );

        long nextBeforeId = hasMore
                ? imports.get(imports.size() - 1).importId()
                : 0L;

        return new RecipientImportHistoryResponse(
                campaignId,
                imports,
                hasMore,
                nextBeforeId
        );
    }
}