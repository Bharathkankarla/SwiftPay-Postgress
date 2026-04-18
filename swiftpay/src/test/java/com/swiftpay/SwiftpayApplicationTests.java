package com.swiftpay;

import com.swiftpay.dto.PaymentRequest;
import com.swiftpay.dto.PaymentResponse;
import com.swiftpay.exception.ApiException;
import com.swiftpay.event.PaymentInitiatedEvent;
import com.swiftpay.model.PaymentTransaction;
import com.swiftpay.model.TransactionStatus;
import com.swiftpay.model.User;
import com.swiftpay.repository.LedgerEntryRepository;
import com.swiftpay.repository.PaymentTransactionRepository;
import com.swiftpay.repository.UserRepository;
import com.swiftpay.service.IdempotencyService;
import com.swiftpay.service.LedgerService;
import com.swiftpay.service.PaymentEventProducer;
import com.swiftpay.service.PaymentService;
import com.swiftpay.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class SwiftpayApplicationTests {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @MockitoBean
    private PaymentEventProducer paymentEventProducer;

    @MockitoBean
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new User("sender-1", "Sender One", "sender@swiftpay.com", "9000000001",
                new BigDecimal("500.00"), "USD", "hash-1", null));
        userRepository.save(new User("receiver-1", "Receiver One", "receiver@swiftpay.com", "9000000002",
                new BigDecimal("100.00"), "USD", "hash-2", null));
        userRepository.save(new User("sender-2", "Sender Two", "sender2@swiftpay.com", "9000000003",
                new BigDecimal("50.00"), "USD", "hash-3", null));
    }

    @Test
    void createPaymentStoresPendingTransactionAndPublishesEvent() {
        when(idempotencyService.reserve("txn-100")).thenReturn(true);

        PaymentResponse response = paymentService.createPayment(new PaymentRequest(
                "txn-100", "sender-1", "receiver-1", new BigDecimal("25.00"), "USD"
        ), "sender-1");

        PaymentTransaction transaction = paymentTransactionRepository.findById("txn-100").orElseThrow();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);

        ArgumentCaptor<com.swiftpay.event.PaymentInitiatedEvent> captor =
                ArgumentCaptor.forClass(com.swiftpay.event.PaymentInitiatedEvent.class);
        verify(paymentEventProducer).publishInitiated(captor.capture());
        assertThat(captor.getValue().getTransactionId()).isEqualTo("txn-100");
    }

    @Test
    void ledgerProcessingTransfersBalanceAndCreatesEntries() {
        when(idempotencyService.reserve(any())).thenReturn(true);

        paymentService.createPayment(new PaymentRequest(
                "txn-200", "sender-1", "receiver-1", new BigDecimal("40.00"), "USD"
        ), "sender-1");

        ledgerService.processPayment(PaymentInitiatedEvent.builder()
                .transactionId("txn-200")
                .senderId("sender-1")
                .receiverId("receiver-1")
                .amount(new BigDecimal("40.00"))
                .currency("USD")
                .build());

        User sender = userRepository.findById("sender-1").orElseThrow();
        User receiver = userRepository.findById("receiver-1").orElseThrow();
        PaymentTransaction transaction = paymentTransactionRepository.findById("txn-200").orElseThrow();

        assertThat(sender.getBalance()).isEqualByComparingTo("460.00");
        assertThat(receiver.getBalance()).isEqualByComparingTo("140.00");
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(ledgerEntryRepository.findAll()).hasSize(2);
        verify(idempotencyService).markCompleted("txn-200", TransactionStatus.SUCCESS.name());
    }

    @Test
    void createPaymentRejectsInsufficientFunds() {
        when(idempotencyService.reserve("txn-300")).thenReturn(true);

        assertThatThrownBy(() -> paymentService.createPayment(new PaymentRequest(
                "txn-300", "sender-2", "receiver-1", new BigDecimal("75.00"), "USD"
        ), "sender-2"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Sender does not have enough balance");

        assertThat(paymentTransactionRepository.findById("txn-300")).isEmpty();
        verify(paymentEventProducer, never()).publishInitiated(any());
    }

    @Test
    void createPaymentReturnsExistingTransactionForDuplicateIdempotency() {
        paymentTransactionRepository.save(new PaymentTransaction(
                "txn-400",
                "sender-1",
                "receiver-1",
                new BigDecimal("20.00"),
                "USD",
                TransactionStatus.PENDING,
                "txn-400",
                null,
                null,
                null
        ));
        when(idempotencyService.reserve("txn-400")).thenReturn(false);

        PaymentResponse response = paymentService.createPayment(new PaymentRequest(
                "txn-400", "sender-1", "receiver-1", new BigDecimal("20.00"), "USD"
        ), "sender-1");

        assertThat(response.getTransactionId()).isEqualTo("txn-400");
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING);
        verify(paymentEventProducer, never()).publishInitiated(any());
    }

    @Test
    void transactionHistoryReturnsProcessedTransaction() {
        when(idempotencyService.reserve(any())).thenReturn(true);

        paymentService.createPayment(new PaymentRequest(
                "txn-500", "sender-1", "receiver-1", new BigDecimal("10.00"), "USD"
        ), "sender-1");
        ledgerService.processPayment(PaymentInitiatedEvent.builder()
                .transactionId("txn-500")
                .senderId("sender-1")
                .receiverId("receiver-1")
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .build());

        assertThat(paymentService.getTransactionHistory("sender-1"))
                .hasSize(1)
                .first()
                .extracting(PaymentResponse::getTransactionId, PaymentResponse::getStatus)
                .containsExactly("txn-500", TransactionStatus.SUCCESS);
    }

    @Test
    void userServiceCreatesAndReadsUser() {
        userRepository.deleteAll();

        var created = userService.createUser(new com.swiftpay.dto.CreateUserRequest(
                "demo-1", "Demo User", "demo@swiftpay.com", "9000000010", new BigDecimal("250.00"), "USD", "demo123"
        ));

        assertThat(created.getId()).isEqualTo("demo-1");
        assertThat(userService.getUser("demo-1").getEmail()).isEqualTo("demo@swiftpay.com");
        assertThat(userService.getAllUsers()).hasSize(1);
    }

    @Test
    void createPaymentRejectsSenderImpersonation() {
        when(idempotencyService.reserve(any())).thenReturn(true);

        assertThatThrownBy(() -> paymentService.createPayment(new PaymentRequest(
                "txn-600", "sender-1", "receiver-1", new BigDecimal("15.00"), "USD"
        ), "receiver-1"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Authenticated user can only initiate payments from their own wallet");
    }
}
