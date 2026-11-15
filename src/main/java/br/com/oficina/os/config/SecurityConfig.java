package br.com.oficina.os.config;

import br.com.oficina.os.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_ATENDENTE = "ATENDENTE";
    private static final String ROLE_CLIENTE = "CLIENTE";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(csrf -> csrf.disable()
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api-docs/**",
                                "/public/**",
                                "/actuator/health",
                                "/actuator/health/**",
                                // O despacho de erro não passa pelo filtro JWT; sem liberar /error, todo
                                // erro (404 de rota inexistente, 500) chegaria ao cliente como 403.
                                "/error"
                        ).permitAll()
                        .requestMatchers("/usuarios/**").hasRole(ROLE_ADMIN)
                        // Únicas rotas abertas ao token de cliente (function de autenticação por CPF).
                        // A posse da OS é conferida no OrdemServicoService.
                        .requestMatchers(HttpMethod.GET,
                                "/ordens/{id}",
                                "/ordens/{id}/status",
                                "/ordens/{id}/historico",
                                "/ordens/cliente/{clienteId}"
                        ).hasAnyRole(ROLE_ADMIN, ROLE_ATENDENTE, ROLE_CLIENTE)
                        .requestMatchers(HttpMethod.PATCH,
                                "/ordens/{id}/cancelar",
                                "/ordens/{id}/aprovar",
                                "/ordens/{id}/reprovar")
                        .hasAnyRole(ROLE_ADMIN, ROLE_ATENDENTE, ROLE_CLIENTE)
                        // Cadastros e ações da oficina: só operadores.
                        .anyRequest().hasAnyRole(ROLE_ADMIN, ROLE_ATENDENTE)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) {
        return configuration.getAuthenticationManager();
    }
}
