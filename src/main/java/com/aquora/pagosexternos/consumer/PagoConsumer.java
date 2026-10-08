package com.aquora.pagosexternos.consumer;

import com.aquora.pagosexternos.banco.BancoSimulado;
import com.aquora.pagosexternos.dto.PagoMessage;
import com.aquora.pagosexternos.model.Pago;
import com.aquora.pagosexternos.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

// Este es el "consumer" del diagrama: toma el mensaje de la cola y llama a los 3 sistemas
// externos (bancos). Al final actualiza el flag en la tabla pagos (lo que en el diagrama
// hace la "Lambda Function": marcar el pago como enviado o fallido).
@Slf4j
@Component
@RequiredArgsConstructor
public class PagoConsumer {

    private static final int MAX_INTENTOS = 3;

    private final PagoRepository pagoRepository;
    private final BancoSimulado bancoSimulado;

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void procesarPago(PagoMessage mensaje) {
        Optional<Pago> pagoOpt = pagoRepository.findById(mensaje.getPagoId());

        if (pagoOpt.isEmpty()) {
            log.warn("Pago {} ya no existe en la base de datos, se descarta el mensaje.", mensaje.getPagoId());
            return;
        }

        Pago pago = pagoOpt.get();

        // Idempotencia: RabbitMQ puede entregar el mismo mensaje mas de una vez
        // (por ejemplo si el consumer se cae justo despues de procesar, antes de confirmar).
        // Si el pago ya quedo en "enviado", no se vuelve a procesar.
        if ("enviado".equals(pago.getEstadoEnvio())) {
            log.info("Pago {} ya estaba marcado como enviado, se ignora mensaje duplicado.", pago.getId());
            return;
        }

        boolean exito = bancoSimulado.enviarABancoA(pago.getId(), pago.getMonto())
                && bancoSimulado.enviarABancoB(pago.getId(), pago.getMonto())
                && bancoSimulado.enviarABancoC(pago.getId(), pago.getMonto());

        int intentos = (pago.getIntentosEnvio() == null ? 0 : pago.getIntentosEnvio()) + 1;
        pago.setIntentosEnvio(intentos);

        if (exito) {
            pago.setEstadoEnvio("enviado");
            pago.setFechaEnvio(LocalDateTime.now());
            log.info("Pago {} enviado con exito a los 3 sistemas externos (intento {}).", pago.getId(), intentos);
        } else if (intentos >= MAX_INTENTOS) {
            pago.setEstadoEnvio("fallido");
            log.error("Pago {} fallo despues de {} intentos. Se marca como fallido.", pago.getId(), intentos);
        } else {
            // Lo regresamos a "pendiente" para que el Producer lo vuelva a tomar en el siguiente ciclo.
            pago.setEstadoEnvio("pendiente");
            log.warn("Pago {} fallo en el intento {}. Se reintentara.", pago.getId(), intentos);
        }

        pagoRepository.save(pago);
    }
}
