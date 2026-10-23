package com.rohub.medicoes.medicoes_api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CinValidator implements ConstraintValidator<CinValido, String> {
    @Override
    public boolean isValid(String cin, ConstraintValidatorContext context) {
        // A obrigatoriedade é tratada por @NotBlank.
        if (cin == null || cin.isEmpty()) return true;
        if (!cin.matches("[0-9]{11}")) return false;
        if (cin.chars().allMatch(digito -> digito == cin.charAt(0))) return false;
        return digitoVerificador(cin, 9) == cin.charAt(9) - '0'
            && digitoVerificador(cin, 10) == cin.charAt(10) - '0';
    }

    private int digitoVerificador(String cin, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (cin.charAt(i) - '0') * (tamanho + 1 - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
