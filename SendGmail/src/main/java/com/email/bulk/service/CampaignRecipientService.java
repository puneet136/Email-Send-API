package com.email.bulk.service;

import com.email.bulk.api.dto.AddRecipientsRequest;
import com.email.bulk.api.dto.AddRecipientsResponse;
import com.email.bulk.exception.CampaignNotFoundException;
import com.email.bulk.repository.CampaignRepository;
import com.email.bulk.repository.EmailJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class CampaignRecipientService {

    private final CampaignRepository campaignRepository;
    private final EmailJobRepository emailJobRepository;

    public CampaignRecipientService(
            CampaignRepository campaignRepository,
            EmailJobRepository emailJobRepository) {

        this.campaignRepository = campaignRepository;
        this.emailJobRepository = emailJobRepository;
    }

    @Transactional
    public AddRecipientsResponse addRecipients(
            long campaignId,
            AddRecipientsRequest request) {

        campaignRepository.findById(campaignId)
                .orElseThrow(
                        () -> new CampaignNotFoundException(campaignId)
                );

        int added = 0;
        int duplicates = 0;

        for (String email : request.emails()) {

            String normalizedEmail = normalizeEmail(email);

            boolean inserted = emailJobRepository.insertIfAbsent(
                    campaignId,
                    normalizedEmail
            );

            if (inserted) {
                added++;
            } else {
                duplicates++;
            }
        }

        return new AddRecipientsResponse(
                campaignId,
                request.emails().size(),
                added,
                duplicates
        );
    }

    private String normalizeEmail(String email) {

        int atIndex = email.lastIndexOf('@');

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1)
                .toLowerCase(Locale.ROOT);

        return localPart + "@" + domain;
    }
}