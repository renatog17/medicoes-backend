package com.rohub.medicoes.medicoes_api.validation;

import com.rohub.medicoes.medicoes_api.dto.ClienteRequest;
import com.rohub.medicoes.medicoes_api.dto.EnderecoRequest;
import com.rohub.medicoes.medicoes_api.domain.Cliente;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CinValidatorTest {
    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "11144477735", "12345678909"})
    void aceitaCpfValido(String cin) {
        assertTrue(new CinValidator().isValid(cin, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", " 52998224725", "52998224725 ", "abcdefghijk",
        "５２９９８２２４７２５", "5299822472", "529982247255", "52998224715", "52998224724",
        "00000000000", "11111111111", "22222222222", "33333333333", "44444444444",
        "55555555555", "66666666666", "77777777777", "88888888888", "99999999999"})
    void rejeitaCpfInvalido(String cin) {
        assertFalse(new CinValidator().isValid(cin, null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "123", "529.982.247-25"})
    void dtoEEntidadeExigemCinValido(String cin) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new ClienteRequest("Ana", cin, null, null,
                new EnderecoRequest(null, null, null, null, null, null, null));
            assertTrue(validator.validate(request).stream().anyMatch(v -> v.getPropertyPath().toString().equals("cin")));
            var cliente = new Cliente("Ana");
            cliente.setCin(cin);
            assertFalse(validator.validateProperty(cliente, "cin").isEmpty());
        }
    }

    @Test
    void dtoAceitaCinValido() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new ClienteRequest("Ana", "52998224725", null, null,
                new EnderecoRequest(null, null, null, null, null, null, null));
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
    }
}
