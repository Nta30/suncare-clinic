package com.suncare.clinic.modules.account.service;

import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.modules.account.dto.CurrentAccountResponse;
import com.suncare.clinic.modules.account.dto.LoginRequest;
import com.suncare.clinic.modules.account.dto.LoginResponse;
import com.suncare.clinic.modules.account.repository.AccountAuthRepository;
import com.suncare.clinic.modules.account.repository.AccountAuthRepository.Account;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountAuthService {
    private final AccountAuthRepository repository;
    private final JwtTokenService tokens;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AccountAuthService(AccountAuthRepository repository, JwtTokenService tokens) {
        this.repository = repository;
        this.tokens = tokens;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Account account = repository.findByLogin(request.loginName().trim().toLowerCase(Locale.ROOT));
        if (account == null || !passwordEncoder.matches(request.password(), account.passwordHash())
                || !"ACTIVE".equals(account.status())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng.");
        }
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(8, ChronoUnit.HOURS);
        repository.createToken(id, account.id(), expiresAt);
        repository.recordLogin(account.id());
        return new LoginResponse(tokens.issue(account.id(), id, expiresAt), "Bearer", 8 * 60 * 60);
    }

    public CurrentAccountResponse authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw unauthenticated();
        }
        JwtTokenService.TokenIdentity identity = tokens.verify(authorization.substring(7));
        if (identity == null || !repository.isTokenActive(identity.tokenId(), identity.accountId())) {
            throw unauthenticated();
        }
        Account account = repository.findById(identity.accountId());
        if (account == null || !"ACTIVE".equals(account.status())) {
            throw unauthenticated();
        }
        return new CurrentAccountResponse(account.id(), account.loginName(), account.accountType(),
                account.patientId(), account.staffId(), repository.roles(account.id()),
                repository.permissions(account.id()));
    }

    @Transactional
    public void logout(String authorization) {
        authenticate(authorization);
        JwtTokenService.TokenIdentity identity = tokens.verify(authorization.substring(7));
        repository.revokeToken(identity.tokenId(), identity.accountId());
    }

    private ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Cần đăng nhập lại.");
    }
}
