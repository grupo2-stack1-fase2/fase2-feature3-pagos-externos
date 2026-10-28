package com.aquora.pagosexternos.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

// Aqui se define "la cola" del diagrama: el exchange, la queue y el binding entre ambos.
// El Producer publica al exchange, el Consumer escucha la queue.
//
// Se agrega una Dead Letter Queue (DLQ): cuando el consumer rechaza un mensaje con
// AmqpRejectAndDontRequeueException (respuestas no reintentables: 400, 403,
// 409 IDEMPOTENCY_KEY_REUSED, 422), RabbitMQ lo manda aqui en vez de perderlo o
// reintentarlo para siempre. Esto es justo lo que pidio el profe (seccion 5.3 de
// la guia): "todo lo demas va directo a la DLQ, sin gastar intentos".
@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.queue}")
    private String queueName;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.routing-key}")
    private String routingKey;

    @Value("${app.rabbitmq.dlq}")
    private String dlqName;

    @Value("${app.rabbitmq.dlx}")
    private String dlxName;

    @Bean
    public Queue pagosQueue() {
        return new Queue(queueName, true, false, false, Map.of(
                "x-dead-letter-exchange", dlxName,
                "x-dead-letter-routing-key", dlqName
        ));
    }

    @Bean
    public DirectExchange pagosExchange() {
        return new DirectExchange(exchangeName);
    }

    @Bean
    public Binding binding(Queue pagosQueue, DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosQueue).to(pagosExchange).with(routingKey);
    }

    @Bean
    public DirectExchange pagosDlx() {
        return new DirectExchange(dlxName);
    }

    @Bean
    public Queue pagosDlq() {
        return new Queue(dlqName, true);
    }

    @Bean
    public Binding dlqBinding(Queue pagosDlq, DirectExchange pagosDlx) {
        return BindingBuilder.bind(pagosDlq).to(pagosDlx).with(dlqName);
    }

    // Convierte automaticamente los objetos Java (PagoMessage) a JSON y viceversa
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
