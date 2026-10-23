package com.aquora.pagosexternos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Mapea la tabla `envios_banco`, creada por Blanky en la rama
 * feature/envios-banco (sql/crear_envios_banco.sql) y ampliada con
 * sql/alterar_envios_banco_idempotencia.sql (idempotency_key, receipt_id,
 * ultimo_http_status, ultimo_codigo_error).
 *
 * La columna se llama "banco" por como la diseno Blanky, pero en el codigo
 * sirve para cualquier destino (BANCO, y mas adelante ERP): simplemente se
 * le guarda el nombre del destino como texto.
 *
 * Los valores de "estado" estan limitados por un CHECK en la base a:
 * 'pendiente' | 'exitoso' | 'fallido'. No usar otros valores.
 */
@Entity
@Table(name = "envios_banco")
@Getter
@Setter
@NoArgsConstructor
public class EnvioDestino {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pago_id", nullable = false)
    private Long pagoId;

    @Column(name = "banco", nullable = false, length = 20)
    private String destino; // "BANCO" por ahora; "ERP" cuando llegue ese contrato

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = Estado.PENDIENTE.valor;

    @Column(name = "receipt_id", length = 100)
    private String receiptId;

    @Column(name = "intentos", nullable = false)
    private Integer intentos = 0;

    @Column(name = "ultimo_error")
    private String ultimoError;

    @Column(name = "ultimo_http_status")
    private Integer ultimoHttpStatus;

    @Column(name = "ultimo_codigo_error", length = 50)
    private String ultimoCodigoError;

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;

    public enum Destino { BANCO, ERP }

    /** Los .valor deben calzar EXACTO con el CHECK de la tabla envios_banco. */
    public enum Estado {
        PENDIENTE("pendiente"), EXITOSO("exitoso"), FALLIDO("fallido");

        public final String valor;
        Estado(String valor) { this.valor = valor; }
    }

    public EnvioDestino(Long pagoId, Destino destino, String idempotencyKey) {
        this.pagoId = pagoId;
        this.destino = destino.name();
        this.idempotencyKey = idempotencyKey;
        this.estado = Estado.PENDIENTE.valor;
        this.intentos = 0;
    }

    public boolean esExitoso() {
        return Estado.EXITOSO.valor.equals(estado);
    }

    public boolean esFallidoDefinitivo() {
        return Estado.FALLIDO.valor.equals(estado);
    }

    public void marcarExitoso(String receiptId) {
        this.estado = Estado.EXITOSO.valor;
        this.receiptId = receiptId;
        this.fechaEnvio = LocalDateTime.now();
    }

    public void marcarFallidoDefinitivo(String ultimoError) {
        this.estado = Estado.FALLIDO.valor;
        this.ultimoError = ultimoError;
    }
}
