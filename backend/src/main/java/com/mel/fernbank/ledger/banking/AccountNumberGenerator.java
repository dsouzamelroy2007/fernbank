package com.mel.fernbank.ledger.banking;

import com.mel.fernbank.ledger.repository.AccountRepository;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class AccountNumberGenerator {

	private static final String COUNTRY_CODE = "FB";
	private static final int BBAN_LENGTH = 16;
	private static final int MAX_GENERATION_ATTEMPTS = 5;
	private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

	private final AccountRepository accountRepository;
	private final SecureRandom random = new SecureRandom();

	public AccountNumberGenerator(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	public String generate() {
		for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
			String bban = randomBban();
			String candidate = COUNTRY_CODE + computeCheckDigits(bban) + bban;
			if (accountRepository.findByAccountNumber(candidate).isEmpty()) {
				return candidate;
			}
		}
		throw new IllegalStateException(
				"Failed to generate a unique account number after " + MAX_GENERATION_ATTEMPTS + " attempts");
	}

	public boolean isValidChecksum(String accountNumber) {
		if (accountNumber == null || accountNumber.length() != COUNTRY_CODE.length() + 2 + BBAN_LENGTH) {
			return false;
		}
		String countryCode = accountNumber.substring(0, 2);
		String checkDigits = accountNumber.substring(2, 4);
		String bban = accountNumber.substring(4);
		BigInteger numeric = new BigInteger(toNumericString(bban + countryCode + checkDigits));
		return numeric.mod(NINETY_SEVEN).intValue() == 1;
	}

	private String computeCheckDigits(String bban) {
		BigInteger numeric = new BigInteger(toNumericString(bban + COUNTRY_CODE + "00"));
		int remainder = numeric.mod(NINETY_SEVEN).intValue();
		return "%02d".formatted(98 - remainder);
	}

	private String randomBban() {
		StringBuilder sb = new StringBuilder(BBAN_LENGTH);
		for (int i = 0; i < BBAN_LENGTH; i++) {
			sb.append(random.nextInt(10));
		}
		return sb.toString();
	}

	private static String toNumericString(String input) {
		StringBuilder sb = new StringBuilder();
		for (char c : input.toCharArray()) {
			if (Character.isLetter(c)) {
				sb.append(Character.getNumericValue(c));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
