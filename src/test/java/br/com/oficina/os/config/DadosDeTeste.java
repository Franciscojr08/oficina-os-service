package br.com.oficina.os.config;

import java.util.concurrent.ThreadLocalRandom;

/** Documentos e placas válidos e aleatórios, para os testes não colidirem nas restrições de unicidade. */
public final class DadosDeTeste {

	private static final String LETRAS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

	private DadosDeTeste() {
	}

	public static String cpf() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int[] digitos = new int[11];
		digitos[0] = 1 + random.nextInt(9);
		for (int i = 1; i < 9; i++) {
			digitos[i] = random.nextInt(10);
		}
		digitos[9] = digitoVerificador(digitos, 9);
		digitos[10] = digitoVerificador(digitos, 10);
		StringBuilder cpf = new StringBuilder();
		for (int digito : digitos) {
			cpf.append(digito);
		}
		return cpf.toString();
	}

	/** Placa no padrão Mercosul (AAA0A00). */
	public static String placa() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		return "" + letra(random) + letra(random) + letra(random) + random.nextInt(10) + letra(random)
				+ random.nextInt(10) + random.nextInt(10);
	}

	private static char letra(ThreadLocalRandom random) {
		return LETRAS.charAt(random.nextInt(LETRAS.length()));
	}

	private static int digitoVerificador(int[] digitos, int tamanho) {
		int soma = 0;
		for (int i = 0; i < tamanho; i++) {
			soma += digitos[i] * (tamanho + 1 - i);
		}
		int resto = (soma * 10) % 11;
		return resto == 10 ? 0 : resto;
	}
}
