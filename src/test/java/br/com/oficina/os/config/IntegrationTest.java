package br.com.oficina.os.config;

import br.com.oficina.os.OficinaOsServiceApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Teste de integração com a aplicação inteira, PostgreSQL e RabbitMQ reais (Testcontainers).
 * Todas as classes anotadas compartilham o mesmo contexto do Spring e os mesmos containers (classes
 * que mudam o contexto, com {@code @MockitoBean} ou configuração própria, ganham contexto e
 * containers próprios).
 */
@SpringBootTest(
		classes = OficinaOsServiceApplication.class,
		// Os testes chamam o publicador do outbox e o verificador de prazos da Saga sob demanda, em
		// vez de esperar os agendadores.
		properties = {
			"oficina.mensageria.outbox.agendamento-habilitado=false",
			"oficina.saga.verificacao.agendamento-habilitado=false"
		}
)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IntegrationTest {
}
