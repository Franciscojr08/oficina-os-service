package br.com.oficina.os.saga.evento;

/** Compensação para a Execução: devolver o estoque reservado e tirar a OS da fila. Formato do contrato: só o motivo. */
public record CancelarExecucaoDados(String motivo) {

	public static CancelarExecucaoDados de(String motivo) {
		return new CancelarExecucaoDados(motivo);
	}
}
