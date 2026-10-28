package com.aquora.pagosexternos.bank;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El contrato REAL del banco (entregado por el docente el 8/10) modela
 * "x-api-key" como un securityScheme (ApiKeyAuth), NO como un header
 * explicito de cada operacion. Por eso openapi-generator (interfaceOnly=true)
 * ya no genera un parametro "apiKey" en BankApi#sendBankPayment/getBankPayment:
 * hay que inyectar el header nosotros mismos con un RequestInterceptor, en
 * vez de pasarlo como argumento del metodo (como se hacia contra el borrador
 * reconstruido que se uso antes de tener el contrato oficial).
 */
@Configuration
public class BankFeignConfig {

    @Value("${app.bank.api-key}")
    private String apiKey;

    @Bean
    public RequestInterceptor bankApiKeyInterceptor() {
        return requestTemplate -> requestTemplate.header("x-api-key", apiKey);
    }
}
