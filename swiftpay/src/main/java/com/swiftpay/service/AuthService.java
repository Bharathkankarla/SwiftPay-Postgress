package com.swiftpay.service;

import com.swiftpay.dto.AuthRequest;
import com.swiftpay.dto.AuthResponse;
import com.swiftpay.exception.ApiException;
import com.swiftpay.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthResponse login(AuthRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUserId(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid user id or password");
        }

        String token = jwtService.generateToken(request.getUserId());
        return AuthResponse.builder()
                .tokenType("Bearer")
                .accessToken(token)
                .userId(request.getUserId())
                .expiresAt(jwtService.getExpiry(token))
                .build();
    }
}
