package com.broadcastmail.api.campaign.filter;

import com.broadcastmail.api.campaign.filter.dto.FilterRequest;
import com.broadcastmail.api.common.exceptions.InvalidCampaignFilterException;
import com.broadcastmail.api.filterablecolumn.FilterableColumn;
import com.broadcastmail.api.filterablecolumn.FilterableColumnRepository;
import com.broadcastmail.common.campaign.filter.FilterOperator;
import com.broadcastmail.common.campaign.filter.FilterSource;
import com.broadcastmail.common.connection.Connection;
import com.broadcastmail.common.connection.ConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignFilterValidatorTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID CONNECTION_ID = UUID.randomUUID();

    @Mock private ConnectionRepository connectionRepository;
    @Mock private FilterableColumnRepository filterableColumnRepository;
    @InjectMocks private CampaignFilterValidator validator;

    @BeforeEach
    void setUp() {
        Connection connection = new Connection();
        connection.setId(CONNECTION_ID);
        when(connectionRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.of(connection));
        when(filterableColumnRepository.findByConnectionId(CONNECTION_ID)).thenReturn(List.of(
                column("plan", FilterSource.PROFILE_TABLE, true),
                column("hidden", FilterSource.PROFILE_TABLE, false),
                column("last_sign_in_at", FilterSource.AUTH_METADATA, true)));
    }

    private FilterableColumn column(String name, FilterSource source, boolean enabled) {
        return FilterableColumn.builder().connectionId(CONNECTION_ID).columnName(name).columnType("text")
                .displayName(name).source(source).enabled(enabled).cardinalityWarning(false).build();
    }

    private FilterRequest filter(String column, FilterSource source, String jsonKey) {
        return new FilterRequest(column, FilterOperator.EQ, "v", source, jsonKey);
    }

    @Test
    void acceptsEnabledProfileAuthAndJsonFilters() {
        assertThatCode(() -> validator.validateRequests(ACCOUNT_ID, List.of(
                filter("plan", null, null),
                filter("last_sign_in_at", FilterSource.AUTH_METADATA, null),
                filter("raw_user_meta_data", FilterSource.AUTH_METADATA_JSON, "country"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownAndDisabledColumns() {
        assertThatThrownBy(() -> validator.validateRequests(ACCOUNT_ID, List.of(
                filter("missing", null, null), filter("hidden", null, null))))
                .isInstanceOf(InvalidCampaignFilterException.class)
                .hasMessageContaining("missing").hasMessageContaining("hidden");
    }

    @Test
    void rejectsColumnUnderWrongSource() {
        assertThatThrownBy(() -> validator.validateRequests(ACCOUNT_ID, List.of(
                filter("plan", FilterSource.AUTH_METADATA, null))))
                .isInstanceOf(InvalidCampaignFilterException.class);
    }

    @Test
    void rejectsBadJsonColumnOrKey() {
        assertThatThrownBy(() -> validator.validateRequests(ACCOUNT_ID, List.of(
                filter("email", FilterSource.AUTH_METADATA_JSON, "k"),
                filter("raw_user_meta_data", FilterSource.AUTH_METADATA_JSON, "bad key"),
                filter("raw_app_meta_data", FilterSource.AUTH_METADATA_JSON, null))))
                .isInstanceOf(InvalidCampaignFilterException.class)
                .hasMessageContaining("email").hasMessageContaining("bad key");
    }
}
