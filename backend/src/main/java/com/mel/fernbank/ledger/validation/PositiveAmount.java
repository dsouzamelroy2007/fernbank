package com.mel.fernbank.ledger.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PositiveAmountValidator.class)
public @interface PositiveAmount {

	String message() default "amount must be a positive value with no more precision than the currency allows";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
