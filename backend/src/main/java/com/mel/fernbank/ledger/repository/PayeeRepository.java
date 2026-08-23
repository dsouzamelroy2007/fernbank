package com.mel.fernbank.ledger.repository;

import com.mel.fernbank.ledger.domain.Payee;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayeeRepository extends JpaRepository<Payee, UUID> {

	List<Payee> findByCustomerIdOrderByCreatedAtAsc(UUID customerId);
}
