package br.com.oficina.os.ordemservico.application.evento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.oficina.os.cliente.domain.Cliente;
import br.com.oficina.os.ordemservico.domain.OrdemServico;
import br.com.oficina.os.ordemservico.itens.domain.ItemOrdemServico;
import br.com.oficina.os.ordemservico.servicos.domain.ServicoOrdemServico;
import br.com.oficina.os.veiculo.domain.Veiculo;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

class SolicitarOrcamentoDadosTest {

	@Test
	void deveLevarSoIdsEQuantidadesComoNoContratoDaFase4() {
		Cliente cliente = new Cliente();
		cliente.setId(1L);
		cliente.setNome("Ana");
		cliente.setCpfCnpj("52998224725");
		cliente.setEmail("ana@example.com");
		Veiculo veiculo = new Veiculo();
		veiculo.setId(10L);
		veiculo.setPlaca("BRA2E19");
		OrdemServico os = new OrdemServico();
		os.setId(100L);
		os.setCodigo("OS-2026-000100");
		os.setCliente(cliente);
		os.setVeiculo(veiculo);

		ItemOrdemServico itemOs = new ItemOrdemServico();
		itemOs.setItemId(20L);
		itemOs.setItemNome("Filtro");
		itemOs.setQuantidade(2);
		ServicoOrdemServico servicoOs = new ServicoOrdemServico();
		servicoOs.setServicoId(30L);
		servicoOs.setServicoNome("Troca de óleo");

		SolicitarOrcamentoDados dados = SolicitarOrcamentoDados.de(os, List.of(itemOs), List.of(servicoOs));

		assertThat(dados.clienteId()).isEqualTo(1L);
		assertThat(dados.veiculoId()).isEqualTo(10L);
		assertThat(dados.itens()).singleElement().satisfies(i -> {
			assertThat(i.itemId()).isEqualTo(20L);
			assertThat(i.quantidade()).isEqualTo(2);
		});
		assertThat(dados.servicos()).singleElement().satisfies(sv -> assertThat(sv.servicoId()).isEqualTo(30L));
	}

	@Test
	void naoLevaPrecoNemDadosPessoaisDoCliente() {
		Cliente cliente = new Cliente();
		cliente.setId(1L);
		cliente.setNome("Ana");
		cliente.setCpfCnpj("52998224725");
		cliente.setEmail("ana@example.com");
		Veiculo veiculo = new Veiculo();
		veiculo.setId(10L);
		OrdemServico os = new OrdemServico();
		os.setCliente(cliente);
		os.setVeiculo(veiculo);

		JsonNode json = JsonMapper.builder().build().valueToTree(SolicitarOrcamentoDados.de(os, List.of(), List.of()));

		assertThat(json.propertyNames()).containsExactlyInAnyOrder("clienteId", "veiculoId", "servicos", "itens");
		assertThat(json.toString()).doesNotContain("valor", "Ana", "52998224725", "ana@example.com");
	}
}
