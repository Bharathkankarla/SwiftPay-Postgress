package com.swiftpay.repository;

import com.swiftpay.model.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {
    List<LedgerEntry> findByUserIdOrderByCreatedAtDesc(String userId);
    Page<LedgerEntry> findByUserId(String userId, Pageable pageable);
}
