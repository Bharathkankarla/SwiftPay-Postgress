package com.swiftpay.controller;

import com.swiftpay.dto.LedgerEntryResponse;
import com.swiftpay.dto.ErrorResponse;
import com.swiftpay.dto.PaymentRequest;
import com.swiftpay.dto.PaymentResponse;
import com.swiftpay.service.LedgerService;
import com.swiftpay.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final LedgerService ledgerService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Create a payment and publish it for ledger processing")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Payment accepted for processing", content = @Content(
                    schema = @Schema(implementation = PaymentResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "transactionId": "txn-1001",
                              "senderId": "sender-1",
                              "receiverId": "receiver-1",
                              "amount": 25.00,
                              "currency": "USD",
                              "status": "PENDING",
                              "failureReason": null,
                              "createdAt": "2026-04-16T21:40:12.123456",
                              "updatedAt": "2026-04-16T21:40:12.123456"
                            }
                            """)
            )),
            @ApiResponse(responseCode = "400", description = "Validation or balance failure", content = @Content(
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = """
                            {
                              "code": "INSUFFICIENT_FUNDS",
                              "message": "Sender does not have enough balance",
                              "timestamp": "2026-04-16T21:41:00.123456"
                            }
                            """)
            )),
            @ApiResponse(responseCode = "403", description = "Authenticated user does not own sender wallet", content = @Content(
                    schema = @Schema(implementation = ErrorResponse.class)
            )),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(
                    schema = @Schema(implementation = ErrorResponse.class)
            ))
    })
    public PaymentResponse createPayment(@Valid @RequestBody PaymentRequest request, Authentication authentication) {
        return paymentService.createPayment(request, authentication.getName());
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Fetch a transaction by id")
    public PaymentResponse getPayment(@PathVariable String transactionId) {
        return paymentService.getPayment(transactionId);
    }

    @GetMapping("/history/{userId}")
    @Operation(summary = "Fetch transaction history for a specific user (paginated)")
    public Page<PaymentResponse> getTransactionHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return paymentService.getTransactionHistory(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/ledger/{userId}")
    @Operation(summary = "Fetch ledger entries for a specific user (paginated)")
    public Page<LedgerEntryResponse> getLedgerHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ledgerService.getLedgerHistory(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
