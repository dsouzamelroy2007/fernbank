package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.domain.AccountBalance;
import com.mel.fernbank.ledger.repository.AccountBalanceRepository;
import com.mel.fernbank.ledger.repository.AccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only account+balance lookups. Ownership-agnostic - callers check access first. */
@Service
public class AccountQueryService {

	private final AccountRepository accountRepository;
	private final AccountBalanceRepository accountBalanceRepository;

	public AccountQueryService(AccountRepository accountRepository, AccountBalanceRepository accountBalanceRepository) {
		this.accountRepository = accountRepository;
		this.accountBalanceRepository = accountBalanceRepository;
	}

	@Transactional(readOnly = true)
	public AccountDetail getAccount(UUID accountId) {
		Account account = accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
		AccountBalance balance = accountBalanceRepository
				.findById(accountId)
				.orElseThrow(() -> new AccountNotFoundException(accountId));
		return toDetail(account, balance);
	}

	@Transactional(readOnly = true)
	public List<AccountDetail> listForCustomer(UUID customerId) {
		return accountRepository.findByCustomerIdOrderByCreatedAtAsc(customerId).stream()
				.map(account -> toDetail(
						account,
						accountBalanceRepository
								.findById(account.getId())
								.orElseThrow(() -> new AccountNotFoundException(account.getId()))))
				.toList();
	}

	private AccountDetail toDetail(Account account, AccountBalance balance) {
		return new AccountDetail(
				account.getId(),
				account.getAccountNumber(),
				account.getType(),
				account.getStatus(),
				balance.getBalance(),
				account.getVersion(),
				balance.getVersion(),
				account.getCreatedAt());
	}
}
