package com.swiftpay.service;

import com.swiftpay.dto.PaymentRequest;
import com.swiftpay.dto.PaymentResponse;
import com.swiftpay.event.PaymentInitiatedEvent;
import com.swiftpay.exception.ApiException;
import com.swiftpay.model.PaymentTransaction;
import com.swiftpay.model.TransactionStatus;
import com.swiftpay.model.User;
import com.swiftpay.repository.PaymentTransactionRepository;
import com.swiftpay.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private PaymentEventProducer paymentEventProducer;
    @Spy  private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentService paymentService;

    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        sender   = new User("sender-1", "Alice", "alice@test.com", "111", new BigDecimal("500.00"), "USD", "hash", LocalDateTime.now());
        receiver = new User("receiver-1", "Bob",   "bob@test.com",   "222", new BigDecimal("100.00"), "USD", "hash", LocalDateTime.now());
    }

    @Test
    void createPayment_savesTransactionAndPublishesEvent() {
        when(idempotencyService.reserve("txn-1")).thenReturn(true);
        when(userRepository.findById("sender-1")).thenReturn(Optional.of(sender));
        when(userRepository.findById("receiver-1")).thenReturn(Optional.of(receiver));
        when(paymentTransactionRepository.save(any())).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            tx.setCreatedAt(LocalDateTime.now());
            tx.setUpdatedAt(LocalDateTime.now());
            return tx;
        });

        PaymentResponse response = paymentService.createPayment(
                new PaymentRequest("txn-1", "sender-1", "receiver-1", new BigDecimal("50.00"), "USD"),
                "sender-1");

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(response.getTransactionId()).isEqualTo("txn-1");

        ArgumentCaptor<PaymentInitiatedEvent> captor = ArgumentCaptor.forClass(PaymentInitiatedEvent.class);
        verify(paymentEventProducer).publishInitiated(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void createPayment_rejectsInsufficientFunds() {
        when(idempotencyService.reserve("txn-2")).thenReturn(true);
        when(userRepository.findById("sender-1")).thenReturn(Optional.of(sender));
        when(userRepository.findById("receiver-1")).thenReturn(Optional.of(receiver));

        assertThatThrownBy(() -> paymentService.createPayment(
                new PaymentRequest("txn-2", "sender-1", "receiver-1", new BigDecimal("999.00"), "USD"),
                "sender-1"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Sender does not have enough balance");

        verify(paymentTransactionRepository, never()).save(any());
        verify(paymentEventProducer, never()).publishInitiated(any());
    }

    @Test
    void createPayment_rejectsSenderImpersonation() {
        when(idempotencyService.reserve("txn-3")).thenReturn(true);

        assertThatThrownBy(() -> paymentService.createPayment(
                new PaymentRequest("txn-3", "sender-1", "receiver-1", new BigDecimal("10.00"), "USD"),
                "receiver-1"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createPayment_rejectsSameParties() {
        when(idempotencyService.reserve("txn-4")).thenReturn(true);

        assertThatThrownBy(() -> paymentService.createPayment(
                new PaymentRequest("txn-4", "sender-1", "sender-1", new BigDecimal("10.00"), "USD"),
                "sender-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Sender and receiver must be different");
    }

    @Test
    void createPayment_rejectsCurrencyMismatch() {
        sender.setCurrency("EUR");
        when(idempotencyService.reserve("txn-5")).thenReturn(true);
        when(userRepository.findById("sender-1")).thenReturn(Optional.of(sender));
        when(userRepository.findById("receiver-1")).thenReturn(Optional.of(receiver));

        assertThatThrownBy(() -> paymentService.createPayment(
                new PaymentRequest("txn-5", "sender-1", "receiver-1", new BigDecimal("10.00"), "USD"),
                "sender-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void createPayment_returnsExistingOnDuplicateIdempotency() {
        PaymentTransaction existing = new PaymentTransaction(
                "txn-6", "sender-1", "receiver-1", new BigDecimal("20.00"), "USD",
                TransactionStatus.PENDING, "txn-6", null, LocalDateTime.now(), LocalDateTime.now());

        when(idempotencyService.reserve("txn-6")).thenReturn(false);
        when(paymentTransactionRepository.findById("txn-6")).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.createPayment(
                new PaymentRequest("txn-6", "sender-1", "receiver-1", new BigDecimal("20.00"), "USD"),
                "sender-1");

        assertThat(response.getTransactionId()).isEqualTo("txn-6");
        verify(paymentEventProducer, never()).publishInitiated(any());
    }

    @Test
    void getPayment_throwsWhenNotFound() {
        when(paymentTransactionRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment("unknown"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
