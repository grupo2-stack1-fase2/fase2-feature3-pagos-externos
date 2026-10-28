package com.aquora.pagosexternos.erp;

import com.aquora.pagosexternos.erp.dto.ErpContribuyenteDto;
import com.aquora.pagosexternos.erp.dto.ErpDocumentoOrigenDto;
import com.aquora.pagosexternos.erp.dto.ErpEquipoDto;
import com.aquora.pagosexternos.erp.dto.ErpLineaDetalleDto;
import com.aquora.pagosexternos.erp.dto.ErpPeriodoDto;
import com.aquora.pagosexternos.erp.dto.ErpRegistroPagoDto;
import com.aquora.pagosexternos.model.Pago;
import com.aquora.pagosexternos.repository.ClienteInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Convierte un Pago (tabla pagos, AQUORA) al request que espera el ERP
 * (erp-payments-api.yaml, contrato oficial entregado por el docente el 8/10).
 *
 * El ERP pide el monto como TEXTO con 2 decimales (no centavos como el banco)
 * y pide un desglose "detalle[]" por rubro cuya suma debe calzar con "total".
 * AQUORA no guarda ese desglose (la tabla pagos solo tiene un monto total),
 * asi que se manda una sola linea con rubro=AGUA_POTABLE por el monto completo
 * (decision documentada en el README, seccion 2.3).
 */
@Component
@RequiredArgsConstructor
public class ErpPaymentMapper {

    private final ClienteInfoRepository clienteInfoRepository;

    /** Mismo prefijo de equipo que usa el banco (BankPaymentMapper.PREFIJO_IDEMPOTENCIA). */
    private static final String PREFIJO_IDEMPOTENCIA = "EQA";

    /** Equipo que envia el pago (debe coincidir con la api-key del ERP para este equipo). */
    private static final ErpEquipoDto EQUIPO = ErpEquipoDto.EQUIPO_A;

    /**
     * Mismo esquema que BankPaymentMapper#idempotencyKeyParaPago: estable,
     * derivado solo del id del pago (nunca un UUID nuevo por intento). Se repite
     * aqui a proposito en vez de reusar la del banco porque "documento_origen.numero"
     * es la llave de unicidad de un contrato distinto (podrian, en principio,
     * divergir en el futuro); hoy usan el mismo valor por simplicidad de logs.
     */
    public String numeroDocumentoParaPago(Pago pago) {
        return PREFIJO_IDEMPOTENCIA + "-" + String.format("%06d", pago.getId());
    }

    public ErpRegistroPagoDto aErpRegistroPago(Pago pago) {
        ErpRegistroPagoDto req = new ErpRegistroPagoDto();
        req.setEquipo(EQUIPO);

        OffsetDateTime fechaPago = pago.getFechaPago() == null
                ? OffsetDateTime.now(ZoneId.of("America/Guatemala"))
                : pago.getFechaPago().atZone(ZoneId.of("America/Guatemala")).toOffsetDateTime();

        ErpDocumentoOrigenDto documentoOrigen = new ErpDocumentoOrigenDto();
        // No hay en AQUORA un tipo de documento distinto por pago: se usa RECIBO_CAJA
        // para todos (es el mas generico de los 3 que acepta el contrato).
        documentoOrigen.setTipo(ErpDocumentoOrigenDto.TipoEnum.RECIBO_CAJA);
        documentoOrigen.setNumero(numeroDocumentoParaPago(pago));
        documentoOrigen.setFechaEmision(fechaPago.toLocalDate());
        req.setDocumentoOrigen(documentoOrigen);

        ErpContribuyenteDto contribuyente = new ErpContribuyenteDto();
        contribuyente.setCodigoCliente("CLI-" + String.format("%04d", pago.getLecturaId()));
        // codigoContador y nit son opcionales; AQUORA no tiene NIT de clientes (igual que con el banco).
        req.setContribuyente(contribuyente);

        ErpPeriodoDto periodo = new ErpPeriodoDto();
        periodo.setAnio(fechaPago.getYear());
        periodo.setMes(fechaPago.getMonthValue());
        req.setPeriodoFiscal(periodo);

        req.setFormaPago(mapearFormaPago(pago.getMetodo()));
        req.setFechaPago(fechaPago);

        String totalTexto = aTextoConDosDecimales(pago.getMonto());
        req.setTotal(totalTexto);

        ErpLineaDetalleDto linea = new ErpLineaDetalleDto();
        linea.setRubro(ErpLineaDetalleDto.RubroEnum.AGUA_POTABLE);
        linea.setMonto(totalTexto);
        linea.setDescripcion("Servicio de agua - pago " + pago.getId());
        req.setDetalle(java.util.List.of(linea));

        return req;
    }

    /** EFECTIVO/TARJETA/TRANSFERENCIA/CHEQUE. */
    private ErpRegistroPagoDto.FormaPagoEnum mapearFormaPago(String metodoAquora) {
        if (metodoAquora == null) return ErpRegistroPagoDto.FormaPagoEnum.EFECTIVO;
        return switch (metodoAquora.toLowerCase()) {
            case "efectivo" -> ErpRegistroPagoDto.FormaPagoEnum.EFECTIVO;
            case "transferencia" -> ErpRegistroPagoDto.FormaPagoEnum.TRANSFERENCIA;
            // "deposito" no tiene equivalente exacto en forma_pago; igual que con el
            // canal del banco (CARD), se mapea a TRANSFERENCIA mientras se confirma
            // con el profe (ver seccion 10 pregunta 4 de la guia del banco).
            case "deposito" -> ErpRegistroPagoDto.FormaPagoEnum.TRANSFERENCIA;
            default -> ErpRegistroPagoDto.FormaPagoEnum.EFECTIVO;
        };
    }

    /** 150.75 -> "150.75". El ERP pide el monto como texto con EXACTAMENTE 2 decimales (nunca double). */
    private String aTextoConDosDecimales(Double montoGtq) {
        BigDecimal monto = BigDecimal.valueOf(montoGtq == null ? 0.0 : montoGtq);
        return monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
