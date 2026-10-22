package com.broadcastmail.api.campaign;

import com.broadcastmail.api.campaign.dto.CreateCampaignRequest;
import com.broadcastmail.api.campaign.dto.TestEmailRequest;
import com.broadcastmail.api.campaign.dto.UpdateCampaignRequest;
import com.broadcastmail.api.campaign.filter.CampaignFilterValidator;
import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.config.EncryptionProperties;
import com.broadcastmail.api.resend.ResendClient;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.common.campaign.filter.FilterOperator;
import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.api.common.exceptions.CampaignNotEditableException;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.common.campaign.recipient.CampaignRecipientRepository;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
import com.broadcastmail.common.emailprovider.EmailProvider;
import com.broadcastmail.common.emailprovider.EmailProviderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignFilterRepository campaignFilterRepository;
    @Mock
    private CampaignFilterValidator filterValidator;
    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private CampaignRecipientRepository recipientRepository;
    @Mock
    private EmailProviderRepository emailProviderRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ResendClient resendClient;
    @Mock
    private EncryptionProperties encryptionProperties;

    @Captor
    private ArgumentCaptor<List<CampaignFilter>> filterCaptor;

    @Mock
    private Connection connection;
    @InjectMocks
    private CampaignService campaignService;

    private static final String ENCRYPTION_KEY = "12345678901234567890123456789012";
    private static final String TEST_API_KEY = "re_test_abc123";

    private Campaign.CampaignBuilder campaign(CampaignStatus status) {
        return Campaign.builder()
                .id(CAMPAIGN_ID)
                .accountId(ACCOUNT_ID)
                .status(status);
    }

    @Test
    void shouldCreateCampaignInDraftStatus() {
        // Given
        CreateCampaignRequest request = new CreateCampaignRequest("Newsletter", "Hello", "<p>Hi</p>", null, null);
        when(connectionRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(connection));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        Campaign result = campaignService.createCampaign(ACCOUNT_ID, request);

        // Then
        assertThat(result.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(result.getAccountId()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void shouldCreateCampaignWithFiltersAttached() {
        // Given
        CreateCampaignRequest request = new CreateCampaignRequest("Newsletter", "Hello", "<p>Hi</p>", null, List.of(
                new FilterRequest("plan", FilterOperator.EQ, "free", null, null),
                new FilterRequest("created_at", FilterOperator.GT, "2024-01-01", null, null)
        ));
        when(connectionRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(connection));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> {
            Campaign c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        // When
        campaignService.createCampaign(ACCOUNT_ID, request);

        // Then
        verify(campaignFilterRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
    }

    @Test
    void shouldNotReturnCampaignBelongingToAnotherAccount()
    {
        // Given
        UUID wrongAccountId = UUID.randomUUID();
        when(campaignRepository.findByAccountIdAndId(wrongAccountId, CAMPAIGN_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> campaignService.getCampaign(wrongAccountId, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotFoundException.class);
    }

    @Test
    void shouldReturnCampaignBelongingToAccount() {
        // Given
        Campaign campaign = campaign(CampaignStatus.DRAFT).build();
        when(campaignRepository.findByAccountIdAndId(ACCOUNT_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(campaign));

        // When
        Campaign result = campaignService.getCampaign(ACCOUNT_ID, CAMPAIGN_ID);

        // Then
        assertThat(result.getId()).isEqualTo(CAMPAIGN_ID);
    }


    @Test
    void shouldReturnCampaignListOrderedByCreatedAtDesc()
    {
        // Given
        Pageable pageable = PageRequest.of(0,20, Sort.by("created_at").descending());
        List<Campaign> campaigns = List.of(
                campaign(CampaignStatus.DRAFT).id(UUID.randomUUID()).build(),
                campaign(CampaignStatus.DRAFT).id(UUID.randomUUID()).build()
        );
        when(campaignRepository.findByAccountId(ACCOUNT_ID,pageable)).thenReturn(
                new PageImpl<>(campaigns)
        );

        // When
        Page<Campaign> result = campaignService.listCampaigns(ACCOUNT_ID, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(Campaign::getAccountId)
                .containsOnly(ACCOUNT_ID);
    }

    @Test
    void shouldApplyPatchToDraftCampaign()
    {
        Campaign existing = campaign(CampaignStatus.DRAFT).name("Old Name").build();
        UpdateCampaignRequest request = UpdateCampaignRequest.builder()
                .name(Optional.of("New Name"))
                .subject(Optional.empty())
                .bodyHtml(Optional.empty())
                .scheduledAt(Optional.empty())
                .audienceMode(Optional.empty())
                .build();
        when(campaignRepository.findByAccountIdAndId(ACCOUNT_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(existing));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));


        // When
        Campaign result = campaignService.updateCampaign(ACCOUNT_ID, CAMPAIGN_ID, request);

        // Then
        assertThat(result.getName()).isEqualTo("New Name");
    }

    @Test
    void shouldReplaceFiltersWhenProvidedOnUpdate()
    {
        Campaign existing = campaign(CampaignStatus.DRAFT).build();
        UpdateCampaignRequest request = new UpdateCampaignRequest(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                java.util.List.of(new com.broadcastmail.api.campaign.filter.dto.FilterRequest(
                        "plan", com.broadcastmail.common.campaign.filter.FilterOperator.EQ, "pro", null, null)),
                Optional.empty(), null, null);
        when(campaignRepository.findByAccountIdAndId(ACCOUNT_ID, CAMPAIGN_ID)).thenReturn(Optional.of(existing));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        campaignService.updateCampaign(ACCOUNT_ID, CAMPAIGN_ID, request);

        verify(campaignFilterRepository).deleteAll(any());
        verify(campaignFilterRepository).saveAll(org.mockito.ArgumentMatchers.argThat(
                (java.util.List<com.broadcastmail.common.campaign.filter.CampaignFilter> l) -> l.size() == 1));
    }

    @Test
    void shouldLeaveFiltersUntouchedWhenNullOnUpdate()
    {
        Campaign existing = campaign(CampaignStatus.DRAFT).build();
        UpdateCampaignRequest request = new UpdateCampaignRequest(
                Optional.of("X"), Optional.empty(), Optional.empty(), Optional.empty(), null,
                Optional.empty(), null, null);
        when(campaignRepository.findByAccountIdAndId(ACCOUNT_ID, CAMPAIGN_ID)).thenReturn(Optional.of(existing));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        campaignService.updateCampaign(ACCOUNT_ID, CAMPAIGN_ID, request);

        org.mockito.Mockito.verifyNoInteractions(campaignFilterRepository);
    }

    @Test
    void shouldRejectUpdateOnSendingCampaign()
    {
        // Given
        Campaign sending = campaign(CampaignStatus.SENDING).build();
        UpdateCampaignRequest request = new UpdateCampaignRequest(
                Optional.of("New Name"), Optional.empty(), Optional.empty(), Optional.empty(), null,
                Optional.empty(), null, null);
        when(campaignRepository.findByAccountIdAndId(ACCOUNT_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(sending));

        // When / Then
        assertThatThrownBy(() -> campaignService.updateCampaign(ACCOUNT_ID, CAMPAIGN_ID, request))
                .isInstanceOf(CampaignNotEditableException.class);
    }

    @Test
    void shouldDeleteDraftCampaign() {
        // Given
        Campaign draft = campaign(CampaignStatus.DRAFT).build();
        when(campaignRepository.findByAccountIdAndId(ACCOUNT_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(draft));

        // When
        campaignService.deleteCampaign(ACCOUNT_ID, CAMPAIGN_ID);

        // Then
        verify(campaignRepository).deleteById(CAMPAIGN_ID);
    }

    @Test
    void shouldRejectDeleteOnSendingCampaign() {
        // Given
        Campaign sending = campaign(CampaignStatus.SENDING).build();
        when(campaignRepository.findByAccountIdAndId(ACCOUNT_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(sending));

        // When / Then
        assertThatThrownBy(() -> campaignService.deleteCampaign(ACCOUNT_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotEditableException.class);
        verify(campaignRepository, never()).deleteById(any());
    }

    @Test
    void shouldSendTestEmailToAccountOwnerEmail() {
        // Given
        givenTestEmailSetup("<p>Hi {{name}}</p>", "Hello");

        // When
        campaignService.sendTestEmail(ACCOUNT_ID, CAMPAIGN_ID, new TestEmailRequest(Map.of("name", "John")));

        // Then
        verify(resendClient).sendTestEmail(ACCOUNT_ID, TEST_API_KEY, "sender@example.com", "user@example.com", "Hello", "<p>Hi John</p>");
    }

    @Test
    void shouldRejectTestEmailOnNonDraftCampaign() {
        // Given
        when(campaignRepository.findByAccountIdAndId(ACCOUNT_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(campaign(CampaignStatus.SENDING).build()));

        // When / Then
        assertThatThrownBy(() -> campaignService.sendTestEmail(ACCOUNT_ID, CAMPAIGN_ID, new TestEmailRequest(Map.of())))
                .isInstanceOf(CampaignNotEditableException.class);
        verifyNoInteractions(resendClient);
    }

@Test
    void shouldRejectUnscheduleWhenScheduledAtAlreadyPassed() {
        // Given
        Campaign scheduled = campaign(CampaignStatus.RESOLVING)
                .scheduledAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .build();
        when(campaignRepository.findByAccountIdAndId(ACCOUNT_ID, CAMPAIGN_ID)).thenReturn(Optional.of(scheduled));

        // When / Then
        assertThatThrownBy(() -> campaignService.unscheduleCampaign(ACCOUNT_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotEditableException.class);
    }

    @Test
    void shouldRejectUnscheduleWhenNoScheduledAt() {
        // Given
        Campaign resolving = campaign(CampaignStatus.RESOLVING).build();
        when(campaignRepository.findByAccountIdAndId(ACCOUNT_ID, CAMPAIGN_ID)).thenReturn(Optional.of(resolving));

        // When / Then
        assertThatThrownBy(() -> campaignService.unscheduleCampaign(ACCOUNT_ID, CAMPAIGN_ID))
                .isInstanceOf(CampaignNotEditableException.class);
    }

    private void givenTestEmailSetup(String bodyHtml, String subject) {
        EmailProvider provider = mock(EmailProvider.class);
        Account account = mock(Account.class);
        when(provider.getEncryptedApiKey()).thenReturn(SecurityUtil.encrypt(TEST_API_KEY, ENCRYPTION_KEY));
        when(provider.getFromAddress()).thenReturn("sender@example.com");
        when(account.getEmail()).thenReturn("user@example.com");
        when(encryptionProperties.key()).thenReturn(ENCRYPTION_KEY);
        when(campaignRepository.findByAccountIdAndId(ACCOUNT_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(campaign(CampaignStatus.DRAFT).subject(subject).bodyHtml(bodyHtml).build()));
        when(emailProviderRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(provider));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    }
}