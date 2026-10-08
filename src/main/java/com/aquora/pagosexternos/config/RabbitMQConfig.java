package com.aquora.pagosexternos.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Aqui se define "la cola" del diagrama: el exchange, la queue y el binding entre ambos.
// El Producer publica al exchange, el Consumer escucha la queue.
@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.queue}")
    private String queueName;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.routing-key}")
    private String routingKey;

    @Bean
    public Queue pagosQueue() {
        return new Queue(queueName, true); // durable=true -> sobrevive si RabbitMQ se reinicia
    }

    @Bean
    public DirectExchange pagosExchange() {
        return new DirectExchange(exchangeName);
    }

    @Bean
    public Binding binding(Queue pagosQueue, DirectExchange pagosExchange) {
        return BindingBuilder.bind(pagosQueue).to(pagosExchange).with(routingKey);
    }

    // Convierte automaticamente los objetos Java (PagoMessage) a JSON y viceversa
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
