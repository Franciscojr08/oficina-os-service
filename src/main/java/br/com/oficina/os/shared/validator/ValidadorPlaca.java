package br.com.oficina.os.shared.validator;

public final class ValidadorPlaca {

	private  ValidadorPlaca() {}

	private static final String PADRAO_ANTIGO = "^[A-Z]{3}\\d{4}$";
	/** Mercosul: três letras, um número, uma letra e dois números (ex.: BRA2E19). */
	private static final String PADRAO_MERCOSUL = "^[A-Z]{3}\\d[A-Z]\\d{2}$";

	public static boolean isValida(String placa) {
		if (placa == null) {
			return false;
		}

		String valor = placa.toUpperCase().replaceAll("[^A-Z0-9]", "");

		return valor.matches(PADRAO_ANTIGO) || valor.matches(PADRAO_MERCOSUL);
	}
}
