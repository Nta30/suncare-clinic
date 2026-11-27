package com.suncare.clinic.modules.prescription.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PositiveDoseValidator.class)
public @interface PositiveDose {
    String message() default "Liều mỗi lần phải là số dương, ví dụ 1 hoặc 0,5.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
