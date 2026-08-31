package com.example.todoapp.dto.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** 6자 이상이면서 UTF-8 인코딩 시 72바이트 이하인지 검증한다 (BCrypt 입력 한계, CLAUDE.md 4장). */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordByteLengthValidator.class)
public @interface ValidPassword {

    String message() default "비밀번호는 6자 이상, UTF-8 기준 72바이트 이하여야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
