package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.audit.AuditLogger;
import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.domain.AccountStatus;
import com.mel.fernbank.ledger.repository.AccountRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin account surface. A missing account is a genuine 404 here (unlike the
 * customer-facing endpoints) - an admin is authorized to see any account, so there's no
 * existence to mask.
 */
@Service
public class AdminAccountService {

	private final AccountRepository accountRepository;
	private final AuditLogger auditLogger;

	public AdminAccountService(AccountRepository accountRepository, AuditLogger auditLogger) {
		this.accountRepository = accountRepository;
		this.auditLogger = auditLogger;
	}

	@Transactional
	public AccountResult freeze(UUID accountId, UUID actingUserId, String reason) {
		Account account = accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
		account.setStatus(AccountStatus.FROZEN);
		accountRepository.save(account);

		auditLogger.record(
				actingUserId, "account.frozen", Map.of("accountId", accountId.toString(), "reason", reason));

		return new AccountResult(
				account.getId(),
				account.getAccountNumber(),
				account.getType(),
				account.getCurrency(),
				account.getStatus(),
				account.getCreatedAt());
	}
}
