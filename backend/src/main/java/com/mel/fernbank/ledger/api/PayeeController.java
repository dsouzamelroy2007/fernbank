package com.mel.fernbank.ledger.api;

import com.mel.fernbank.ledger.api.dto.PayeeRequest;
import com.mel.fernbank.ledger.api.dto.PayeeResponse;
import com.mel.fernbank.ledger.api.mapper.PayeeMapper;
import com.mel.fernbank.ledger.payee.AddPayeeCommand;
import com.mel.fernbank.ledger.payee.PayeeResult;
import com.mel.fernbank.ledger.payee.PayeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payees")
@Tag(name = "Payees")
public class PayeeController {

	private final CurrentCustomerResolver currentCustomerResolver;
	private final PayeeService payeeService;
	private final PayeeMapper payeeMapper;

	public PayeeController(
			CurrentCustomerResolver currentCustomerResolver, PayeeService payeeService, PayeeMapper payeeMapper) {
		this.currentCustomerResolver = currentCustomerResolver;
		this.payeeService = payeeService;
		this.payeeMapper = payeeMapper;
	}

	@Operation(summary = "Save a payee for the authenticated customer")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Payee saved"),
		@ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
		@ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
	})
	@PostMapping
	public ResponseEntity<PayeeResponse> addPayee(
			@Valid @RequestBody PayeeRequest request,
			@IdempotencyKeyHeader @RequestHeader("Idempotency-Key") String idempotencyKey,
			JwtAuthenticationToken authentication) {
		UUID customerId = currentCustomerResolver.resolveCustomerId(authentication);
		UUID userId = userId(authentication);
		PayeeResult result = payeeService.addPayee(
				new AddPayeeCommand(customerId, request.name(), request.targetAccountNumber(), userId, idempotencyKey));
		return ResponseEntity.status(HttpStatus.CREATED).body(payeeMapper.toResponse(result));
	}

	@Operation(summary = "List every payee saved by the authenticated customer")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "The customer's saved payees")})
	@GetMapping
	public List<PayeeResponse> listPayees(JwtAuthenticationToken authentication) {
		UUID customerId = currentCustomerResolver.resolveCustomerId(authentication);
		return payeeService.listPayees(customerId).stream().map(payeeMapper::toResponse).toList();
	}

	@Operation(
			summary = "Delete a payee owned by the authenticated customer",
			description = "Returns 404 (not 403) for a cross-customer payeeId - existence is never leaked.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "Payee deleted"),
		@ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	@DeleteMapping("/{payeeId}")
	public ResponseEntity<Void> deletePayee(@PathVariable UUID payeeId, JwtAuthenticationToken authentication) {
		UUID customerId = currentCustomerResolver.resolveCustomerId(authentication);
		UUID userId = userId(authentication);
		payeeService.deletePayee(payeeId, customerId, userId);
		return ResponseEntity.noContent().build();
	}

	private UUID userId(JwtAuthenticationToken authentication) {
		Jwt jwt = authentication.getToken();
		return UUID.fromString(jwt.getSubject());
	}
}
