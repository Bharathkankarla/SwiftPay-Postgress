package com.swiftpay.dto;

import com.swiftpay.model.TransactionStatus;
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
@Schema(name = "PaymentResponse", description = "Payment transaction details")
public class PaymentResponse {
    @Schema(example = "txn-1001")
    private String transactionId;
    @Schema(example = "sender-1")
    private String senderId;
    @Schema(example = "receiver-1")
    private String receiverId;
    @Schema(example = "25.00")
    private BigDecimal amount;
    @Schema(example = "USD")
    private String currency;
    @Schema(example = "PENDING")
    private TransactionStatus status;
    @Schema(example = "Sender does not have enough balance")
    private String failureReason;
    @Schema(example = "2026-04-16T21:40:12.123456")
    private LocalDateTime createdAt;
    @Schema(example = "2026-04-16T21:40:13.567890")
    private LocalDateTime updatedAt;
}
