package com.broadcastmail.api.campaign;

import com.broadcastmail.api.campaign.dto.CreateCampaignRequest;
import com.broadcastmail.api.campaign.dto.UpdateCampaignRequest;
import com.broadcastmail.common.campaign.filter.CampaignFilter;
import com.broadcastmail.common.campaign.filter.CampaignFilterRepository;
import com.broadcastmail.common.campaign.filter.FilterOperator;
import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.api.common.exceptions.CampaignNotEditableException;
import com.broadcastmail.api.common.exceptions.CampaignNotFoundException;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.campaign.Campaign;
import com.broadcastmail.common.campaign.CampaignRepository;
import com.broadcastmail.common.campaign.CampaignStatus;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignFilterRepository campaignFilterRepository;


    @Captor
    private ArgumentCaptor<List<CampaignFilter>> filterCaptor;

    @Mock
    private Connection connection;
    @InjectMocks
    private CampaignService campaignService;

    private Campaign.CampaignBuilder campaign(CampaignStatus status) {
        return Campaign.builder()
                .id(CAMPAIGN_ID)
                .accountId(ACCOUNT_ID)
                .status(status);
    }

    @Test
    void shouldCreateCampaignInDraftStatus() {
        // Given
        CreateCampaignRequest request = new CreateCampaignRequest("Newsletter", "Hello", "<p>Hi</p>", null, connection.getId(), null);
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
        CreateCampaignRequest request = new CreateCampaignRequest("Newsletter", "Hello", "<p>Hi</p>", null, connection.getId(), List.of(
                new FilterRequest("plan", FilterOperator.EQ, "free"),
                new FilterRequest("created_at", FilterOperator.GT, "2024-01-01")
        ));
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
        UpdateCampaignRequest request = new UpdateCampaignRequest(
                Optional.of("New Name"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        when(campaignRepository.findByAccountIdAndId(ACCOUNT_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(existing));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));


        // When
        Campaign result = campaignService.updateCampaign(ACCOUNT_ID, CAMPAIGN_ID, request);

        // Then
        assertThat(result.getName()).isEqualTo("New Name");
    }

    @Test
    void shouldRejectUpdateOnSendingCampaign()
    {
        // Given
        Campaign sending = campaign(CampaignStatus.SENDING).build();
        UpdateCampaignRequest request = new UpdateCampaignRequest(
                Optional.of("New Name"), Optional.empty(), Optional.empty(), Optional.empty());
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
}