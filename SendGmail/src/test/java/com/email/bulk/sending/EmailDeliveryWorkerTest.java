package com.email.bulk.sending;

import com.email.bulk.repository.EmailDeliveryRepository;
import com.email.bulk.service.CampaignEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mail.MailSendException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailDeliveryWorkerTest {

    private EmailDeliveryRepository repository;
    private CampaignEmailSender sender;
    private EmailDeliveryWorker worker;
    private ClaimedEmailJob work;

    @BeforeEach
    void setUp() {

        repository = mock(EmailDeliveryRepository.class);
        sender = mock(CampaignEmailSender.class);

        worker = new EmailDeliveryWorker(
                repository,
                sender,
                300,
                3,
                30
        );

        work = new ClaimedEmailJob(
                101L,
                10L,
                "recipient@example.com",
                "Test subject",
                "Test body",
                1,
                "test-worker-token"
        );
    }

    @Test
    void sendsThenMarksSentAndChecksCompletion() {

        givenClaimedJob();
        when(repository.markSent(work)).thenReturn(true);

        worker.poll();

        InOrder order = inOrder(repository, sender);

        order.verify(repository).recoverExpiredJobs(3, 30);
        order.verify(repository).claimNext(300, 3);

        order.verify(sender).send(
                work.recipientEmail(),
                work.subject(),
                work.body()
        );

        order.verify(repository).markSent(work);
        order.verify(repository).completeFinishedCampaigns();

        verifyNoFailureRecorded();
    }

    @Test
    void recordsSmtpFailureWithoutMarkingSent() {

        givenClaimedJob();

        doThrow(new MailSendException("Simulated SMTP outage"))
                .when(sender)
                .send(
                        work.recipientEmail(),
                        work.subject(),
                        work.body()
                );

        when(repository.recordFailure(
                eq(work),
                anyString(),
                eq(3),
                eq(30)
        )).thenReturn(true);

        assertDoesNotThrow(worker::poll);

        verify(repository).recordFailure(
                eq(work),
                anyString(),
                eq(3),
                eq(30)
        );

        verify(repository, never()).markSent(any());
        verify(repository).completeFinishedCampaigns();
    }

    @Test
    void doesNotRecordSmtpFailureWhenSavingSentThrows() {

        givenClaimedJob();

        when(repository.markSent(work))
                .thenThrow(
                        new IllegalStateException(
                                "Simulated database failure"
                        )
                );

        assertDoesNotThrow(worker::poll);

        verify(sender, times(1)).send(
                work.recipientEmail(),
                work.subject(),
                work.body()
        );

        verify(repository).markSent(work);

        // SMTP already succeeded. Do not classify this as send failure.
        verifyNoFailureRecorded();

        verify(repository).completeFinishedCampaigns();
    }

    @Test
    void doesNotRecordSmtpFailureWhenSentUpdateIsRejected() {

        givenClaimedJob();
        when(repository.markSent(work)).thenReturn(false);

        assertDoesNotThrow(worker::poll);

        verify(sender, times(1)).send(
                work.recipientEmail(),
                work.subject(),
                work.body()
        );

        verify(repository).markSent(work);
        verifyNoFailureRecorded();

        verify(repository).completeFinishedCampaigns();
    }

    @Test
    void checksCompletionEvenWhenNoJobIsAvailable() {

        when(repository.claimNext(300, 3))
                .thenReturn(Optional.empty());

        worker.poll();

        verify(repository).recoverExpiredJobs(3, 30);
        verify(repository).claimNext(300, 3);
        verify(repository).completeFinishedCampaigns();

        verifyNoInteractions(sender);
        verify(repository, never()).markSent(any());
        verifyNoFailureRecorded();
    }

    @Test
    void checksCompletionEvenWhenClaimFails() {

        when(repository.claimNext(300, 3))
                .thenThrow(
                        new IllegalStateException(
                                "Simulated claim failure"
                        )
                );

        assertDoesNotThrow(worker::poll);

        verify(repository).completeFinishedCampaigns();
        verifyNoInteractions(sender);
        verifyNoFailureRecorded();
    }

    @Test
    void checksCompletionEvenWhenSavingFailureThrows() {

        givenClaimedJob();

        doThrow(new MailSendException("Simulated SMTP outage"))
                .when(sender)
                .send(
                        work.recipientEmail(),
                        work.subject(),
                        work.body()
                );

        when(repository.recordFailure(
                eq(work),
                anyString(),
                eq(3),
                eq(30)
        )).thenThrow(
                new IllegalStateException(
                        "Simulated failure-record persistence error"
                )
        );

        assertDoesNotThrow(worker::poll);

        verify(repository).recordFailure(
                eq(work),
                anyString(),
                eq(3),
                eq(30)
        );

        verify(repository, never()).markSent(any());
        verify(repository).completeFinishedCampaigns();
    }

    @Test
    void handlesCompletionCheckFailureWithoutThrowing() {

        when(repository.claimNext(300, 3))
                .thenReturn(Optional.empty());

        when(repository.completeFinishedCampaigns())
                .thenThrow(
                        new IllegalStateException(
                                "Simulated completion query failure"
                        )
                );

        assertDoesNotThrow(worker::poll);

        verify(repository).completeFinishedCampaigns();
        verifyNoInteractions(sender);
    }

    private void givenClaimedJob() {

        when(repository.claimNext(300, 3))
                .thenReturn(Optional.of(work));
    }

    private void verifyNoFailureRecorded() {

        verify(repository, never()).recordFailure(
                any(ClaimedEmailJob.class),
                anyString(),
                anyInt(),
                anyInt()
        );
    }
}