package com.mel.fernbank.ledger.api;

import com.mel.fernbank.ledger.api.dto.AccountResponse;
import com.mel.fernbank.ledger.api.dto.CursorPage;
import com.mel.fernbank.ledger.api.dto.DepositWithdrawRequest;
import com.mel.fernbank.ledger.api.dto.MoneyCodec;
import com.mel.fernbank.ledger.api.dto.MoneyMovementResponse;
import com.mel.fernbank.ledger.api.dto.OpenAccountRequest;
import com.mel.fernbank.ledger.api.dto.StatementEntryResponse;
import com.mel.fernbank.ledger.api.export.StatementExportService;
import com.mel.fernbank.ledger.api.mapper.AccountMapper;
import com.mel.fernbank.ledger.api.mapper.TransferMapper;
import com.mel.fernbank.ledger.banking.AccountDetail;
import com.mel.fernbank.ledger.banking.AccountQueryService;
import com.mel.fernbank.ledger.banking.AccountResult;
import com.mel.fernbank.ledger.banking.DepositWithdrawCommand;
import com.mel.fernbank.ledger.banking.DepositWithdrawService;
import com.mel.fernbank.ledger.banking.GetStatementService;
import com.mel.fernbank.ledger.banking.MoneyMovementResult;
import com.mel.fernbank.ledger.banking.OpenAccountCommand;
import com.mel.fernbank.ledger.banking.OpenAccountService;
import com.mel.fernbank.ledger.banking.StatementPage;
import com.mel.fernbank.ledger.banking.StatementQuery;
import com.mel.fernbank.ledger.domain.Account;
import com.mel.fernbank.ledger.idempotency.IdempotencyGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
public class AccountController {

	private static final int MAX_PAGE_SIZE = 100;
	private static final int DEFAULT_PAGE_SIZE = 20;

	private final CurrentCustomerResolver currentCustomerResolver;
	private final AccountOwnershipGuard accountOwnershipGuard;
	private final OpenAccountService openAccountService;
	private final AccountQueryService accountQueryService;
	private final DepositWithdrawService depositWithdrawService;
	private final GetStatementService getStatementService;
	private final StatementExportService statementExportService;
	private final IdempotencyGuard idempotencyGuard;
	private final MoneyCodec moneyCodec;
	private final AccountMapper accountMapper;
	private final TransferMapper transferMapper;

	public AccountController(
			CurrentCustomerResolver currentCustomerResolver,
			AccountOwnershipGuard accountOwnershipGuard,
			OpenAccountService openAccountService,
			AccountQueryService accountQueryService,
			DepositWithdrawService depositWithdrawService,
			GetStatementService getStatementService,
			StatementExportService statementExportService,
			IdempotencyGuard idempotencyGuard,
			MoneyCodec moneyCodec,
			AccountMapper accountMapper,
			TransferMapper transferMapper) {
		this.currentCustomerResolver = currentCustomerResolver;
		this.accountOwnershipGuard = accountOwnershipGuard;
		this.openAccountService = openAccountService;
		this.accountQueryService = accountQueryService;
		this.depositWithdrawService = depositWithdrawService;
		this.getStatementService = getStatementService;
		this.statementExportService = statementExportService;
		this.idempotencyGuard = idempotencyGuard;
		this.moneyCodec = moneyCodec;
		this.accountMapper = accountMapper;
		this.transferMapper = transferMapper;
	}

