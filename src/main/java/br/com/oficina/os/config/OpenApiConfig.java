package br.com.oficina.os.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addServersItem(new Server()
                        .url("/oficina/v1")
                        .description("Contexto da API v1"))
                .info(new Info()
                        .title("Oficina - OS Service")
                        .version("v1")
                        .description("""
                                Microsserviço de Ordem de Serviço da oficina (Tech Challenge Fase 4).

                                É dono do ciclo de vida da OS e orquestra a Saga: abertura, diagnóstico, pedido de
                                orçamento, aprovação ou reprovação pelo cliente, cancelamento, entrega, consulta de
                                status e histórico. Orçamento, pagamento e estorno ficam no Billing, e estoque e
                                execução no serviço de Execução: essas etapas chegam ao OS Service por eventos da
                                Saga, não por esta API. O catálogo de itens e serviços também é do Billing.

                                Rotas administrativas exigem token JWT no header Authorization: Bearer <token>
                                (obtido em POST /auth/login). O grupo api-publica não exige token.
                                Erros respondem em application/problem+json.
                                """)
                        .contact(new Contact()
                                .name("Equipe Oficina - OS Service")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .name(BEARER_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
