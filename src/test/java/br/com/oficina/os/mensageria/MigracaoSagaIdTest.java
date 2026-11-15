package br.com.oficina.os.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

/**
 * A V14 tira os valores da OS e a V13 traduz os status removidos da OS e do histórico. A V12 mexe em tabelas com dados (backfill do sagaId e troca da chave primária do inbox). Este teste
 * sobe o banco na V11, grava dados como a versão anterior gravaria, migra para a última versão e confere
 * que nada se perdeu e que o inbox continua descartando as repetições que já descartava.
 */
class MigracaoSagaIdTest {

	private static PostgreSQLContainer postgres;
	private static JdbcTemplate jdbc;

	@BeforeAll
	static void subirBancoNaV11ComDados() {
		postgres = new PostgreSQLContainer("postgres:15-alpine");
		postgres.start();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);

		flyway(dataSource, "11").migrate();

		jdbc.update("""
				INSERT INTO cliente (nome, cpf_cnpj, cep, logradouro, bairro, cidade, uf, data_nascimento)
				VALUES ('Ana', '52998224725', '01001000', 'Rua A', 'Centro', 'São Paulo', 'SP', '1990-01-01')""");
		jdbc.update("INSERT INTO veiculo (cliente_id, placa, marca, modelo, ano) VALUES (1, 'BRA2E19', 'VW', 'Gol', 2020)");
		for (int i = 0; i < 2; i++) {
			jdbc.update("""
					INSERT INTO ordem_servico (descricao_servicos_executados, status, valor_total_servicos,
					                           valor_total_itens, cliente_id, veiculo_id)
					VALUES ('', 'AGUARDANDO_ORCAMENTO', 0, 0, 1, 1)""");
		}
		for (String antigo : List.of("AGUARDANDO_ORCAMENTO", "AGUARDANDO_PAGAMENTO", "AGUARDANDO_EXECUCAO")) {
			jdbc.update("""
					INSERT INTO ordem_servico (descricao_servicos_executados, status, valor_total_servicos,
					                           valor_total_itens, cliente_id, veiculo_id)
					VALUES (?, ?, 0, 0, 1, 1)""", "status-" + antigo, antigo);
			jdbc.update("""
					INSERT INTO historico_ordem_servico (status, origem, ordem_servico_id)
					SELECT ?, 'API', id FROM ordem_servico WHERE descricao_servicos_executados = ?""",
					antigo, "status-" + antigo);
		}
		jdbc.update("INSERT INTO saga_ordem_servico (ordem_servico_id, etapa, situacao) VALUES (1, 'AGUARDANDO_ORCAMENTO', 'EM_ANDAMENTO')");
		jdbc.update("INSERT INTO saga_ordem_servico (ordem_servico_id, etapa, situacao) VALUES (2, 'EM_EXECUCAO', 'CONCLUIDA')");
		jdbc.update("INSERT INTO evento_processado (message_id, tipo, ordem_servico_id) VALUES (?, 'OrcamentoGerado', 1)",
				UUID.fromString("7b1f2c1e-3d4a-4f7e-9c2b-0a1b2c3d4e5f"));
		jdbc.update("""
				INSERT INTO outbox_evento (message_id, tipo, exchange, routing_key, ordem_servico_id, correlation_id, payload)
				VALUES (?, 'OrcamentoSolicitado', 'os.eventos', 'OrcamentoSolicitado', 1, 'corr', '{}')""", UUID.randomUUID());

