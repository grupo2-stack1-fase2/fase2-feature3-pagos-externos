-- Ejecutar esto UNA VEZ sobre tu base de datos local de AQUORA (MariaDB).
-- Solo agrega columnas nuevas a la tabla `pagos` ya existente. No borra ni modifica nada más.
-- No afecta ninguna consulta ni vista ya construida en AQUORA, porque son columnas adicionales
-- con valor por defecto, y el resto del sistema sigue funcionando igual.

ALTER TABLE pagos
    ADD COLUMN estado_envio VARCHAR(20) NOT NULL DEFAULT 'pendiente',
    ADD COLUMN fecha_envio DATETIME NULL,
    ADD COLUMN intentos_envio INT NOT NULL DEFAULT 0;

-- estado_envio puede ser: 'pendiente', 'en_cola', 'enviado', 'fallido'

-- (Opcional) para poder probar el flujo de inmediato sin esperar a que entren pagos nuevos,
-- puedes forzar algunos pagos ya existentes a estado 'pendiente' para que el Producer los recoja:
-- UPDATE pagos SET estado_envio = 'pendiente' WHERE id IN (1, 2, 3);
