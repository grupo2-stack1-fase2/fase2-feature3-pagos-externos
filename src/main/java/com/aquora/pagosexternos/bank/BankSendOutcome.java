package com.aquora.pagosexternos.bank;

/** Resultado de un intento de envio al banco, ya clasificado segun la seccion 5 de la guia. */
public class BankSendOutcome {

    public enum Tipo { EXITOSO, REINTENTAR, RECHAZADO_DEFINITIVO }

    public final Tipo tipo;
    public final Integer httpStatus;
    public final String codigoError;
    public final String receiptId;
    public final Long retryAfterMillis; // de Retry-After, si vino

    private BankSendOutcome(Tipo tipo, Integer httpStatus, String codigoError, String receiptId, Long retryAfterMillis) {
        this.tipo = tipo;
        this.httpStatus = httpStatus;
        this.codigoError = codigoError;
        this.receiptId = receiptId;
        this.retryAfterMillis = retryAfterMillis;
    }

    public static BankSendOutcome exitoso(int httpStatus, String receiptId) {
        return new BankSendOutcome(Tipo.EXITOSO, httpStatus, null, receiptId, null);
    }

    public static BankSendOutcome reintentar(Integer httpStatus, String codigoError, Long retryAfterMillis) {
        return new BankSendOutcome(Tipo.REINTENTAR, httpStatus, codigoError, null, retryAfterMillis);
    }

    public static BankSendOutcome rechazadoDefinitivo(Integer httpStatus, String codigoError) {
        return new BankSendOutcome(Tipo.RECHAZADO_DEFINITIVO, httpStatus, codigoError, null, null);
    }
}
