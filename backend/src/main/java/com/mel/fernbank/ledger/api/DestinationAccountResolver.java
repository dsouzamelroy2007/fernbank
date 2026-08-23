package com.mel.fernbank.ledger.api;

import com.mel.fernbank.ledger.api.dto.TransferRequest;
import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.error.BadRequestException;
import com.mel.fernbank.ledger.repository.AccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves a {@link TransferRequest}'s destination to an account id, either the
 * client-supplied id directly or a lookup by account number. Controllers must not
 * touch {@link AccountRepository} directly (ArchUnit-enforced).
 */
@Component
public class DestinationAccountResolver {

	private final AccountRepository accountRepository;

	public DestinationAccountResolver(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	public UUID resolve(TransferRequest request) {
		boolean hasId = request.destinationAccountId() != null;
		boolean hasNumber = request.destinationAccountNumber() != null;
		if (hasId == hasNumber) {
			throw new BadRequestException(
					"Provide exactly one of destinationAccountId or destinationAccountNumber");
		}
		if (hasId) {
			return request.destinationAccountId();
		}
		Account destination = accountRepository
				.findByAccountNumber(request.destinationAccountNumber())
				.orElseThrow(() -> new BadRequestException(
						"No account found for destinationAccountNumber " + request.destinationAccountNumber()));
		return destination.getId();
	}
}
