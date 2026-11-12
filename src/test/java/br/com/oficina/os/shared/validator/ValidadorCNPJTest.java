package br.com.oficina.os.shared.validator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ValidadorCNPJTest {

    @Test
    void deveValidarCnpjComDigitosCorretos() {
        assertTrue(ValidadorCNPJ.isValido("11222333000181"));
    }

    @Test
    void naoDeveValidarCnpjComDigitosRepetidos() {
        assertFalse(ValidadorCNPJ.isValido("00000000000000"));
    }

    @Test
    void naoDeveValidarCnpjComDigitosVerificadoresInvalidos() {
        assertFalse(ValidadorCNPJ.isValido("11222333000180"));
    }
}
