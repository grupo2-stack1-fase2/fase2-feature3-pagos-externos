package com.aquora.pagosexternos.bank;

// Nombres confirmados el 28/10 generando con el YAML OFICIAL del docente
// (src/main/resources/openapi/bank-payments-api.yaml). El borrador reconstruido
// que se uso antes nombraba estos esquemas BankPayer/BankAmount; el contrato
// real los llama simplemente Payer/Money -> PayerDto/MoneyDto.
import com.aquora.pagosexternos.bank.dto.MoneyDto;
import com.aquora.pagosexternos.bank.dto.PayerDto;
import com.aquora.pagosexternos.bank.dto.TeamCodeDto;
import com.aquora.pagosexternos.bank.dto.BankPaymentRequestDto;
import com.aquora.pagosexternos.model.Pago;
import com.aquora.pagosexternos.repository.ClienteInfoRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class BankPaymentMapper {

    private final ClienteInfoRepository clienteInfoRepository;

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
        req.setTeamCode(TeamCodeDto.fromValue(TEAM_CODE));

        PayerDto payer = new PayerDto();
        payer.setCustomerCode("CLI-" + String.format("%04d", pago.getLecturaId()));
        // Nombre real del cliente (via lecturas -> contadores -> clientes). El banco
        // acepta hasta 150 caracteres; taxId no existe en el esquema de AQUORA y
        // es opcional en el contrato, por eso se deja sin llenar.
        clienteInfoRepository.buscarNombreClientePorPago(pago.getId())
                .ifPresent(nombre -> payer.setFullName(
                        nombre.length() > 150 ? nombre.substring(0, 150) : nombre));
        req.setPayer(payer);

        MoneyDto amount = new MoneyDto();
        amount.setValueInCents(aCentavos(pago.getMonto()));
        amount.setCurrency(MoneyDto.CurrencyEnum.GTQ);
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
