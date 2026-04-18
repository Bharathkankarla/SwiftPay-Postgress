package com.swiftpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateUserRequest", description = "Request payload to create a wallet user")
public class CreateUserRequest {
    @Schema(example = "sender-1")
    @NotBlank
    private String id;

    @Schema(example = "Sender One")
    @NotBlank
    private String fullName;

    @Schema(example = "sender@swiftpay.com")
    @Email
    @NotBlank
    private String email;

    @Schema(example = "9000000001")
    @NotBlank
    private String mobileNumber;

    @Schema(example = "500.00")
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal balance;

    @Schema(example = "USD")
    @NotBlank
    private String currency;

    @Schema(example = "demo123")
    @NotBlank
    private String password;
}
