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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final UserRepository userRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentEventProducer paymentEventProducer;
    private final PaymentMapper paymentMapper;

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, String authenticatedUserId) {
        validateRequest(request);
        if (!request.getSenderId().equals(authenticatedUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PAYMENT_FORBIDDEN",
                    "Authenticated user can only initiate payments from their own wallet");
        }
        if (!idempotencyService.reserve(request.getTransactionId())) {
            PaymentTransaction existing = paymentTransactionRepository.findById(request.getTransactionId())
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "DUPLICATE_TRANSACTION",
                            "Transaction is already being processed"));
            return paymentMapper.toResponse(existing);
        }

        User sender = userRepository.findById(request.getSenderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SENDER_NOT_FOUND", "Sender not found"));
        User receiver = userRepository.findById(request.getReceiverId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECEIVER_NOT_FOUND", "Receiver not found"));

        if (!sender.getCurrency().equalsIgnoreCase(request.getCurrency())
                || !receiver.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENCY_MISMATCH",
                    "Both users must match the payment currency");
        }

        if (sender.getBalance().compareTo(request.getAmount()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS",
                    "Sender does not have enough balance");
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setTransactionId(request.getTransactionId());
        transaction.setSenderId(request.getSenderId());
        transaction.setReceiverId(request.getReceiverId());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency().toUpperCase());
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setIdempotencyKey(request.getTransactionId());

        PaymentTransaction saved = paymentTransactionRepository.save(transaction);
        paymentEventProducer.publishInitiated(PaymentInitiatedEvent.builder()
                .transactionId(saved.getTransactionId())
                .senderId(saved.getSenderId())
                .receiverId(saved.getReceiverId())
                .amount(saved.getAmount())
                .currency(saved.getCurrency())
                .createdAt(saved.getCreatedAt())
                .build());
        log.info("Payment {} accepted and published", saved.getTransactionId());
        return paymentMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String transactionId) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND",
                        "Transaction not found"));
        return paymentMapper.toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getTransactionHistory(String userId) {
        return paymentTransactionRepository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId)
                .stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getTransactionHistory(String userId, Pageable pageable) {
        return paymentTransactionRepository.findBySenderIdOrReceiverId(userId, userId, pageable)
                .map(paymentMapper::toResponse);
    }

    private void validateRequest(PaymentRequest request) {
        if (request.getSenderId().equals(request.getReceiverId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARTIES",
                    "Sender and receiver must be different");
        }
        if (request.getAmount().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Amount must be greater than zero");
        }
    }
}
