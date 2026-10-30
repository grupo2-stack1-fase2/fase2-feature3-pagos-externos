package com.aquora.pagosexternos.consumer;

import com.aquora.pagosexternos.bank.BankIntegrationService;
import com.aquora.pagosexternos.bank.BankPaymentMapper;
import com.aquora.pagosexternos.bank.BankSendOutcome;
import com.aquora.pagosexternos.bank.dto.BankPaymentRequestDto;
import com.aquora.pagosexternos.dto.PagoMessage;
import com.aquora.pagosexternos.erp.ErpIntegrationService;
import com.aquora.pagosexternos.erp.ErpPaymentMapper;
import com.aquora.pagosexternos.erp.ErpSendOutcome;
import com.aquora.pagosexternos.erp.dto.ErpRegistroPagoDto;
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
import com.aquora.pagosexternos.logging.PagoLogDynamoService;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Este es el "consumer" del diagrama: toma el mensaje de la cola y llama a cada
 * destino externo (banco y ERP, ambos con contrato oficial del docente desde el
 * 8/10). Al final actualiza el flag en la tabla pagos (lo que en el diagrama
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
    private final ErpPaymentMapper erpPaymentMapper;
    private final ErpIntegrationService erpIntegrationService;
    private final PagoLogDynamoService pagoLogDynamoService;

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
        boolean erpOk = procesarDestinoErp(pago);
        boolean todosExitosos = bancoOk && erpOk;
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
            pagoLogDynamoService.registrarLog(pago.getId(), "BANCO", "EXITOSO",
                    resultado.httpStatus, null, resultado.receiptId);
            return true;
        }
        case RECHAZADO_DEFINITIVO -> {
            envio.marcarFallidoDefinitivo("HTTP " + resultado.httpStatus + " " + resultado.codigoError);
            envioDestinoRepository.save(envio);
            pagoLogDynamoService.registrarLog(pago.getId(), "BANCO", "RECHAZADO_DEFINITIVO",
                    resultado.httpStatus, resultado.codigoError, null);
            throw new AmqpRejectAndDontRequeueException(
                    "Pago " + pago.getId() + " rechazado definitivamente por BANCO: " + resultado.codigoError);
        }
        default -> { // REINTENTAR
            if (envio.getIntentos() >= MAX_INTENTOS_POR_DESTINO) {
                envio.marcarFallidoDefinitivo("Agoto " + MAX_INTENTOS_POR_DESTINO + " intentos. Ultimo: HTTP " + resultado.httpStatus + " " + resultado.codigoError);
                envioDestinoRepository.save(envio);
                pagoLogDynamoService.registrarLog(pago.getId(), "BANCO", "FALLIDO_DEFINITIVO_REINTENTOS",
                        resultado.httpStatus, resultado.codigoError, null);
                log.error("Pago {} -> BANCO agoto los {} intentos.", pago.getId(), MAX_INTENTOS_POR_DESTINO);
                return false;
            }
            envioDestinoRepository.save(envio);
            pagoLogDynamoService.registrarLog(pago.getId(), "BANCO", "REINTENTAR",
                    resultado.httpStatus, resultado.codigoError, null);
            log.warn("Pago {} -> BANCO fallo (intento {}), se reintentara.", pago.getId(), envio.getIntentos());
            return false;
        }
    }
}

    /**
     * Procesa (o salta, si ya esta EXITOSO) el destino ERP para este pago.
     * Mismo patron que procesarDestinoBanco: idempotencia por destino via
     * envios_banco (banco='ERP'), y un rechazo definitivo manda el mensaje
     * completo a la DLQ de inmediato (no tiene sentido seguir reintentando
     * el banco si el ERP ya dijo que el pago esta mal formado, por ejemplo).
     * @return true si el ERP ya confirmo el pago (hoy o en un intento anterior).
     */
    private boolean procesarDestinoErp(Pago pago) {
    EnvioDestino envio = envioDestinoRepository
            .findByPagoIdAndDestino(pago.getId(), EnvioDestino.Destino.ERP.name())
            .orElseGet(() -> new EnvioDestino(
                    pago.getId(),
                    EnvioDestino.Destino.ERP,
                    erpPaymentMapper.numeroDocumentoParaPago(pago)
            ));

    if (envio.esExitoso()) {
        log.info("Pago {} -> ERP ya estaba EXITOSO, se salta.", pago.getId());
        return true;
    }
    if (envio.esFallidoDefinitivo()) {
        log.info("Pago {} -> ERP ya quedo FALLIDO_DEFINITIVO, no se reintenta.", pago.getId());
        return false;
    }

    ErpRegistroPagoDto request = erpPaymentMapper.aErpRegistroPago(pago);
    ErpSendOutcome resultado = erpIntegrationService.enviar(request);

    envio.setIntentos(envio.getIntentos() + 1);
    envio.setUltimoHttpStatus(resultado.httpStatus);
    envio.setUltimoCodigoError(resultado.codigoError);

    switch (resultado.tipo) {
        case EXITOSO -> {
            envio.marcarExitoso(resultado.numeroComprobante);
            envioDestinoRepository.save(envio);
            pagoLogDynamoService.registrarLog(pago.getId(), "ERP", "EXITOSO",
                    resultado.httpStatus, null, resultado.numeroComprobante);
            return true;
        }
        case RECHAZADO_DEFINITIVO -> {
            envio.marcarFallidoDefinitivo("HTTP " + resultado.httpStatus + " " + resultado.codigoError);
            envioDestinoRepository.save(envio);
            pagoLogDynamoService.registrarLog(pago.getId(), "ERP", "RECHAZADO_DEFINITIVO",
                    resultado.httpStatus, resultado.codigoError, null);
            throw new AmqpRejectAndDontRequeueException(
                    "Pago " + pago.getId() + " rechazado definitivamente por ERP: " + resultado.codigoError);
        }
        default -> { // REINTENTAR
            if (envio.getIntentos() >= MAX_INTENTOS_POR_DESTINO) {
                envio.marcarFallidoDefinitivo("Agoto " + MAX_INTENTOS_POR_DESTINO + " intentos. Ultimo: HTTP " + resultado.httpStatus + " " + resultado.codigoError);
                envioDestinoRepository.save(envio);
                pagoLogDynamoService.registrarLog(pago.getId(), "ERP", "FALLIDO_DEFINITIVO_REINTENTOS",
                        resultado.httpStatus, resultado.codigoError, null);
                log.error("Pago {} -> ERP agoto los {} intentos.", pago.getId(), MAX_INTENTOS_POR_DESTINO);
                return false;
            }
            envioDestinoRepository.save(envio);
            pagoLogDynamoService.registrarLog(pago.getId(), "ERP", "REINTENTAR",
                    resultado.httpStatus, resultado.codigoError, null);
            log.warn("Pago {} -> ERP fallo (intento {}), se reintentara.", pago.getId(), envio.getIntentos());
            return false;
        }
    }
}

    private boolean huboFallidoDefinitivo(Long pagoId) {
        List<EnvioDestino> envios = envioDestinoRepository.findByPagoId(pagoId);
        return envios.stream().anyMatch(EnvioDestino::esFallidoDefinitivo);
    }
}
