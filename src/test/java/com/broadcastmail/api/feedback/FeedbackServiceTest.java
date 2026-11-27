package com.broadcastmail.api.feedback;

import com.broadcastmail.api.common.exceptions.EmailSendException;
import com.broadcastmail.api.config.FeedbackProperties;
import com.broadcastmail.api.feedback.dto.FeedbackRequest;
import com.broadcastmail.api.resend.ResendClient;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.account.plan.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TurnstileValidator turnstileValidator;
    @Mock
    private ResendClient resendClient;

    private final UUID accountId = UUID.randomUUID();

    private FeedbackService feedbackService;

    @BeforeEach
    void setUp() {
        FeedbackProperties properties = new FeedbackProperties(
                "re_test_key", "feedback@batchbeam.com", "laki@batchbeam.com", "secret");
        feedbackService = new FeedbackService(
                feedbackRepository, accountRepository, turnstileValidator, resendClient, properties);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(Account.builder()
                .id(accountId)
                .email("owner@example.com")
                .passwordHash("")
                .plan(Plan.STARTER)
                .emailVerified(true)
                .build()));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldEmailSignedInFeedbackWithReplyToTheAccountEmailAndItsPlan() {
        // Given
        FeedbackRequest request = new FeedbackRequest("How did that go? Great", "first_campaign");

        // When
        feedbackService.submit(accountId, request);

        // Then
        verify(resendClient).sendPlainEmail(eq("re_test_key"), eq("feedback@batchbeam.com"),
                eq("laki@batchbeam.com"), eq("owner@example.com"),
                eq("Feedback (first_campaign): How did that go? Great"), contains("Plan: STARTER"));
    }

    @Test
    void shouldKeepTheFeedbackWhenTheEmailFails() {
        // Given
        doThrow(new EmailSendException("Resend is down")).when(resendClient)
                .sendPlainEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        FeedbackRequest request = new FeedbackRequest("Love it", "app");

        // When / Then: stored before the email, so the request still succeeds
        assertThatCode(() -> feedbackService.submit(accountId, request)).doesNotThrowAnyException();
        verify(feedbackRepository).save(any(Feedback.class));
    }
}
