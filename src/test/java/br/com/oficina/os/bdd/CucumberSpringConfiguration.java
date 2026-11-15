package br.com.oficina.os.bdd;

import br.com.oficina.os.config.IntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;

/**
 * Contexto do Spring dos cenários BDD: a aplicação inteira com PostgreSQL e RabbitMQ reais, a mesma
 * configuração dos testes de integração (o contexto é reaproveitado entre eles).
 */
@CucumberContextConfiguration
@IntegrationTest
public class CucumberSpringConfiguration {
}
