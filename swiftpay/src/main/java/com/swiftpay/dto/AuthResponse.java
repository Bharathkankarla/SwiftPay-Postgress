package com.swiftpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthResponse", description = "JWT login response")
public class AuthResponse {
    @Schema(example = "Bearer")
    private String tokenType;

    @Schema(example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    @Schema(example = "sender-1")
    private String userId;

    @Schema(example = "2026-04-16T18:30:00Z")
    private Instant expiresAt;
}
