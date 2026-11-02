package com.suncare.clinic.modules.account.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {
    private final byte[] secret;

    public JwtTokenService() {
        String configured = System.getenv("SUNCARE_JWT_SECRET");
        if (configured == null || configured.isBlank()) {
            secret = new byte[32];
            new SecureRandom().nextBytes(secret);
        } else {
            secret = configured.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (secret.length < 32) {
                throw new IllegalStateException("SUNCARE_JWT_SECRET cần ít nhất 32 byte UTF-8.");
            }
        }
    }

    public String issue(long accountId, UUID tokenId, Instant expiresAt) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("suncare-clinic").subject(Long.toString(accountId))
                    .jwtID(tokenId.toString()).issueTime(new Date())
                    .expirationTime(Date.from(expiresAt)).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo token.", exception);
        }
    }

    public TokenIdentity verify(String serialized) {
        try {
            SignedJWT jwt = SignedJWT.parse(serialized);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new MACVerifier(secret))) {
                return null;
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!"suncare-clinic".equals(claims.getIssuer()) || claims.getExpirationTime() == null
                    || !claims.getExpirationTime().toInstant().isAfter(Instant.now())) {
                return null;
            }
            return new TokenIdentity(Long.parseLong(claims.getSubject()), UUID.fromString(claims.getJWTID()));
        } catch (Exception exception) {
            return null;
        }
    }

    public record TokenIdentity(long accountId, UUID tokenId) { }
}
