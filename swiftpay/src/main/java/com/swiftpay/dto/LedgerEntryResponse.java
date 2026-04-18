package com.swiftpay.dto;

import com.swiftpay.model.EntryType;
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
@Schema(name = "LedgerEntryResponse", description = "Ledger entry for a debit or credit")
public class LedgerEntryResponse {
    @Schema(example = "7d7d0d5d-2d18-4aa3-b4e7-d3b5d7ef2f11")
    private String entryId;
    @Schema(example = "txn-1001")
    private String transactionId;
    @Schema(example = "sender-1")
    private String userId;
    @Schema(example = "DEBIT")
    private EntryType entryType;
    @Schema(example = "25.00")
    private BigDecimal amount;
    @Schema(example = "500.00")
    private BigDecimal balanceBefore;
    @Schema(example = "475.00")
    private BigDecimal balanceAfter;
    @Schema(example = "2026-04-16T21:40:13.567890")
    private LocalDateTime createdAt;
}
