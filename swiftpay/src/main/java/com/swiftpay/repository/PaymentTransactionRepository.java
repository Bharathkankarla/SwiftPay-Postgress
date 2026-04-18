package com.swiftpay.repository;

import com.swiftpay.model.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    List<PaymentTransaction> findBySenderIdOrReceiverIdOrderByCreatedAtDesc(String senderId, String receiverId);

    Page<PaymentTransaction> findBySenderIdOrReceiverId(String senderId, String receiverId, Pageable pageable);
}
