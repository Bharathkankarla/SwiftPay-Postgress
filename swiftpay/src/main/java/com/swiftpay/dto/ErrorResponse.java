package com.swiftpay.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ErrorResponse", description = "Standard API error payload")
public class ErrorResponse {
    @Schema(example = "INSUFFICIENT_FUNDS")
    private String code;
    @Schema(example = "Sender does not have enough balance")
    private String message;
    @Schema(example = "2026-04-16T21:41:00.123456")
    private LocalDateTime timestamp;
}
