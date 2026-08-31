package com.example.todoapp.dto.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * BCrypt는 72바이트를 초과하는 입력을 조용히 잘라버리므로, 그 앞단에서 명시적으로 거부해야 한다.
 * 문자 수가 아니라 UTF-8 바이트 수로 검사한다 — 한글 1자는 3바이트라 문자 수 제한만으로는
 * 25자(=75바이트)에서 서버가 500을 내는 사고로 이어진다.
 */
public class PasswordByteLengthValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_LENGTH = 6;
    private static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        if (value.length() < MIN_LENGTH) {
            return false;
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
