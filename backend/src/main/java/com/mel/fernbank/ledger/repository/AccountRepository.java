package com.mel.fernbank.ledger.repository;

import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.domain.AccountType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByAccountNumber(String accountNumber);

	Optional<Account> findByTypeAndCurrency(AccountType type, String currency);

	List<Account> findByCustomerIdOrderByCreatedAtAsc(UUID customerId);
}
