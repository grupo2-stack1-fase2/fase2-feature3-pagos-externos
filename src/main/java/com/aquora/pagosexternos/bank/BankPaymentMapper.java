package com.aquora.pagosexternos.bank;

// Nombres confirmados el 22/10 corriendo `mvn clean compile` real contra el
// YAML de src/main/resources/openapi/bank-payments-api.yaml.
import com.aquora.pagosexternos.bank.dto.BankAmountDto;
import com.aquora.pagosexternos.bank.dto.BankPayerDto;
import com.aquora.pagosexternos.bank.dto.BankPaymentRequestDto;
import com.aquora.pagosexternos.model.Pago;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.OffsetDateTime;

/**
 * Convierte un Pago (tabla pagos, AQUORA) al request que espera el banco.
 * Ver seccion 7 de la guia "API Bancos" para el detalle de cada campo.
 */
@Component
public class BankPaymentMapper {

    /** Prefijo de equipo. Cambiar segun el equipo asignado (EQA, EQB, EQC o EQD). */
    private static final String PREFIJO_IDEMPOTENCIA = "EQA";

    /** Debe coincidir con la API key que use este equipo. */
    private static final String TEAM_CODE = "EQUIPO_A";

    /**
     * La idempotency key tiene que ser SIEMPRE la misma para el mismo pago,
     * en todos los reintentos. Por eso se deriva solo del id del pago, nunca
     * de un UUID aleatorio.
     */
    public String idempotencyKeyParaPago(Pago pago) {
        return PREFIJO_IDEMPOTENCIA + "-" + String.format("%06d", pago.getId());
    }

    public BankPaymentRequestDto aBankPaymentRequest(Pago pago) {
        BankPaymentRequestDto req = new BankPaymentRequestDto();
        req.setTeamCode(BankPaymentRequestDto.TeamCodeEnum.fromValue(TEAM_CODE));

        BankPayerDto payer = new BankPayerDto();
        // TODO: confirmar con el esquema real de AQUORA de donde sale el
        // codigo/nombre/NIT del cliente (join con la tabla clientes via lectura_id).
        payer.setCustomerCode("CLI-" + String.format("%04d", pago.getLecturaId()));
        req.setPayer(payer);

        BankAmountDto amount = new BankAmountDto();
        amount.setValueInCents(aCentavos(pago.getMonto()));
        amount.setCurrency(BankAmountDto.CurrencyEnum.GTQ);
        req.setAmount(amount);

        req.setTransactionDate(
                pago.getFechaPago() == null
                        ? OffsetDateTime.now(ZoneId.of("America/Guatemala"))
                        : pago.getFechaPago().atZone(ZoneId.of("America/Guatemala")).toOffsetDateTime()
        );

        req.setChannel(mapearCanal(pago.getMetodo()));
        req.setDescription("Servicio de agua - pago " + pago.getId());
        return req;
    }

    /** CASH/CARD/BANK_TRANSFER/CHECK. El banco rechaza CHECK con 422. */
    private BankPaymentRequestDto.ChannelEnum mapearCanal(String metodoAquora) {
        if (metodoAquora == null) return BankPaymentRequestDto.ChannelEnum.CASH;
        return switch (metodoAquora.toLowerCase()) {
            case "efectivo" -> BankPaymentRequestDto.ChannelEnum.CASH;
            case "transferencia" -> BankPaymentRequestDto.ChannelEnum.BANK_TRANSFER;
            case "deposito" -> BankPaymentRequestDto.ChannelEnum.CARD; // revisar con el profe (seccion 10 pregunta 4)
            default -> BankPaymentRequestDto.ChannelEnum.CASH;
        };
    }

    /** 150.75 -> 15075. Nunca con double. */
    private long aCentavos(Double montoGtq) {
        BigDecimal monto = BigDecimal.valueOf(montoGtq == null ? 0.0 : montoGtq);
        return monto.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }
}
