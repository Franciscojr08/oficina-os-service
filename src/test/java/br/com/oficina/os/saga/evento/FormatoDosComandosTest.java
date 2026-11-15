package br.com.oficina.os.saga.evento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.ordemservico.itens.domain.ItemOrdemServico;
import br.com.oficina.os.ordemservico.servicos.domain.ServicoOrdemServico;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Trava o {@code dados} dos comandos no formato do PDF da Fase 4 ("O que vai em dados, por tipo"): sem
 * campos extras. Billing e Execução leem exatamente isto.
 */
class FormatoDosComandosTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void cobrarPagamentoLevaSoOOrcamentoId() {
		OrdemServico os = os();
		os.setOrcamentoId(77L);

		JsonNode json = jsonMapper.valueToTree(CobrarPagamentoDados.de(os));

		assertThat(json.propertyNames()).containsExactly("orcamentoId");
		assertThat(json.get("orcamentoId").asLong()).isEqualTo(77L);
	}

	@Test
	void estornarPagamentoLevaPagamentoIdEMotivo() {
		OrdemServico os = os();
		os.setPagamentoId(900L);
		os.setOrcamentoId(77L);

		JsonNode json = jsonMapper.valueToTree(EstornarPagamentoDados.de(os, "Sem estoque"));

		assertThat(json.propertyNames()).containsExactlyInAnyOrder("pagamentoId", "motivo");
		assertThat(json.get("pagamentoId").asLong()).isEqualTo(900L);
		assertThat(json.get("motivo").asString()).isEqualTo("Sem estoque");
	}

	@Test
	void cancelarExecucaoLevaSoOMotivo() {
		JsonNode json = jsonMapper.valueToTree(CancelarExecucaoDados.de("Cancelamento do cliente"));

		assertThat(json.propertyNames()).containsExactly("motivo");
	}

	@Test
	void enfileirarExecucaoLevaServicoComNomeEItemSoComIdEQuantidade() {
		ItemOrdemServico item = new ItemOrdemServico();
		item.setItemId(55L);
		item.setItemNome("Pastilha de freio");
		item.setQuantidade(4);
		ServicoOrdemServico servico = new ServicoOrdemServico();
		servico.setServicoId(7L);
		servico.setServicoNome("Troca de pastilhas de freio");

		JsonNode json = jsonMapper.valueToTree(EnfileirarExecucaoDados.de(List.of(item), List.of(servico)));

		assertThat(json.propertyNames()).containsExactlyInAnyOrder("servicos", "itens");
		JsonNode servicoJson = json.get("servicos").get(0);
		assertThat(servicoJson.propertyNames()).containsExactlyInAnyOrder("servicoId", "nome");
		assertThat(servicoJson.get("nome").asString()).isEqualTo("Troca de pastilhas de freio");
		JsonNode itemJson = json.get("itens").get(0);
		assertThat(itemJson.propertyNames()).containsExactlyInAnyOrder("itemId", "quantidade");
		assertThat(itemJson.get("quantidade").asInt()).isEqualTo(4);
	}

	private static OrdemServico os() {
		OrdemServico os = new OrdemServico();
		os.setId(1042L);
		os.setCodigo("OS-2026-000123");
		return os;
	}
}
