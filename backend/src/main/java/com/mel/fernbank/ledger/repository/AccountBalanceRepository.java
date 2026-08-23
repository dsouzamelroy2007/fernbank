package com.mel.fernbank.ledger.repository;

import com.mel.fernbank.ledger.domain.AccountBalance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {}
