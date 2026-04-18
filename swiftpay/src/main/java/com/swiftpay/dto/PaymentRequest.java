package com.swiftpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PaymentRequest", description = "Request payload to initiate a payment")
public class PaymentRequest {
    @Schema(example = "txn-1001")
    @NotBlank
    private String transactionId;

    @Schema(example = "sender-1")
    @NotBlank
    private String senderId;

    @Schema(example = "receiver-1")
    @NotBlank
    private String receiverId;

    @Schema(example = "25.00")
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @Schema(example = "USD")
    @NotBlank
    private String currency;
}
