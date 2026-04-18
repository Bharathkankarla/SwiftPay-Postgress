package com.swiftpay.service;

import com.swiftpay.event.PaymentInitiatedEvent;
import com.swiftpay.event.PaymentResultEvent;
import com.swiftpay.model.EntryType;
import com.swiftpay.model.LedgerEntry;
import com.swiftpay.model.PaymentTransaction;
import com.swiftpay.model.TransactionStatus;
import com.swiftpay.model.User;
import com.swiftpay.repository.LedgerEntryRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private PaymentEventProducer paymentEventProducer;
    @Mock private IdempotencyService idempotencyService;
    @Spy  private PaymentMapper paymentMapper;

    @InjectMocks
    private LedgerService ledgerService;

    private User sender;
    private User receiver;
    private PaymentTransaction pendingTx;

    @BeforeEach
    void setUp() {
        sender   = new User("s1", "Alice", "alice@test.com", "111", new BigDecimal("500.00"), "USD", "h", LocalDateTime.now());
        receiver = new User("r1", "Bob",   "bob@test.com",   "222", new BigDecimal("100.00"), "USD", "h", LocalDateTime.now());
        pendingTx = new PaymentTransaction("txn-1", "s1", "r1", new BigDecimal("80.00"), "USD",
                TransactionStatus.PENDING, "txn-1", null, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void processPayment_transfersBalanceAndCreatesLedgerEntries() {
        when(paymentTransactionRepository.findById("txn-1")).thenReturn(Optional.of(pendingTx));
        when(userRepository.findByIdForUpdate("s1")).thenReturn(Optional.of(sender));
        when(userRepository.findByIdForUpdate("r1")).thenReturn(Optional.of(receiver));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.processPayment(buildEvent("txn-1", "s1", "r1", new BigDecimal("80.00")));

        assertThat(sender.getBalance()).isEqualByComparingTo("420.00");
        assertThat(receiver.getBalance()).isEqualByComparingTo("180.00");
        assertThat(pendingTx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);

        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());
        List<LedgerEntry> entries = entryCaptor.getAllValues();
        assertThat(entries).anyMatch(e -> e.getEntryType() == EntryType.DEBIT && e.getUserId().equals("s1"));
        assertThat(entries).anyMatch(e -> e.getEntryType() == EntryType.CREDIT && e.getUserId().equals("r1"));

        ArgumentCaptor<PaymentResultEvent> resultCaptor = ArgumentCaptor.forClass(PaymentResultEvent.class);
        verify(paymentEventProducer).publishResult(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    void processPayment_marksFailedOnInsufficientFunds() {
        sender.setBalance(new BigDecimal("10.00"));
        when(paymentTransactionRepository.findById("txn-1")).thenReturn(Optional.of(pendingTx));
        when(userRepository.findByIdForUpdate("s1")).thenReturn(Optional.of(sender));
        when(userRepository.findByIdForUpdate("r1")).thenReturn(Optional.of(receiver));
        when(paymentTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.processPayment(buildEvent("txn-1", "s1", "r1", new BigDecimal("80.00")));

        assertThat(pendingTx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(pendingTx.getFailureReason()).contains("Insufficient");

        ArgumentCaptor<PaymentResultEvent> captor = ArgumentCaptor.forClass(PaymentResultEvent.class);
        verify(paymentEventProducer).publishResult(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void processPayment_skipsAlreadyProcessedTransaction() {
        pendingTx.setStatus(TransactionStatus.SUCCESS);
        when(paymentTransactionRepository.findById("txn-1")).thenReturn(Optional.of(pendingTx));

        ledgerService.processPayment(buildEvent("txn-1", "s1", "r1", new BigDecimal("80.00")));

        verify(userRepository, org.mockito.Mockito.never()).findByIdForUpdate(any());
        verify(ledgerEntryRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void processPayment_throwsWhenTransactionNotFound() {
        when(paymentTransactionRepository.findById("missing")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> ledgerService.processPayment(buildEvent("missing", "s1", "r1", BigDecimal.ONE)));
    }

    private PaymentInitiatedEvent buildEvent(String txnId, String senderId, String receiverId, BigDecimal amount) {
        return PaymentInitiatedEvent.builder()
                .transactionId(txnId)
                .senderId(senderId)
                .receiverId(receiverId)
                .amount(amount)
                .currency("USD")
                .build();
    }
}
