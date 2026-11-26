package com.broadcastmail.api.account.plan;

import com.broadcastmail.api.billing.BillingService;
import com.broadcastmail.api.common.exceptions.PlanFeatureException;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.plan.Plan;
import com.broadcastmail.common.account.plan.PlanFeature;
import com.broadcastmail.common.account.plan.PlanLimitExceededException;
import com.broadcastmail.common.usage.RecipientUsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanFacadeTest {

    @Mock
    private BillingService billingService;
    @Mock
    private RecipientUsageRepository recipientUsageRepository;

    @InjectMocks
    private PlanFacade planFacade;

    private Account freeAccount() {
        return Account.builder()
                .id(UUID.randomUUID())
                .plan(Plan.FREE)
                .stripeSubscriptionStatus(null)
                .build();
    }

    private Account proAccount() {
        return Account.builder()
                .id(UUID.randomUUID())
                .plan(Plan.STARTER)
                .stripeSubscriptionStatus("active")
                .build();
    }

    @Test
    void shouldThrowWhenFeatureNotAvailableOnFreePlan() {
        Account account = freeAccount();

        assertThatThrownBy(() -> planFacade.enforceFeature(account, PlanFeature.SCHEDULING))
                .isInstanceOf(PlanFeatureException.class);
    }

    @Test
    void shouldNotThrowWhenFeatureAvailableOnProPlan() {
        Account account = proAccount();
        when(billingService.isSubscriptionActive(account)).thenReturn(true);

        assertThatNoException().isThrownBy(() ->
                planFacade.enforceFeature(account, PlanFeature.SCHEDULING));
    }

    @Test
    void shouldThrowWhenProPlanButSubscriptionInactive() {
        Account account = proAccount();
        when(billingService.isSubscriptionActive(account)).thenReturn(false);

        assertThatThrownBy(() -> planFacade.enforceFeature(account, PlanFeature.SCHEDULING))
                .isInstanceOf(PlanFeatureException.class);
    }

    @Test
    void shouldThrowWhenRecipientLimitExceeded() {
        Account account = freeAccount();
        when(recipientUsageRepository.countSince(eq(account.getId()), any()))
                .thenReturn(490L);

        assertThatThrownBy(() -> planFacade.enforceRecipientLimit(account, 20))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    @Test
    void shouldNotThrowWhenRecipientLimitNotExceeded() {
        Account account = freeAccount();
        when(recipientUsageRepository.countSince(eq(account.getId()), any()))
                .thenReturn(100L);

        assertThatNoException().isThrownBy(() ->
                planFacade.enforceRecipientLimit(account, 20));
    }

    @Test
    void shouldNotEnforceRecipientLimitForActivePro() {
        Account account = proAccount();
        when(recipientUsageRepository.countSince(eq(account.getId()), any()))
                .thenReturn(0L);

        assertThatNoException().isThrownBy(() ->
                planFacade.enforceRecipientLimit(account, 1));

        verify(recipientUsageRepository).countSince(eq(account.getId()), any());
    }
}
