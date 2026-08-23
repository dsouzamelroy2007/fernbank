package com.mel.fernbank.ledger.api;

import com.mel.fernbank.ledger.api.dto.MoneyCodec;
import com.mel.fernbank.ledger.api.dto.ScheduleTransferRequest;
import com.mel.fernbank.ledger.api.dto.ScheduledTransferResponse;
import com.mel.fernbank.ledger.api.dto.TransferRequest;
import com.mel.fernbank.ledger.api.dto.TransferResponse;
import com.mel.fernbank.ledger.api.mapper.TransferMapper;
import com.mel.fernbank.ledger.banking.ScheduleTransferCommand;
import com.mel.fernbank.ledger.banking.ScheduleTransferService;
import com.mel.fernbank.ledger.banking.TransferCommand;
import com.mel.fernbank.ledger.banking.TransferResult;
import com.mel.fernbank.ledger.banking.TransferService;
import com.mel.fernbank.ledger.domain.ScheduledTransfer;
import com.mel.fernbank.ledger.idempotency.IdempotencyGuard;
import com.mel.fernbank.ledger.security.StepUpAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers")
public class TransferController {

	private final CurrentCustomerResolver currentCustomerResolver;
	private final AccountOwnershipGuard accountOwnershipGuard;
	private final DestinationAccountResolver destinationAccountResolver;
	private final TransferService transferService;
	private final ScheduleTransferService scheduleTransferService;
	private final StepUpAuthService stepUpAuthService;
	private final IdempotencyGuard idempotencyGuard;
	private final MoneyCodec moneyCodec;
	private final TransferMapper transferMapper;

	public TransferController(
			CurrentCustomerResolver currentCustomerResolver,
			AccountOwnershipGuard accountOwnershipGuard,
			DestinationAccountResolver destinationAccountResolver,
			TransferService transferService,
			ScheduleTransferService scheduleTransferService,
			StepUpAuthService stepUpAuthService,
			IdempotencyGuard idempotencyGuard,
			MoneyCodec moneyCodec,
			TransferMapper transferMapper) {
		this.currentCustomerResolver = currentCustomerResolver;
		this.accountOwnershipGuard = accountOwnershipGuard;
		this.destinationAccountResolver = destinationAccountResolver;
		this.transferService = transferService;
		this.scheduleTransferService = scheduleTransferService;
		this.stepUpAuthService = stepUpAuthService;
		this.idempotencyGuard = idempotencyGuard;
		this.moneyCodec = moneyCodec;
		this.transferMapper = transferMapper;
	}

	@Operation(
			summary = "Transfer money between two accounts",
			description = "Destination is either destinationAccountId or destinationAccountNumber, "
					+ "never both. Transfers above the step-up threshold require a recent MFA "
					+ "elevation (POST /api/v1/auth/step-up) or fail with 403 step-up-required.",
			requestBody =
					@io.swagger.v3.oas.annotations.parameters.RequestBody(
							content =
									@Content(
											schema = @Schema(implementation = TransferRequest.class),
											examples =
													@ExampleObject(
															name = "transfer-to-payee",
															value =
																	"""
																	{
																	  "sourceAccountId": "3c1f2e2a-4b8e-4b0a-9b7a-1f2c3d4e5f60",
																	  "destinationAccountNumber": "FB1234567890123456789012",
																	  "amount": {"amount": "125.50", "currency": "USD"},
																	  "description": "Rent"
																	}
																	"""))))
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Transfer applied"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
		@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
		@ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableContent")
	})
	@PostMapping
	public TransferResponse transfer(
			@Valid @RequestBody TransferRequest request,
			@IdempotencyKeyHeader @RequestHeader("Idempotency-Key") String idempotencyKey,
			JwtAuthenticationToken authentication) {
		UUID customerId = currentCustomerResolver.resolveCustomerId(authentication);
		accountOwnershipGuard.requireOwnedAccount(request.sourceAccountId(), customerId);
		UUID destinationAccountId = destinationAccountResolver.resolve(request);
		UUID userId = userId(authentication);
		boolean stepUpVerified = stepUpAuthService.isElevated(authentication.getToken());

		TransferResult result = transferService.transfer(new TransferCommand(
				request.sourceAccountId(),
				destinationAccountId,
				moneyCodec.toDomain(request.amount()),
				request.description(),
				userId,
				idempotencyKey,
				stepUpVerified));
		return transferMapper.toResponse(result);
	}

	@Operation(
			summary = "Schedule a future-dated transfer between two accounts",
			description = "Only supports destinationAccountId (not destinationAccountNumber). A batch "
					+ "job picks it up once scheduledFor is due; step-up elevation is not checked for "
					+ "scheduled transfers. The source account is checked for sufficient funds at "
					+ "scheduling time, but this is not a hold - the balance can still change before "
					+ "scheduledFor. A failed execution retries a few times before being marked FAILED.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Transfer scheduled"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
		@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
		@ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableContent")
	})
	@PostMapping("/scheduled")
	public ResponseEntity<ScheduledTransferResponse> scheduleTransfer(
			@Valid @RequestBody ScheduleTransferRequest request,
			@IdempotencyKeyHeader @RequestHeader("Idempotency-Key") String idempotencyKey,
			JwtAuthenticationToken authentication) {
		UUID customerId = currentCustomerResolver.resolveCustomerId(authentication);
		accountOwnershipGuard.requireOwnedAccount(request.sourceAccountId(), customerId);
		UUID userId = userId(authentication);

		ScheduledTransferResponse response = idempotencyGuard.execute(
				userId, idempotencyKey, request, ScheduledTransferResponse.class, () -> {
					ScheduledTransfer scheduled = scheduleTransferService.schedule(new ScheduleTransferCommand(
							request.sourceAccountId(),
							request.destinationAccountId(),
							moneyCodec.toDomain(request.amount()),
							request.description(),
							request.scheduledFor(),
							userId));
					return transferMapper.toResponse(scheduled);
				});
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	private UUID userId(JwtAuthenticationToken authentication) {
		Jwt jwt = authentication.getToken();
		return UUID.fromString(jwt.getSubject());
	}
}
