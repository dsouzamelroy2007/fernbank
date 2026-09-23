package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.audit.AuditLogger;
import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.domain.AccountBalance;
import com.mel.fernbank.ledger.domain.Money;
import com.mel.fernbank.ledger.repository.AccountBalanceRepository;
import com.mel.fernbank.ledger.repository.AccountRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenAccountService {

	private static final Logger log = LoggerFactory.getLogger(OpenAccountService.class);

	private final AccountRepository accountRepository;
	private final AccountBalanceRepository accountBalanceRepository;
	private final AccountNumberGenerator accountNumberGenerator;
	private final AuditLogger auditLogger;

	public OpenAccountService(
			AccountRepository accountRepository,
			AccountBalanceRepository accountBalanceRepository,
			AccountNumberGenerator accountNumberGenerator,
			AuditLogger auditLogger) {
		this.accountRepository = accountRepository;
		this.accountBalanceRepository = accountBalanceRepository;
		this.accountNumberGenerator = accountNumberGenerator;
		this.auditLogger = auditLogger;
	}

	@Transactional
	public AccountResult openAccount(OpenAccountCommand command) {
		log.debug(
				"Opening account for customerId={}, type={}, currency={}, actingUserId={}",
				command.customerId(),
				command.type(),
				command.currency(),
				command.actingUserId());
		String accountNumber = accountNumberGenerator.generate();
		Account account = accountRepository.save(
				new Account(command.customerId(), accountNumber, command.type(), command.currency()));
		accountBalanceRepository.save(new AccountBalance(account.getId(), Money.zero(command.currency())));

		auditLogger.record(
				command.actingUserId(),
				"account.opened",
				Map.of("accountId", account.getId().toString(), "customerId", command.customerId().toString()));
		log.info(
				"Account opened successfully: accountId={}, customerId={}, type={}, currency={}",
				account.getId(),
				command.customerId(),
				account.getType(),
				account.getCurrency());

		return new AccountResult(
				account.getId(),
				account.getAccountNumber(),
				account.getType(),
				account.getCurrency(),
				account.getStatus(),
				account.getCreatedAt());
	}
}
