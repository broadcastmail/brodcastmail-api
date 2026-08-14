package com.broadcastmail.api.account;

import com.broadcastmail.api.common.SecurityUtil;
import com.broadcastmail.api.common.exceptions.AccountNotFoundException;
import com.broadcastmail.common.account.Account;
import com.broadcastmail.common.account.AccountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;

    @Transactional
    public String rotateApiKey(UUID accountId) throws AccountNotFoundException {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);

        String rawKey = SecurityUtil.generateApiKey();
        account.setApiKeyHash(SecurityUtil.sha256(rawKey));
        accountRepository.save(account);

        return rawKey;
    }

}
