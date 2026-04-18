package com.swiftpay.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.dto.LedgerEntryResponse;
import com.swiftpay.dto.PaymentRequest;
import com.swiftpay.dto.PaymentResponse;
import com.swiftpay.exception.ApiException;
import com.swiftpay.model.EntryType;
import com.swiftpay.model.TransactionStatus;
import com.swiftpay.security.JwtAuthenticationFilter;
import com.swiftpay.service.LedgerService;
import com.swiftpay.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PaymentController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean PaymentService paymentService;
    @MockitoBean LedgerService ledgerService;

    @Test
    @WithMockUser(username = "sender-1")
    void postPayment_returns202OnSuccess() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .transactionId("txn-1")
                .senderId("sender-1")
                .receiverId("receiver-1")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .status(TransactionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(paymentService.createPayment(any(PaymentRequest.class), eq("sender-1"))).thenReturn(response);

        mockMvc.perform(post("/v1/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentRequest("txn-1", "sender-1", "receiver-1", new BigDecimal("50.00"), "USD"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value("txn-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "sender-1")
    void postPayment_returns400OnInsufficientFunds() throws Exception {
        when(paymentService.createPayment(any(), any()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS", "Sender does not have enough balance"));

        mockMvc.perform(post("/v1/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentRequest("txn-2", "sender-1", "receiver-1", new BigDecimal("9999.00"), "USD"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void postPayment_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/v1/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentRequest("txn-3", "sender-1", "receiver-1", new BigDecimal("10.00"), "USD"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getTransactionHistory_returnsPaginatedResults() throws Exception {
        PaymentResponse item = PaymentResponse.builder()
                .transactionId("txn-10")
                .status(TransactionStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(paymentService.getTransactionHistory(eq("sender-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/v1/payments/history/sender-1")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionId").value("txn-10"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void getLedgerHistory_returnsPaginatedResults() throws Exception {
        LedgerEntryResponse entry = LedgerEntryResponse.builder()
                .entryId("e-1")
                .transactionId("txn-10")
                .userId("sender-1")
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("50.00"))
                .balanceBefore(new BigDecimal("500.00"))
                .balanceAfter(new BigDecimal("450.00"))
                .createdAt(LocalDateTime.now())
                .build();

        when(ledgerService.getLedgerHistory(eq("sender-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        mockMvc.perform(get("/v1/payments/ledger/sender-1")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entryId").value("e-1"))
                .andExpect(jsonPath("$.content[0].entryType").value("DEBIT"));
    }
}
