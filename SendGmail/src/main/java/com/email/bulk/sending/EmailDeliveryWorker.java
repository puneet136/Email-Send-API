package com.email.bulk.sending;

import com.email.bulk.repository.EmailDeliveryRepository;
import com.email.bulk.service.CampaignEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.email.worker",
        name = "enabled",
        havingValue = "true"
)
public class EmailDeliveryWorker {

    private static final Logger log =
            LoggerFactory.getLogger(EmailDeliveryWorker.class);

    private final EmailDeliveryRepository deliveryRepository;
    private final CampaignEmailSender emailSender;
    private final int leaseSeconds;
    private final int maxAttempts;
    private final int retryDelaySeconds;

    public EmailDeliveryWorker(
            EmailDeliveryRepository deliveryRepository,
            CampaignEmailSender emailSender,
            @Value("${app.email.worker.lease-seconds:300}")
            int leaseSeconds,
            @Value("${app.email.worker.max-attempts:3}")
            int maxAttempts,
            @Value("${app.email.worker.retry-delay-seconds:30}")
            int retryDelaySeconds) {

        if (leaseSeconds < 30 || leaseSeconds > 3600) {
            throw new IllegalArgumentException(
                    "Lease duration must be between 30 and 3600 seconds"
            );
        }

        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException(
                    "Maximum attempts must be between 1 and 10"
            );
        }

        if (retryDelaySeconds < 1 || retryDelaySeconds > 86400) {
            throw new IllegalArgumentException(
                    "Retry delay must be between 1 and 86400 seconds"
            );
        }

        this.deliveryRepository = deliveryRepository;
        this.emailSender = emailSender;
        this.leaseSeconds = leaseSeconds;
        this.maxAttempts = maxAttempts;
        this.retryDelaySeconds = retryDelaySeconds;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.email.worker.poll-delay-ms:5000}",
            initialDelayString =
                    "${app.email.worker.poll-delay-ms:5000}"
    )
    public void poll() {

        try {
            int recovered = deliveryRepository.recoverExpiredJobs(
                    maxAttempts,
                    retryDelaySeconds
            );

            if (recovered > 0) {
                log.warn(
                        "Recovered {} expired email sending jobs",
                        recovered
                );
            }

            deliveryRepository
                    .claimNext(leaseSeconds, maxAttempts)
                    .ifPresent(this::deliver);

        } catch (RuntimeException exception) {
            log.error("Email delivery polling failed", exception);
        }

        // Run even when no eligible email job was found.
        try {
            int completed =
                    deliveryRepository.completeFinishedCampaigns();

            if (completed > 0) {
                log.info(
                        "Finished {} campaigns",
                        completed
                );
            }

        } catch (RuntimeException exception) {
            log.error(
                    "Campaign completion check failed",
                    exception
            );
        }
    }

    private void deliver(ClaimedEmailJob work) {

        // SMTP runs after the claim transaction has committed.
        try {
            emailSender.send(
                    work.recipientEmail(),
                    work.subject(),
                    work.body()
            );

        } catch (MailException | IllegalArgumentException exception) {
            log.error(
                    "Email job {} sending attempt {} failed",
                    work.jobId(),
                    work.attemptCount(),
                    exception
            );

            recordFailure(work);
            return;
        }

        // Keep persistence failures separate from SMTP failures.
        try {
            boolean updated = deliveryRepository.markSent(work);

            if (updated) {
                log.info(
                        "Email job {} recorded as SENT on attempt {}",
                        work.jobId(),
                        work.attemptCount()
                );

            } else {
                log.warn(
                        "SMTP accepted email job {}, but SENT update "
                                + "was rejected by ownership/status checks; "
                                + "a later retry may duplicate the email",
                        work.jobId()
                );
            }

        } catch (RuntimeException exception) {
            log.error(
                    "SMTP accepted email job {}, but saving SENT failed; "
                            + "a later retry may duplicate the email",
                    work.jobId(),
                    exception
            );
        }
    }

    private void recordFailure(ClaimedEmailJob work) {

        try {
            boolean updated = deliveryRepository.recordFailure(
                    work,
                    "Email sending attempt failed. "
                            + "Check application logs.",
                    maxAttempts,
                    retryDelaySeconds
            );

            if (!updated) {
                log.warn(
                        "Failure result was not saved for email job {}: "
                                + "ownership, lease or status changed",
                        work.jobId()
                );
            }

        } catch (RuntimeException exception) {
            log.error(
                    "Could not save failure result for email job {}",
                    work.jobId(),
                    exception
            );
        }
    }
}