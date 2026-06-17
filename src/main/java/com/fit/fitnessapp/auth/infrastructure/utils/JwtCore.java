package com.fit.fitnessapp.auth.infrastructure.utils;

import com.fit.fitnessapp.auth.infrastructure.config.SecurityProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtCore {

    private final SecurityProperties securityProperties;

    public JwtCore(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(securityProperties.secret()));
    }

    public String generateToken(UserDetails userDetails) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(issuedAt.plus(securityProperties.jwtExpiration())))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getNameFromJwt(String token) {
        return Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
