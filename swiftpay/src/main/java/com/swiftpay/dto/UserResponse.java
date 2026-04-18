package com.swiftpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UserResponse", description = "Wallet user details")
public class UserResponse {
    @Schema(example = "sender-1")
    private String id;

    @Schema(example = "Sender One")
    private String fullName;

    @Schema(example = "sender@swiftpay.com")
    private String email;

    @Schema(example = "9000000001")
    private String mobileNumber;

    @Schema(example = "500.00")
    private BigDecimal balance;

    @Schema(example = "USD")
    private String currency;

    @Schema(example = "2026-04-16T21:40:12.123456")
    private LocalDateTime createdAt;
}
