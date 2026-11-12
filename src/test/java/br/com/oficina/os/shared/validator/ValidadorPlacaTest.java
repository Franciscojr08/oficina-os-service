package br.com.oficina.os.shared.validator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidadorPlacaTest {

	@ParameterizedTest
	@ValueSource(strings = {"ABC1234", "abc-1234", "BRA2E19", "bra2e19", "BRA-2E19"})
	void deveAceitarPlacasNoPadraoAntigoEMercosul(String placa) {
		assertThat(ValidadorPlaca.isValida(placa)).isTrue();
	}

	/**
	 * Regressão: o monólito exigia duas letras depois do primeiro número (AAA0AA00, 8 caracteres)
	 * e recusava toda placa Mercosul real.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"BRA2EE19", "AB12345", "ABCD123", "ABC12E3", "", "1234567"})
	@NullSource
	void deveRecusarPlacasForaDoPadrao(String placa) {
		assertThat(ValidadorPlaca.isValida(placa)).isFalse();
	}
}