		flyway(dataSource, "latest").migrate();
	}

	@AfterAll
	static void derrubarBanco() {
		postgres.stop();
	}

	@Test
	void statusRemovidosViramOsDoContratoNaOsENoHistorico() {
		assertThat(statusDaOs("AGUARDANDO_ORCAMENTO")).isEqualTo("EM_DIAGNOSTICO");
		assertThat(statusDaOs("AGUARDANDO_PAGAMENTO")).isEqualTo("AGUARDANDO_APROVACAO");
		assertThat(statusDaOs("AGUARDANDO_EXECUCAO")).isEqualTo("APROVADA");
		assertThat(jdbc.queryForList("""
				SELECT h.status FROM historico_ordem_servico h
				JOIN ordem_servico o ON o.id = h.ordem_servico_id
				WHERE o.descricao_servicos_executados LIKE 'status-%' ORDER BY h.id""", String.class))
				.containsExactly("EM_DIAGNOSTICO", "AGUARDANDO_APROVACAO", "APROVADA");
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM ordem_servico
				WHERE status IN ('AGUARDANDO_ORCAMENTO', 'AGUARDANDO_PAGAMENTO', 'AGUARDANDO_EXECUCAO')""", Integer.class))
				.isZero();
	}

	private static String statusDaOs(String statusAntigo) {
		return jdbc.queryForObject("SELECT status FROM ordem_servico WHERE descricao_servicos_executados = ?",
				String.class, "status-" + statusAntigo);
	}

	@Test
	void aOsDeixaDeGuardarValor() {
		assertThat(jdbc.queryForList("""
				SELECT table_name || '.' || column_name FROM information_schema.columns
				WHERE table_name IN ('ordem_servico', 'item_ordem_servico', 'servico_ordem_servico')
				  AND column_name LIKE 'valor%'""", String.class)).isEmpty();
	}

	@Test
	void sagasExistentesGanhamUmSagaIdProprio() {
		List<UUID> ids = jdbc.queryForList("SELECT saga_id FROM saga_ordem_servico ORDER BY id", UUID.class);

		assertThat(ids).hasSize(2).doesNotContainNull().doesNotHaveDuplicates();
	}

	@Test
	void sagaIdEObrigatorioEUnico() {
		assertThat(jdbc.queryForObject("""
				SELECT is_nullable FROM information_schema.columns
				WHERE table_name = 'saga_ordem_servico' AND column_name = 'saga_id'""", String.class)).isEqualTo("NO");
		UUID existente = jdbc.queryForObject("SELECT saga_id FROM saga_ordem_servico WHERE ordem_servico_id = 1", UUID.class);
		jdbc.update("""
				INSERT INTO ordem_servico (descricao_servicos_executados, status, cliente_id, veiculo_id)
				VALUES ('', 'RECEBIDA', 1, 1)""");
		Long novaOs = jdbc.queryForObject("SELECT max(id) FROM ordem_servico", Long.class);

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO saga_ordem_servico (ordem_servico_id, saga_id, etapa, situacao)
				VALUES (?, ?, 'AGUARDANDO_ORCAMENTO', 'EM_ANDAMENTO')""", novaOs, existente))
				.isInstanceOf(DuplicateKeyException.class);
		jdbc.update("DELETE FROM ordem_servico WHERE id = ?", novaOs);
	}

	@Test
	void mensagensJaProcessadasContinuamReconhecidasPeloMessageId() {
		assertThat(jdbc.queryForObject("""
				SELECT chave_deduplicacao FROM evento_processado
				WHERE message_id = '7b1f2c1e-3d4a-4f7e-9c2b-0a1b2c3d4e5f'""", String.class))
				.isEqualTo("7b1f2c1e-3d4a-4f7e-9c2b-0a1b2c3d4e5f");

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO evento_processado (chave_deduplicacao, message_id, tipo, ordem_servico_id)
				VALUES ('7b1f2c1e-3d4a-4f7e-9c2b-0a1b2c3d4e5f', '7b1f2c1e-3d4a-4f7e-9c2b-0a1b2c3d4e5f', 'OrcamentoGerado', 1)"""))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void inboxAceitaMensagemSemMessageIdEDeduplicaPelaChave() {
		String chave = UUID.randomUUID() + ":" + "a".repeat(64);
		String insercao = """
				INSERT INTO evento_processado (chave_deduplicacao, message_id, saga_id, tipo, ordem_servico_id)
				VALUES (?, NULL, ?, 'ServicoFinalizado', 1)
				ON CONFLICT (chave_deduplicacao) DO NOTHING""";

		int primeira = jdbc.update(insercao, chave, UUID.randomUUID());
		int repetida = jdbc.update(insercao, chave, UUID.randomUUID());

		assertThat(primeira).isEqualTo(1);
		assertThat(repetida).isZero();
	}

	@Test
	void outboxGanhaSagaIdOpcionalSemPerderOsEventosAntigos() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE saga_id IS NULL", Integer.class))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				SELECT is_nullable FROM information_schema.columns
				WHERE table_name = 'outbox_evento' AND column_name = 'saga_id'""", String.class)).isEqualTo("YES");
	}

	private static Flyway flyway(DriverManagerDataSource dataSource, String alvo) {
		return Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.target(alvo)
				.load();
	}
}
