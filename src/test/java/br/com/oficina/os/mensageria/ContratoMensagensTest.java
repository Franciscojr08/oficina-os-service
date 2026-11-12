package br.com.oficina.os.mensageria;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.oficina.os.mensageria.config.MensageriaProperties;
import br.com.oficina.os.mensageria.evento.EventoEntrada;
import br.com.oficina.os.mensageria.evento.EventoSaida;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

/**
 * Trava os nomes que Billing e Execução usam para falar com o OS Service (contrato da Fase 4, documento
 * "Divisão dos três microsserviços"). Renomear qualquer um quebra a integração sem erro em tempo de
 * compilação, então a mudança precisa passar por aqui e por {@code docs/mensageria.md}.
 */
class ContratoMensagensTest {

	@Test
	void comandosEEventosQueOOsPublicaTemOsNomesDoContrato() {
		assertThat(Arrays.stream(EventoSaida.values()).map(EventoSaida::getTipo))
				.containsExactlyInAnyOrder(
						"SolicitarOrcamento",
						"CobrarPagamento",
						"EnfileirarExecucao",
						"EstornarPagamento",
						"CancelarExecucao",
						"OrdemServicoStatusAlterado");
	}

	@Test
	void eventosQueOOsConsomeTemOsNomesDoContrato() {
		assertThat(Arrays.stream(EventoEntrada.values()).map(EventoEntrada::getTipo))
				.containsExactlyInAnyOrder("OrcamentoGerado", "PagamentoConfirmado", "PagamentoRecusado",
						"PagamentoEstornado", "ExecucaoAguardandoItens", "ExecucaoIniciada", "ServicoFinalizado",
						"ExecucaoFinalizada", "ExecucaoFalhou")
				.as("a aprovação é do cliente, pela API do OS: o Billing não publica esses eventos")
				.doesNotContain("OrcamentoAprovado", "OrcamentoReprovado", "OrcamentoFalhou", "ExecucaoRecusada");
	}

	@Test
	void filaDeEntradaEDlqTemOsNomesDoContrato() throws IOException {
		Properties configuracao = new Properties();
		try (InputStream entrada = getClass().getResourceAsStream("/application.properties")) {
			configuracao.load(entrada);
		}
		String fila = configuracao.getProperty("oficina.mensageria.fila-entrada");

		assertThat(fila).isEqualTo("oficina-os-eventos");
		MensageriaProperties propriedades = new MensageriaProperties(
				"os.eventos", "billing.eventos", "execucao.eventos", fila,
				new MensageriaProperties.Outbox(false, 1000, 50), new MensageriaProperties.Retencao(7, 30));
		assertThat(propriedades.filaDeadLetter()).isEqualTo("oficina-os-eventos-dlq");
		assertThat(propriedades.exchangeDeadLetter()).isEqualTo("oficina-os-eventos-dlx");
	}
}
