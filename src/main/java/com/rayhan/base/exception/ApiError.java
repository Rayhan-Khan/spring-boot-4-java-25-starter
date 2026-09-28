package com.rayhan.base.exception;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiError {

    public static Map<String, String> fieldError(BindingResult bindingResult) {
        Map<String, String> fieldErrors = new HashMap<>();
        if (bindingResult.hasErrors()) {
            List<FieldError> errors = bindingResult.getFieldErrors();
            errors.forEach(e -> {
                fieldErrors.put(e.getField(), e.getDefaultMessage());
            });
        }
        return fieldErrors;
    }
}


