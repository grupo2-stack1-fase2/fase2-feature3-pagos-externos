package com.aquora.pagosexternos.bank;

// Firma confirmada el 22/10 contra el BankApi.java realmente generado.
import com.aquora.pagosexternos.bank.dto.BankPaymentReceiptDto;
import com.aquora.pagosexternos.bank.dto.BankPaymentRequestDto;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Unico punto de contacto con la API del banco. Clasifica cada respuesta
 * segun la tabla de la seccion 5 de la guia "API Bancos - Guia de integracion".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankIntegrationService {

    private final BankFeignClient bankApi;

    @Value("${app.bank.api-key}")
    private String apiKey;

    @Value("${app.bank.team-code:EQUIPO_A}")
    private String teamCode;

    private static final List<Integer> REINTENTABLES = List.of(429, 500, 502, 503, 504);
    private static final Pattern RETRY_AFTER_SEGUNDOS = Pattern.compile("^\\d+$");

    public BankSendOutcome enviar(BankPaymentRequestDto request, String idempotencyKey) {
        String correlationId = UUID.randomUUID().toString();
        try {
            // Orden real: x-api-key, Idempotency-Key, body, X-Correlation-Id, X-Simulation-Scenario
            ResponseEntity<BankPaymentReceiptDto> respuesta = bankApi.sendBankPayment(
                    apiKey, idempotencyKey, request, correlationId, null
            );
            BankPaymentReceiptDto receipt = respuesta.getBody();
            String receiptId = receipt == null ? null : receipt.getReceiptId();
            log.info("[Banco] 201 recibo={} corr={}", receiptId, correlationId);
            return BankSendOutcome.exitoso(201, receiptId);

        } catch (FeignException.Conflict e) {
            // 409: puede ser DUPLICATE_PAYMENT (exito) o IDEMPOTENCY_KEY_REUSED (bug, DLQ)
            String body = e.contentUTF8();
            if (body != null && body.contains("DUPLICATE_PAYMENT")) {
                log.info("[Banco] 409 DUPLICATE_PAYMENT -> se trata como exito. corr={}", correlationId);
                return BankSendOutcome.exitoso(409, extraerReceiptIdDeDuplicado(body));
            }
            log.error("[Banco] 409 IDEMPOTENCY_KEY_REUSED (bug del productor) corr={} body={}", correlationId, body);
            return BankSendOutcome.rechazadoDefinitivo(409, "IDEMPOTENCY_KEY_REUSED");

        } catch (FeignException e) {
            int status = e.status();
            String codigo = extraerCodigo(e.contentUTF8());
            Long retryAfterMillis = extraerRetryAfterMillis(e);

            if (status == 400 || status == 403 || status == 422) {
                log.error("[Banco] {} no reintentable corr={} codigo={}", status, correlationId, codigo);
                return BankSendOutcome.rechazadoDefinitivo(status, codigo);
            }
            if (REINTENTABLES.contains(status)) {
                log.warn("[Banco] {} reintentable corr={} codigo={} retryAfterMs={}", status, correlationId, codigo, retryAfterMillis);
                return BankSendOutcome.reintentar(status, codigo, retryAfterMillis);
            }
            // Status no previsto: por seguridad, se trata como reintentable.
            log.warn("[Banco] status no previsto {} corr={}, se trata como reintentable", status, correlationId);
            return BankSendOutcome.reintentar(status, codigo, null);

        } catch (Exception e) {
            // Timeout / error de red: no se sabe si el banco registro el pago.
            // Se reintenta; el 409 DUPLICATE_PAYMENT del siguiente intento lo resuelve (seccion 5.2).
            log.warn("[Banco] timeout/error de red corr={}: {}", correlationId, e.toString());
            return BankSendOutcome.reintentar(null, "TIMEOUT_O_RED", null);
        }
    }

    /** Los errores de API Gateway (403 por key, 429, 502, 504) no traen "code": hay que tolerarlo. */
    private String extraerCodigo(String body) {
        if (body == null) return null;
        int i = body.indexOf("\"code\"");
        if (i < 0) return null;
        int start = body.indexOf('"', i + 7) + 1;
        int end = body.indexOf('"', start);
        return (start > 0 && end > start) ? body.substring(start, end) : null;
    }

    private String extraerReceiptIdDeDuplicado(String body) {
        int i = body.indexOf("\"receiptId\"");
        if (i < 0) return null;
        int start = body.indexOf('"', i + 12) + 1;
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
