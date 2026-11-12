package br.com.oficina.os.mensageria.evento;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Formato comum de todas as mensagens entre os serviços (corpo JSON).
 *
 * @param messageId id único da mensagem. Opcional: o OS Service sempre manda, mas Billing e Execução
 *        podem não mandar; sem ele, a deduplicação usa {@code sagaId + tipo + dados}
 * @param tipo nome do comando ou evento (também é a routing key)
 * @param sagaId id da Saga, nasce no OS Service quando a OS entra na Saga e acompanha todas as
 *        mensagens daquele ciclo. Obrigatório ao receber; só é nulo em eventos informativos publicados
 *        fora de uma Saga
 * @param ordemServicoId OS a que a mensagem se refere
 * @param codigoOrdemServico código legível da OS (OS-AAAA-NNNNNN)
 * @param correlationId id que acompanha a requisição original por todos os serviços
 * @param ocorridoEm momento em que o fato aconteceu
 * @param versao versão do contrato do envelope (ausente é tratada como a versão 1)
 * @param dados conteúdo específico do tipo
 */
public record EnvelopeMensagem(
		UUID messageId,
		String tipo,
		UUID sagaId,
		Long ordemServicoId,
		String codigoOrdemServico,
		String correlationId,
		Instant ocorridoEm,
		Integer versao,
		JsonNode dados
) {

	public static final int VERSAO_ATUAL = 1;
}
