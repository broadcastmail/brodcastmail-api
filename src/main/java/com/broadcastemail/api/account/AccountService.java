package com.broadcastemail.api.account;

import com.broadcastemail.api.auth.dto.RegisterRequest;
import com.broadcastemail.api.auth.dto.RegisterResponse;
import com.broadcastemail.api.common.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;



    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if(accountRepository.existsByEmail(request.email()))
        {
            throw new IllegalArgumentException("Email already registered");
        }

        String rawApiKey = SecurityUtil.generateApiKey();
        String hashedApiKey = SecurityUtil.sha256(rawApiKey);

        String hashedPassword = passwordEncoder.encode(request.password());

        Account account= Account.builder()
                .email(request.email())
                .passwordHash(hashedPassword)
                .apiKeyHash(hashedApiKey)
                .plan("free")
                .emailVerified(false)
                .uniqueRecipientsThisPeriod(0)
                .periodResetAt(OffsetDateTime.now(ZoneId.systemDefault()))
                .build();

        accountRepository.save(account);
        return new RegisterResponse(
                rawApiKey,
                "Account created successfully"
        );
    }
}
