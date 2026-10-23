package com.aquora.pagosexternos.consumer;

import com.aquora.pagosexternos.bank.BankIntegrationService;
import com.aquora.pagosexternos.bank.BankPaymentMapper;
import com.aquora.pagosexternos.bank.BankSendOutcome;
import com.aquora.pagosexternos.bank.dto.BankPaymentRequestDto;
import com.aquora.pagosexternos.dto.PagoMessage;
import com.aquora.pagosexternos.model.EnvioDestino;
import com.aquora.pagosexternos.model.Pago;
import com.aquora.pagosexternos.repository.EnvioDestinoRepository;
import com.aquora.pagosexternos.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Este es el "consumer" del diagrama: toma el mensaje de la cola y llama a cada
 * destino externo (por ahora, solo el banco; el ERP queda como TODO cuando llegue
 * su contrato). Al final actualiza el flag en la tabla pagos (lo que en el diagrama
 * hace la "Lambda Function": marcar el pago como enviado o fallido).
 *
 * CAMBIO IMPORTANTE respecto a la version anterior (con BancoSimulado):
 * ahora se lleva un registro POR DESTINO en la tabla envios_destino. Si el banco ya
 * confirmo el pago (estado EXITOSO) y el ERP todavia no, un reintento SOLO vuelve a
 * llamar al ERP. Nunca se repite un destino que ya tuvo exito. Esto es exactamente
 * lo que exigio el profe en la ultima clase: "esta transaccion se tiene que repetir
 * solo en bancos" / "solo en el ERP", nunca en los dos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PagoConsumer {

    private static final int MAX_INTENTOS_POR_DESTINO = 3;

    private final PagoRepository pagoRepository;
    private final EnvioDestinoRepository envioDestinoRepository;
    private final BankPaymentMapper bankPaymentMapper;
    private final BankIntegrationService bankIntegrationService;

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    @Transactional
    public void procesarPago(PagoMessage mensaje) {
        Optional<Pago> pagoOpt = pagoRepository.findById(mensaje.getPagoId());

        if (pagoOpt.isEmpty()) {
            log.warn("Pago {} ya no existe en la base de datos, se descarta el mensaje.", mensaje.getPagoId());
            return;
        }

        Pago pago = pagoOpt.get();

        if ("enviado".equals(pago.getEstadoEnvio())) {
            log.info("Pago {} ya estaba marcado como enviado, se ignora mensaje duplicado.", pago.getId());
            return;
        }

        boolean bancoOk = procesarDestinoBanco(pago);
        // TODO cuando llegue el contrato del ERP: boolean erpOk = procesarDestinoErp(pago);
        // y abajo, enviado = bancoOk && erpOk. Por ahora solo depende del banco.
        boolean todosExitosos = bancoOk;
        boolean algunoFallidoDefinitivo = huboFallidoDefinitivo(pago.getId());

        if (todosExitosos) {
            pago.setEstadoEnvio("enviado");
            pago.setFechaEnvio(LocalDateTime.now());
            log.info("Pago {} enviado con exito a todos los destinos.", pago.getId());
        } else if (algunoFallidoDefinitivo) {
            pago.setEstadoEnvio("fallido");
            log.error("Pago {} fallido definitivo en al menos un destino.", pago.getId());
        } else {
            // Todavia hay destinos pendientes de reintentar (fallo reintentable o faltan por llamar).
            pago.setEstadoEnvio("pendiente");
            log.warn("Pago {} queda pendiente: algun destino aun no se confirma.", pago.getId());
        }

        pagoRepository.save(pago);
    }

    /**
     * Procesa (o salta, si ya esta EXITOSO) el destino BANCO para este pago.
     * @return true si el banco ya confirmo el pago (hoy o en un intento anterior).
     */
    private boolean procesarDestinoBanco(Pago pago) {
        EnvioDestino envio = envioDestinoRepository
                .findByPagoIdAndDestino(pago.getId(), EnvioDestino.Destino.BANCO.name())
                .orElseGet(() -> new EnvioDestino(
                        pago.getId(),
                        EnvioDestino.Destino.BANCO,
                        bankPaymentMapper.idempotencyKeyParaPago(pago)
                ));

        if (envio.esExitoso()) {
            log.info("Pago {} -> BANCO ya estaba EXITOSO, se salta.", pago.getId());
            return true;
        }
        if (envio.esFallidoDefinitivo()) {
            log.info("Pago {} -> BANCO ya quedo FALLIDO_DEFINITIVO, no se reintenta.", pago.getId());
            return false;
        }

        BankPaymentRequestDto request = bankPaymentMapper.aBankPaymentRequest(pago);
        BankSendOutcome resultado = bankIntegrationService.enviar(request, envio.getIdempotencyKey());

        envio.setIntentos(envio.getIntentos() + 1);
        envio.setUltimoHttpStatus(resultado.httpStatus);
        envio.setUltimoCodigoError(resultado.codigoError);

        switch (resultado.tipo) {
            case EXITOSO -> {
                envio.marcarExitoso(resultado.receiptId);
                envioDestinoRepository.save(envio);
                return true;
            }
            case RECHAZADO_DEFINITIVO -> {
                envio.marcarFallidoDefinitivo("HTTP " + resultado.httpStatus + " " + resultado.codigoError);
                envioDestinoRepository.save(envio);
                // No reintentable: a la DLQ de inmediato, sin gastar mas intentos (seccion 5.3 de la guia).
                throw new AmqpRejectAndDontRequeueException(
                        "Pago " + pago.getId() + " rechazado definitivamente por BANCO: " + resultado.codigoError);
            }
            default -> { // REINTENTAR
                if (envio.getIntentos() >= MAX_INTENTOS_POR_DESTINO) {
                    envio.marcarFallidoDefinitivo("Agoto " + MAX_INTENTOS_POR_DESTINO + " intentos. Ultimo: HTTP " + resultado.httpStatus + " " + resultado.codigoError);
                    envioDestinoRepository.save(envio);
                    log.error("Pago {} -> BANCO agoto los {} intentos.", pago.getId(), MAX_INTENTOS_POR_DESTINO);
                    return false;
                }
                envioDestinoRepository.save(envio);
                log.warn("Pago {} -> BANCO fallo (intento {}), se reintentara.", pago.getId(), envio.getIntentos());
                return false;
            }
        }
    }

    private boolean huboFallidoDefinitivo(Long pagoId) {
        List<EnvioDestino> envios = envioDestinoRepository.findByPagoId(pagoId);
        return envios.stream().anyMatch(EnvioDestino::esFallidoDefinitivo);
    }
}
