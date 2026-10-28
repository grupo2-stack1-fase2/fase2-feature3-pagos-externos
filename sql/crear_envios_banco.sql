-- Tabla envios_banco: control de envio por pago y por destino

-- Objetivo: idempotencia por destino. Una fila por (pago, banco).
-- Antes de llamar a un destino, el consumer revisa si esa fila ya
-- esta en 'exitoso' y, si es asi, no lo vuelve a llamar.

CREATE TABLE IF NOT EXISTS envios_banco (
    id             INT UNSIGNED NOT NULL AUTO_INCREMENT,
    pago_id        INT UNSIGNED NOT NULL,
    banco          VARCHAR(20)  NOT NULL,
    estado         VARCHAR(20)  NOT NULL DEFAULT 'pendiente',
    intentos       INT UNSIGNED NOT NULL DEFAULT 0,
    ultimo_error   VARCHAR(255) NULL,
    fecha_envio    DATETIME     NULL,
    creado_en      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_envios_pago_banco (pago_id, banco),
    KEY idx_envios_estado (estado),
    CONSTRAINT chk_envios_estado
        CHECK (estado IN ('pendiente', 'exitoso', 'fallido')),
    CONSTRAINT fk_envios_pago
        FOREIGN KEY (pago_id) REFERENCES pagos (id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;