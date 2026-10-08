package com.aquora.pagosexternos.producer;

import com.aquora.pagosexternos.dto.PagoMessage;
import com.aquora.pagosexternos.model.Pago;
import com.aquora.pagosexternos.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Este es el "producer" del diagrama: consulta pagos pendientes por medio del flag (estado_envio)
// y los publica en la cola.
@Slf4j
@Component
@RequiredArgsConstructor
public class PagoProducer {

    private final PagoRepository pagoRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.routing-key}")
    private String routingKey;

    // Corre cada 30 segundos. Para probarlo de inmediato sin esperar, usa el endpoint
    // POST /api/pagos-externos/procesar del PagoController.
    @Scheduled(fixedDelay = 30000)
    public void revisarPagosPendientes() {
        List<Pago> pendientes = pagoRepository.findByEstadoEnvio("pendiente");

        if (pendientes.isEmpty()) {
            log.info("No hay pagos pendientes de enviar.");
            return;
        }

        log.info("Encontrados {} pago(s) pendientes de enviar.", pendientes.size());

        for (Pago pago : pendientes) {
            // Update atomico: si otro proceso ya lo tomo, marcarEnCola devuelve 0 y lo saltamos.
            int filasActualizadas = pagoRepository.marcarEnCola(pago.getId());
            if (filasActualizadas == 0) {
                log.warn("Pago {} ya fue tomado por otro proceso, se omite.", pago.getId());
                continue;
            }

            PagoMessage mensaje = new PagoMessage(pago.getId(), pago.getMonto(), pago.getMetodo());
            rabbitTemplate.convertAndSend(exchangeName, routingKey, mensaje);
            log.info("Pago {} publicado en la cola.", pago.getId());
        }
    }
}
