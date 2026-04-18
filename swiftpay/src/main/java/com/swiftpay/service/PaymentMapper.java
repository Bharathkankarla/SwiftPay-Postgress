package com.swiftpay.service;

import com.swiftpay.dto.LedgerEntryResponse;
import com.swiftpay.dto.PaymentResponse;
import com.swiftpay.model.LedgerEntry;
import com.swiftpay.model.PaymentTransaction;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {
    public PaymentResponse toResponse(PaymentTransaction transaction) {
        return PaymentResponse.builder()
                .transactionId(transaction.getTransactionId())
                .senderId(transaction.getSenderId())
                .receiverId(transaction.getReceiverId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .status(transaction.getStatus())
                .failureReason(transaction.getFailureReason())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    public LedgerEntryResponse toResponse(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
                .entryId(entry.getEntryId())
                .transactionId(entry.getTransactionId())
                .userId(entry.getUserId())
                .entryType(entry.getEntryType())
                .amount(entry.getAmount())
                .balanceBefore(entry.getBalanceBefore())
                .balanceAfter(entry.getBalanceAfter())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
