package com.suncare.clinic.modules.prescription.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.util.regex.Pattern;

public class PositiveDoseValidator implements ConstraintValidator<PositiveDose, String> {
    private static final Pattern FORMAT = Pattern.compile("^[0-9]+(?:[.,][0-9]+)?$");

    public static boolean valid(String value) {
        if (value == null || value.isBlank()) return true; // @NotBlank handles empty input.
        String text = value.strip();
        return FORMAT.matcher(text).matches() && new BigDecimal(text.replace(',', '.')).signum() > 0;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return valid(value);
    }
}