	@Operation(
			summary = "Open a new account for the authenticated customer",
			description = "The account is always opened for the caller's own customer - "
					+ "there is no way to open an account for anyone else.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Account opened"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	@PostMapping
	public ResponseEntity<AccountResponse> openAccount(
			@Valid @RequestBody OpenAccountRequest request,
			@IdempotencyKeyHeader @RequestHeader("Idempotency-Key") String idempotencyKey,
			JwtAuthenticationToken authentication) {
		UUID customerId = currentCustomerResolver.resolveCustomerId(authentication);
		UUID userId = userId(authentication);
		AccountResponse response = idempotencyGuard.execute(
				userId, idempotencyKey, request, AccountResponse.class, () -> {
					AccountResult result = openAccountService.openAccount(
							new OpenAccountCommand(customerId, request.type(), request.currency(), userId));
					return accountMapper.toResponse(accountQueryService.getAccount(result.id()));
				});
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "List every account owned by the authenticated customer")
	@GetMapping
	public List<AccountResponse> listAccounts(JwtAuthenticationToken authentication) {
		UUID customerId = currentCustomerResolver.resolveCustomerId(authentication);
		return accountQueryService.listForCustomer(customerId).stream()
				.map(accountMapper::toResponse)
				.toList();
	}

	@Operation(
			summary = "Get one account owned by the authenticated customer",
			description = "Returns 404 (not 403) for a cross-customer accountId - existence is never "
					+ "leaked. Supports conditional GETs via ETag/If-None-Match.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "The account, with an ETag header"),
		@ApiResponse(responseCode = "304", description = "Not Modified - the supplied If-None-Match matches the current ETag"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	@GetMapping("/{accountId}")
	public ResponseEntity<AccountResponse> getAccount(
			@PathVariable UUID accountId,
			@Parameter(description = "ETag from a previous response; a match returns 304 with no body")
					@RequestHeader(value = "If-None-Match", required = false)
					String ifNoneMatch,
			JwtAuthenticationToken authentication) {
		UUID customerId = currentCustomerResolver.resolveCustomerId(authentication);
		accountOwnershipGuard.requireOwnedAccount(accountId, customerId);
		AccountDetail detail = accountQueryService.getAccount(accountId);
		String etag = "\"%d-%d\"".formatted(detail.accountVersion(), detail.balanceVersion());
		if (etag.equals(ifNoneMatch)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
		}
		return ResponseEntity.ok().eTag(etag).body(accountMapper.toResponse(detail));
	}

	@Operation(
			summary = "Get a cursor-paginated statement of ledger entries for one account",
			description = "Keyset pagination: pass the previous page's nextCursor to fetch the next page.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "A page of statement entries"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	@GetMapping("/{accountId}/statement")
	public CursorPage<StatementEntryResponse> getStatement(
			@PathVariable UUID accountId,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize,
			JwtAuthenticationToken authentication) {
		UUID customerId = currentCustomerResolver.resolveCustomerId(authentication);
		accountOwnershipGuard.requireOwnedAccount(accountId, customerId);

		StatementQuery query = new StatementQuery(
				accountId,
				from == null ? Instant.EPOCH : from,
				to == null ? Instant.now() : to,
				cursor,
				Math.min(pageSize, MAX_PAGE_SIZE));
		StatementPage page = getStatementService.getStatement(query);
		List<StatementEntryResponse> entries = page.entries().stream()
				.map(e -> new StatementEntryResponse(
						e.entryId(), e.transactionId(), e.createdAt(), moneyCodec.toDto(e.amount()), e.description()))
				.toList();
		return new CursorPage<>(entries, page.nextCursor(), page.hasNext());
	}

	@Operation(
			summary = "Export a full-range statement as CSV or PDF",
			description = "Unpaginated - returns every entry in [from,to], capped at 10,000 rows; "
					+ "narrow the range if the cap is exceeded.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "The exported statement file"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	@GetMapping("/{accountId}/statement/export")
	public ResponseEntity<byte[]> exportStatement(
			@PathVariable UUID accountId,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@Parameter(description = "csv or pdf") @RequestParam(defaultValue = "csv") String format,
			JwtAuthenticationToken authentication) {
		UUID customerId = currentCustomerResolver.resolveCustomerId(authentication);
		Account account = accountOwnershipGuard.requireOwnedAccount(accountId, customerId);

		Instant rangeFrom = from == null ? Instant.EPOCH : from;
		Instant rangeTo = to == null ? Instant.now() : to;
		List<StatementEntryResponse> entries = getStatementService.getFullStatement(accountId, rangeFrom, rangeTo).stream()
				.map(e -> new StatementEntryResponse(
						e.entryId(), e.transactionId(), e.createdAt(), moneyCodec.toDto(e.amount()), e.description()))
				.toList();

		boolean pdf = "pdf".equalsIgnoreCase(format);
		byte[] body = pdf
				? statementExportService.toPdf(account.getAccountNumber(), rangeFrom, rangeTo, entries)
				: statementExportService.toCsv(entries);
		String filename = "statement-" + account.getAccountNumber() + (pdf ? ".pdf" : ".csv");
		return ResponseEntity.ok()
				.contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.parseMediaType("text/csv"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.body(body);
	}

	@Operation(
			summary = "Deposit into an account owned by the authenticated customer",
			description = "Allowed even on a FROZEN account - only debits (withdrawals, transfers) are blocked.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Deposit applied"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
		@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	@PostMapping("/{accountId}/deposits")
	public MoneyMovementResponse deposit(
			@PathVariable UUID accountId,
			@Valid @RequestBody DepositWithdrawRequest request,
			@IdempotencyKeyHeader @RequestHeader("Idempotency-Key") String idempotencyKey,
			JwtAuthenticationToken authentication) {
		UUID customerId = currentCustomerResolver.resolveCustomerId(authentication);
		accountOwnershipGuard.requireOwnedAccount(accountId, customerId);
		UUID userId = userId(authentication);
		MoneyMovementResult result = depositWithdrawService.deposit(new DepositWithdrawCommand(
				accountId, moneyCodec.toDomain(request.amount()), request.description(), userId, idempotencyKey));
		return transferMapper.toResponse(result);
	}

	@Operation(
			summary = "Withdraw from an account owned by the authenticated customer",
			description = "Blocked with 409 if the account isn't ACTIVE (e.g. FROZEN).")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Withdrawal applied"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
		@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
		@ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableContent")
	})
	@PostMapping("/{accountId}/withdrawals")
	public MoneyMovementResponse withdraw(
			@PathVariable UUID accountId,
			@Valid @RequestBody DepositWithdrawRequest request,
			@IdempotencyKeyHeader @RequestHeader("Idempotency-Key") String idempotencyKey,
			JwtAuthenticationToken authentication) {
		UUID customerId = currentCustomerResolver.resolveCustomerId(authentication);
		accountOwnershipGuard.requireOwnedAccount(accountId, customerId);
		UUID userId = userId(authentication);
		MoneyMovementResult result = depositWithdrawService.withdraw(new DepositWithdrawCommand(
				accountId, moneyCodec.toDomain(request.amount()), request.description(), userId, idempotencyKey));
		return transferMapper.toResponse(result);
	}

	private UUID userId(JwtAuthenticationToken authentication) {
		Jwt jwt = authentication.getToken();
		return UUID.fromString(jwt.getSubject());
	}
}
