package com.swiftpay.service;

import com.swiftpay.dto.LedgerEntryResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {
    private final UserRepository userRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final IdempotencyService idempotencyService;
    private final PaymentMapper paymentMapper;

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000))
    @KafkaListener(topics = "${swiftpay.kafka.topics.payment-initiated:payment.initiated}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumePaymentInitiated(PaymentInitiatedEvent event) {
        processPayment(event);
    }

    @Transactional
    public void processPayment(PaymentInitiatedEvent event) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(event.getTransactionId())
                .orElseThrow(() -> new IllegalStateException("Transaction not found: " + event.getTransactionId()));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            log.info("Skipping transaction {} because it is already {}", transaction.getTransactionId(), transaction.getStatus());
            return;
        }

        User sender = userRepository.findByIdForUpdate(event.getSenderId())
                .orElseThrow(() -> new IllegalStateException("Sender not found: " + event.getSenderId()));
        User receiver = userRepository.findByIdForUpdate(event.getReceiverId())
                .orElseThrow(() -> new IllegalStateException("Receiver not found: " + event.getReceiverId()));

        if (sender.getBalance().compareTo(event.getAmount()) < 0) {
            markFailed(transaction, "Insufficient funds during ledger processing");
            return;
        }

        BigDecimal senderBalanceBefore = sender.getBalance();
        BigDecimal receiverBalanceBefore = receiver.getBalance();
        BigDecimal senderBalanceAfter = senderBalanceBefore.subtract(event.getAmount());
        BigDecimal receiverBalanceAfter = receiverBalanceBefore.add(event.getAmount());

        sender.setBalance(senderBalanceAfter);
        receiver.setBalance(receiverBalanceAfter);
        userRepository.save(sender);
        userRepository.save(receiver);

        ledgerEntryRepository.save(createEntry(transaction.getTransactionId(), sender.getId(), EntryType.DEBIT,
                event.getAmount(), senderBalanceBefore, senderBalanceAfter));
        ledgerEntryRepository.save(createEntry(transaction.getTransactionId(), receiver.getId(), EntryType.CREDIT,
                event.getAmount(), receiverBalanceBefore, receiverBalanceAfter));

        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setFailureReason(null);
        paymentTransactionRepository.save(transaction);
        idempotencyService.markCompleted(transaction.getTransactionId(), TransactionStatus.SUCCESS.name());
        paymentEventProducer.publishResult(PaymentResultEvent.builder()
                .transactionId(transaction.getTransactionId())
                .status(TransactionStatus.SUCCESS)
                .processedAt(LocalDateTime.now())
                .build());
        log.info("Transaction {} processed successfully", transaction.getTransactionId());
    }

    @DltHandler
    public void handleDlt(PaymentInitiatedEvent event,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("DLT: all retries exhausted for transaction {} from topic {}", event.getTransactionId(), topic);
        paymentTransactionRepository.findById(event.getTransactionId()).ifPresent(tx -> {
            if (tx.getStatus() == TransactionStatus.PENDING) {
                markFailed(tx, "Processing failed after all retry attempts");
            }
        });
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getLedgerHistory(String userId, Pageable pageable) {
        return ledgerEntryRepository.findByUserId(userId, pageable)
                .map(paymentMapper::toResponse);
    }

    private LedgerEntry createEntry(String transactionId, String userId, EntryType entryType, BigDecimal amount,
                                    BigDecimal balanceBefore, BigDecimal balanceAfter) {
        LedgerEntry entry = new LedgerEntry();
        entry.setEntryId(UUID.randomUUID().toString());
        entry.setTransactionId(transactionId);
        entry.setUserId(userId);
        entry.setEntryType(entryType);
        entry.setAmount(amount);
        entry.setBalanceBefore(balanceBefore);
        entry.setBalanceAfter(balanceAfter);
        return entry;
    }

    private void markFailed(PaymentTransaction transaction, String reason) {
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setFailureReason(reason);
        paymentTransactionRepository.save(transaction);
        idempotencyService.markCompleted(transaction.getTransactionId(), TransactionStatus.FAILED.name());
        paymentEventProducer.publishResult(PaymentResultEvent.builder()
                .transactionId(transaction.getTransactionId())
                .status(TransactionStatus.FAILED)
                .failureReason(reason)
                .processedAt(LocalDateTime.now())
                .build());
        log.warn("Transaction {} failed: {}", transaction.getTransactionId(), reason);
    }
}
