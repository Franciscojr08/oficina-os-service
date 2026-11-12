package br.com.oficina.os.mensageria.consumo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.oficina.os.mensageria.evento.EnvelopeMensagem;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

class ChaveDeduplicacaoTest {

	private static final UUID SAGA = UUID.randomUUID();
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void comMessageIdAChaveEOProprioId() {
		UUID messageId = UUID.randomUUID();

		assertThat(ChaveDeduplicacao.de(envelope(messageId, SAGA, "ServicoFinalizado", "{\"servicoId\":1}")))
				.isEqualTo(messageId.toString());
	}

	@Test
	void semMessageIdAChaveEhSagaMaisHashDeTipoEDados() {
		String chave = ChaveDeduplicacao.de(envelope(null, SAGA, "ServicoFinalizado", "{\"servicoId\":1}"));

		assertThat(chave).matches(SAGA + ":[0-9a-f]{64}");
		assertThat(chave.length()).isLessThanOrEqualTo(200);
	}

	@Test
	void amesmaMensagemEntregueDuasVezesTemAMesmaChave() {
		assertThat(ChaveDeduplicacao.de(envelope(null, SAGA, "ServicoFinalizado", "{\"servicoId\":1}")))
				.isEqualTo(ChaveDeduplicacao.de(envelope(null, SAGA, "ServicoFinalizado", "{\"servicoId\":1}")));
	}

	@Test
	void doisServicosFinalizadosNaMesmaSagaTemChavesDiferentes() {
		assertThat(ChaveDeduplicacao.de(envelope(null, SAGA, "ServicoFinalizado", "{\"servicoId\":1}")))
				.isNotEqualTo(ChaveDeduplicacao.de(envelope(null, SAGA, "ServicoFinalizado", "{\"servicoId\":2}")));
	}

	@Test
	void tiposDiferentesComOsMesmosDadosTemChavesDiferentes() {
		assertThat(ChaveDeduplicacao.de(envelope(null, SAGA, "ExecucaoIniciada", "{}")))
				.isNotEqualTo(ChaveDeduplicacao.de(envelope(null, SAGA, "ExecucaoFinalizada", "{}")));
	}

	@Test
	void sagasDiferentesTemChavesDiferentes() {
		assertThat(ChaveDeduplicacao.de(envelope(null, SAGA, "ExecucaoIniciada", "{}")))
				.isNotEqualTo(ChaveDeduplicacao.de(envelope(null, UUID.randomUUID(), "ExecucaoIniciada", "{}")));
	}

	@Test
	void semDadosAindaGeraChave() {
		EnvelopeMensagem semDados = new EnvelopeMensagem(null, "ExecucaoIniciada", SAGA, 1L, "OS-2026-000001", "c",
				Instant.now(), 1, null);

		assertThat(ChaveDeduplicacao.de(semDados)).matches(SAGA + ":[0-9a-f]{64}");
	}

	@Test
	void tipoLongoNaoEstouraOTamanhoDaColuna() {
		String chave = ChaveDeduplicacao.de(envelope(null, SAGA, "T".repeat(500), "{}"));

		assertThat(chave.length()).isLessThanOrEqualTo(200);
	}

	private EnvelopeMensagem envelope(UUID messageId, UUID sagaId, String tipo, String dados) {
		JsonNode no = jsonMapper.readTree(dados);
		return new EnvelopeMensagem(messageId, tipo, sagaId, 1L, "OS-2026-000001", "c", Instant.now(), 1, no);
	}
}
