package br.com.oficina.os.mensageria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Garante que o broker fica atrás das portas: só o pacote {@code mensageria/rabbit} pode importar o
 * cliente do RabbitMQ ou o Spring AMQP. Trocar para SNS+SQS é escrever outro adaptador, sem tocar no
 * domínio.
 */
class FronteiraMensageriaTest {

	private static final Path FONTES = Path.of("src/main/java");
	private static final String PACOTE_DO_ADAPTADOR = "br/com/oficina/os/mensageria/rabbit/";
	private static final List<String> IMPORTS_DE_BROKER = List.of(
			"import org.springframework.amqp.",
			"import org.springframework.boot.amqp.",
			"import com.rabbitmq.");

	@Test
	void soOAdaptadorDoRabbitImportaOClienteDoBroker() throws IOException {
		List<String> violacoes;
		try (Stream<Path> arquivos = Files.walk(FONTES)) {
			violacoes = arquivos
					.filter(caminho -> caminho.toString().endsWith(".java"))
					.filter(caminho -> !caminho.toString().replace('\\', '/').contains(PACOTE_DO_ADAPTADOR))
					.filter(FronteiraMensageriaTest::importaBroker)
					.map(caminho -> FONTES.relativize(caminho).toString())
					.toList();
		}

		assertThat(violacoes)
				.as("arquivos fora de mensageria/rabbit que importam o broker")
				.isEmpty();
	}

	@Test
	void oAdaptadorDoRabbitExisteEOGuardaVerificaAlgumaCoisa() throws IOException {
		try (Stream<Path> arquivos = Files.walk(FONTES.resolve(PACOTE_DO_ADAPTADOR))) {
			assertThat(arquivos
					.filter(caminho -> caminho.toString().endsWith(".java"))
					.filter(FronteiraMensageriaTest::importaBroker)
					.count()).isPositive();
		}
	}

	private static boolean importaBroker(Path arquivo) {
		try {
			return Files.readAllLines(arquivo).stream()
					.anyMatch(linha -> IMPORTS_DE_BROKER.stream().anyMatch(linha::startsWith));
		} catch (IOException e) {
			throw new IllegalStateException("Não foi possível ler " + arquivo, e);
		}
	}
}
