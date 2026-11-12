package br.com.oficina.os.saga.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SagaProperties.class)
public class SagaConfig {
}
