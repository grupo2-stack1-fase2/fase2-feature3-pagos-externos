package com.aquora.pagosexternos.erp;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Igual que BankFeignConfig: el contrato del ERP tambien modela "x-api-key"
 * como securityScheme, no como parametro de las operaciones, asi que se
 * inyecta aqui con un RequestInterceptor propio (llave distinta a la del banco).
 */
@Configuration
public class ErpFeignConfig {

    @Value("${app.erp.api-key}")
    private String apiKey;

    @Bean
    public RequestInterceptor erpApiKeyInterceptor() {
        return requestTemplate -> requestTemplate.header("x-api-key", apiKey);
    }
}
