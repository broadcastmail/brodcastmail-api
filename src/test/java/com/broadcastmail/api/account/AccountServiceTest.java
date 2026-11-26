package com.broadcastmail.api.account;

import com.broadcastmail.api.billing.BillingService;
import com.broadcastmail.api.campaign.CampaignInProgressValidator;
import com.broadcastmail.api.common.exceptions.CampaignInProgressException;
import com.broadcastmail.api.common.exceptions.AccountNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BillingService billingService;

    @Mock
    private CampaignInProgressValidator campaignInProgressValidator;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, billingService, campaignInProgressValidator);
    }

    private Account buildAccount(UUID id) {
        return Account.builder()
                .id(id)
                .email("owner@example.com")
                .passwordHash("")
                .plan(Plan.FREE)
                .emailVerified(true)
                .build();
    }

    @Test
    void shouldNotDeleteTheAccountOrCancelBillingWhileACampaignIsInProgress() {
        // Given
        UUID accountId = UUID.randomUUID();
        doThrow(new CampaignInProgressException(UUID.randomUUID(), "Launch"))
                .when(campaignInProgressValidator).validateNoneInProgress(accountId);

        // When / Then
        assertThatThrownBy(() -> accountService.deleteAccount(accountId))
                .isInstanceOf(CampaignInProgressException.class);
        verifyNoInteractions(billingService, accountRepository);
    }
}
