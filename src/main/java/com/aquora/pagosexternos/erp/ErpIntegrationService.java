package com.aquora.pagosexternos.erp;

import com.aquora.pagosexternos.erp.dto.ErpComprobanteDto;
import com.aquora.pagosexternos.erp.dto.ErpRegistroPagoDto;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Unico punto de contacto con la API del ERP. Clasifica cada respuesta segun
 * el contrato oficial (erp-payments-api.yaml, entregado 8/10):
 *
 *   201                                  -> EXITOSO
 *   409 codigo_error=DOCUMENTO_DUPLICADO -> EXITOSO (ya estaba entregado; ack)
 *   409 codigo_error=DOCUMENTO_REUTILIZADO -> RECHAZADO_DEFINITIVO (bug del productor)
 *   400 / 403 / 422                      -> RECHAZADO_DEFINITIVO
 *   429 / 500 / 502 / 503 / 504          -> REINTENTAR (respetando Retry-After)
 *
 * Mismo patron que BankIntegrationService; se mantiene separado (no se generaliza
 * con el banco) porque los campos del error vienen en español/snake_case y porque
 * el ERP, segun el equipo confirmo con el profe, "falla menos pero es mas lento",
 * lo que puede requerir afinar timeouts distinto al banco mas adelante.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpIntegrationService {

    private final ErpFeignClient erpApi;

    private static final List<Integer> REINTENTABLES = List.of(429, 500, 502, 503, 504);
    private static final Pattern RETRY_AFTER_SEGUNDOS = Pattern.compile("^\\d+$");

    public ErpSendOutcome enviar(ErpRegistroPagoDto request) {
        String correlationId = UUID.randomUUID().toString();
        try {
            // x-api-key lo inyecta ErpFeignConfig. Firma real: body, X-Correlation-Id, X-Simulation-Scenario.
            ResponseEntity<ErpComprobanteDto> respuesta = erpApi.registrarPagoErp(request, correlationId, null);
            ErpComprobanteDto comprobante = respuesta.getBody();
            String numeroComprobante = comprobante == null ? null : comprobante.getNumeroComprobante();
            log.info("[ERP] 201 comprobante={} corr={}", numeroComprobante, correlationId);
            return ErpSendOutcome.exitoso(201, numeroComprobante);

        } catch (FeignException.Conflict e) {
            // 409: puede ser DOCUMENTO_DUPLICADO (exito/ack) o DOCUMENTO_REUTILIZADO (bug, DLQ)
            String body = e.contentUTF8();
            if (body != null && body.contains("DOCUMENTO_DUPLICADO")) {
                log.info("[ERP] 409 DOCUMENTO_DUPLICADO -> se trata como exito. corr={}", correlationId);
                return ErpSendOutcome.exitoso(409, extraerNumeroComprobanteDeDuplicado(body));
            }
            log.error("[ERP] 409 DOCUMENTO_REUTILIZADO (bug del productor) corr={} body={}", correlationId, body);
            return ErpSendOutcome.rechazadoDefinitivo(409, "DOCUMENTO_REUTILIZADO");

        } catch (FeignException e) {
            int status = e.status();
            String bodyError = e.contentUTF8();
            String codigo = extraerCodigo(bodyError);
            Long retryAfterMillis = extraerRetryAfterMillis(e);

            if (status == 400 || status == 403 || status == 422) {
                log.error("[ERP] {} no reintentable corr={} codigo={} body={}", status, correlationId, codigo, bodyError);
                return ErpSendOutcome.rechazadoDefinitivo(status, codigo);
            }
            if (REINTENTABLES.contains(status)) {
                log.warn("[ERP] {} reintentable corr={} codigo={} retryAfterMs={} body={}", status, correlationId, codigo, retryAfterMillis, bodyError);
                return ErpSendOutcome.reintentar(status, codigo, retryAfterMillis);
            }
            log.warn("[ERP] status no previsto {} corr={} body={}, se trata como reintentable", status, correlationId, bodyError);
            return ErpSendOutcome.reintentar(status, codigo, null);

        } catch (Exception e) {
            // Timeout / error de red: no se sabe si el ERP registro el pago.
            // Se reintenta; el 409 DOCUMENTO_DUPLICADO del siguiente intento lo resuelve.
            log.warn("[ERP] timeout/error de red corr={}: {}", correlationId, e.toString());
            return ErpSendOutcome.reintentar(null, "TIMEOUT_O_RED", null);
        }
    }

    /** codigo_error (snake_case en el JSON del ERP). */
    private String extraerCodigo(String body) {
        if (body == null) return null;
        int i = body.indexOf("\"codigo_error\"");
        if (i < 0) return null;
        int start = body.indexOf('"', i + 15) + 1;
        int end = body.indexOf('"', start);
        return (start > 0 && end > start) ? body.substring(start, end) : null;
    }

    private String extraerNumeroComprobanteDeDuplicado(String body) {
        int i = body.indexOf("\"numero_comprobante\"");
        if (i < 0) return null;
        int start = body.indexOf('"', i + 21) + 1;
        int end = body.indexOf('"', start);
        return (start > 0 && end > start) ? body.substring(start, end) : null;
    }

    private Long extraerRetryAfterMillis(FeignException e) {
        return e.responseHeaders().getOrDefault("Retry-After", List.of()).stream()
                .findFirst()
                .filter(v -> RETRY_AFTER_SEGUNDOS.matcher(v).matches())
                .map(v -> Long.parseLong(v) * 1000)
                .orElse(null);
    }
}
