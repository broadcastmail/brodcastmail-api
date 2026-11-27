package com.broadcastmail.api.feedback;

import com.broadcastmail.api.common.exceptions.AccountNotFoundException;
import com.broadcastmail.api.common.exceptions.EmailSendException;
import com.broadcastmail.api.config.FeedbackProperties;
import com.broadcastmail.api.feedback.dto.FeedbackRequest;
import com.broadcastmail.api.feedback.dto.PublicFeedbackRequest;
import com.broadcastmail.api.resend.ResendClient;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    static final String MARKETING_SOURCE = "marketing";

    private final FeedbackRepository feedbackRepository;
    private final AccountRepository accountRepository;
    private final TurnstileValidator turnstileValidator;
    private final ResendClient resendClient;
    private final FeedbackProperties properties;

    public void submit(UUID accountId, FeedbackRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);
        Feedback feedback = feedbackRepository.save(Feedback.builder()
                .accountId(accountId)
                .plan(account.getPlan().name())
                .source(request.source())
                .message(request.message().strip())
                .build());

        email(feedback, account.getEmail(),
                "Account: " + account.getEmail() + " (" + accountId + ")\nPlan: " + feedback.getPlan());
    }

    public void submitPublic(PublicFeedbackRequest request) {
        turnstileValidator.validate(request.turnstileToken());
        String email = blankToNull(request.email());
        Feedback feedback = feedbackRepository.save(Feedback.builder()
                .source(MARKETING_SOURCE)
                .page(blankToNull(request.page()))
                .message(request.message().strip())
                .email(email)
                .build());

        email(feedback, email,
                "Visitor email: " + (email != null ? email : "(none)") + "\nPage: " + feedback.getPage());
    }

    private void email(Feedback feedback, String replyTo, String details) {
        String subject = "Feedback (" + feedback.getSource() + "): " + preview(feedback.getMessage());
        String text = feedback.getMessage() + "\n\n---\nSource: " + feedback.getSource() + "\n" + details
                + "\nFeedback id: " + feedback.getId();
        try {
            resendClient.sendPlainEmail(properties.resendApiKey(), properties.from(), properties.to(), replyTo, subject, text);
        } catch (EmailSendException e) {
            log.warn("[feedback] stored feedback={} but could not email it: {}", feedback.getId(), e.getMessage());
        }
    }

    private static String preview(String message) {
        String oneLine = message.replaceAll("\\s+", " ");
        return oneLine.length() <= 60 ? oneLine : oneLine.substring(0, 60) + "…";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
