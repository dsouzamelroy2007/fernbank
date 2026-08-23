package com.mel.fernbank.ledger.repository;

import com.mel.fernbank.ledger.domain.Transaction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {}
