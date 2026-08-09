package com.fit.fitnessapp.auth.infrastructure.utils;

import com.fit.fitnessapp.auth.application.service.UserDetailsService;
import com.fit.fitnessapp.exception.ApiErrorResponseWriter;
import com.fit.fitnessapp.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

@Component
@AllArgsConstructor
public class TokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenFilter.class);

    private final JwtCore jwtCore;
    private final UserDetailsService userDetailsService;
    private final ApiErrorResponseWriter errorResponseWriter;

    @Override
    @SuppressWarnings("null")
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String jwt = null;
        try {
            String headerAuth = request.getHeader("Authorization");
            if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
                jwt = headerAuth.substring(7);
            }

            if (jwt != null) {
                String username = jwtCore.getNameFromJwt(jwt);
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (ExpiredJwtException e) {
            log.warn("Expired token: {}", e.getMessage());
            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED, ErrorCode.TOKEN_EXPIRED, "Token expired");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid token: {}", e.getMessage());
            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_TOKEN, "Invalid token");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
