package com.broadcastmail.api.account;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.AccountNotFoundException;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository);
    }

    private Account buildAccount(UUID id) {
        return Account.builder()
                .id(id)
                .email("owner@example.com")
                .passwordHash("")
                .apiKeyHash(SecurityUtil.sha256("bm_live_oldkey"))
                .plan("free")
                .emailVerified(true)
                .build();
    }

    @Test
    void shouldPersistNewHashedApiKey() {
        // Given
        UUID accountId = UUID.randomUUID();
        Account account = buildAccount(accountId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // When
        String rawKey = accountService.rotateApiKey(accountId);

        // Then
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getApiKeyHash()).isEqualTo(SecurityUtil.sha256(rawKey));
    }

    @Test
    void shouldReturnDifferentKeyOnEachRotation() {
        // Given
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(buildAccount(accountId)));

        // When
        String firstKey = accountService.rotateApiKey(accountId);
        String secondKey = accountService.rotateApiKey(accountId);

        // Then
        assertThat(firstKey).isNotBlank().isNotEqualTo(secondKey);
    }

    @Test
    void shouldThrowWhenAccountDoesNotExist() {
        // Given
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> accountService.rotateApiKey(accountId))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
