package com.aquora.pagosexternos.erp;

/** Resultado de un intento de envio al ERP, clasificado segun el contrato oficial (erp-payments-api.yaml). */
public class ErpSendOutcome {

    public enum Tipo { EXITOSO, REINTENTAR, RECHAZADO_DEFINITIVO }

    public final Tipo tipo;
    public final Integer httpStatus;
    public final String codigoError;
    public final String numeroComprobante;
    public final Long retryAfterMillis;

    private ErpSendOutcome(Tipo tipo, Integer httpStatus, String codigoError, String numeroComprobante, Long retryAfterMillis) {
        this.tipo = tipo;
        this.httpStatus = httpStatus;
        this.codigoError = codigoError;
        this.numeroComprobante = numeroComprobante;
        this.retryAfterMillis = retryAfterMillis;
    }

    public static ErpSendOutcome exitoso(int httpStatus, String numeroComprobante) {
        return new ErpSendOutcome(Tipo.EXITOSO, httpStatus, null, numeroComprobante, null);
    }

    public static ErpSendOutcome reintentar(Integer httpStatus, String codigoError, Long retryAfterMillis) {
        return new ErpSendOutcome(Tipo.REINTENTAR, httpStatus, codigoError, null, retryAfterMillis);
    }

    public static ErpSendOutcome rechazadoDefinitivo(Integer httpStatus, String codigoError) {
        return new ErpSendOutcome(Tipo.RECHAZADO_DEFINITIVO, httpStatus, codigoError, null, null);
    }
}
