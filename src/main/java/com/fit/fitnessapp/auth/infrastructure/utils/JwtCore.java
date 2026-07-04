package com.fit.fitnessapp.auth.infrastructure.utils;

import com.fit.fitnessapp.auth.infrastructure.config.SecurityProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

@Service
public class JwtCore {

    private final SecurityProperties securityProperties;

    public JwtCore(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(securityProperties.secret()));
    }

    public String generateToken(UserDetails userDetails) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(securityProperties.jwtExpiration())))
                .signWith(getSigningKey())
                .compact();
    }

    public String getNameFromJwt(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
