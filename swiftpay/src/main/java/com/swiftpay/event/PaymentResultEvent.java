package com.swiftpay.event;

import com.swiftpay.model.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultEvent {
    private String transactionId;
    private TransactionStatus status;
    private String failureReason;
    private LocalDateTime processedAt;
}
