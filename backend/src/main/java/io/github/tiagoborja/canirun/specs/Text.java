package io.github.tiagoborja.canirun.specs;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** A text value of a report: 200 characters maximum and no NUL character. Postgres jsonb rejects NUL. */
@Size(max = 200)
@Pattern(regexp = "[^\\x00]*")
@Constraint(validatedBy = {})
@Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE })
@Retention(RUNTIME)
@interface Text {

	String message() default "Text value is not valid.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
