package com.mel.fernbank.ledger.payee;

import com.mel.fernbank.ledger.audit.AuditLogger;
import com.mel.fernbank.ledger.domain.Payee;
import com.mel.fernbank.ledger.idempotency.IdempotencyGuard;
import com.mel.fernbank.ledger.repository.PayeeRepository;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayeeService {

	private final PayeeRepository payeeRepository;
	private final IdempotencyGuard idempotencyGuard;
	private final AuditLogger auditLogger;

	public PayeeService(PayeeRepository payeeRepository, IdempotencyGuard idempotencyGuard, AuditLogger auditLogger) {
		this.payeeRepository = payeeRepository;
		this.idempotencyGuard = idempotencyGuard;
		this.auditLogger = auditLogger;
	}

	@Transactional
	public PayeeResult addPayee(AddPayeeCommand command) {
		return idempotencyGuard.execute(
				command.actingUserId(), command.idempotencyKey(), command, PayeeResult.class, () -> {
					Payee payee = payeeRepository.save(
							new Payee(command.customerId(), command.name(), command.targetAccountNumber()));
					auditLogger.record(command.actingUserId(), "payee.added", Map.of("payeeId", payee.getId().toString()));
					return toResult(payee);
				});
	}

	@Transactional(readOnly = true)
	public List<PayeeResult> listPayees(UUID customerId) {
		return payeeRepository.findByCustomerIdOrderByCreatedAtAsc(customerId).stream()
				.map(this::toResult)
				.toList();
	}

	@Transactional
	public void deletePayee(UUID payeeId, UUID customerId, UUID actingUserId) {
		Payee payee = payeeRepository.findById(payeeId).orElseThrow(NoSuchElementException::new);
		if (!payee.getCustomerId().equals(customerId)) {
			throw new NoSuchElementException();
		}
		payeeRepository.deleteById(payeeId);
		auditLogger.record(actingUserId, "payee.removed", Map.of("payeeId", payeeId.toString()));
	}

	private PayeeResult toResult(Payee payee) {
		return new PayeeResult(payee.getId(), payee.getName(), payee.getTargetAccountNumber(), payee.getCreatedAt());
	}
}
