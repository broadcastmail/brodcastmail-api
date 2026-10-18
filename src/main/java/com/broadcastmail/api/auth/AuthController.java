package com.broadcastmail.api.auth;

import com.broadcastmail.api.account.AccountService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "auth")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AccountService accountService;
    private final CookieService cookieService;

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.clearSessionCookie().toString());
        return ResponseEntity.noContent().build();
    }

}
