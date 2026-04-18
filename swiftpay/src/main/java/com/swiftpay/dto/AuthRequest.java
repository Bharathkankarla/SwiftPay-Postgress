package com.swiftpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthRequest", description = "Login request for JWT token issuance")
public class AuthRequest {
    @Schema(example = "sender-1")
    @NotBlank
    private String userId;

    @Schema(example = "demo123")
    @NotBlank
    private String password;
}
