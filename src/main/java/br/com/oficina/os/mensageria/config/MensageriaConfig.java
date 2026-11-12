package br.com.oficina.os.mensageria.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuração da mensageria que não depende do broker: as propriedades {@code oficina.mensageria.*}
 * e o agendamento do publicador do outbox. Fica fora do pacote do adaptador para continuar valendo
 * se o broker for trocado.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(MensageriaProperties.class)
public class MensageriaConfig {
}
