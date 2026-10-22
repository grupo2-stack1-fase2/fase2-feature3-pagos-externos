-- Esta migracion se aplica DESPUES de sql/crear_envios_banco.sql (de Blanky, en
-- feature/envios-banco). Le agrega, sin tocar nada de lo que ella ya escribio,
-- las columnas que exige el contrato real del banco (seccion 8 de la guia):
--   - idempotency_key: debe ser estable, la misma en todos los reintentos del
--     mismo pago+destino.
--   - receipt_id: el comprobante que devuelve el banco en 201 o en el
--     originalReceipt de un 409 DUPLICATE_PAYMENT. Hay que guardarlo.
--   - ultimo_http_status / ultimo_codigo_error: para poder distinguir, por
--     ejemplo, un 429 (reintentable) de un 422 (nunca reintentable), que es
--     justo la tabla de la seccion 5 de la guia. El campo ultimo_error de
--     Blanky queda igual, por si se sigue usando para un mensaje libre.

ALTER TABLE envios_banco
  ADD COLUMN idempotency_key   VARCHAR(64) NULL AFTER banco,
  ADD COLUMN receipt_id        VARCHAR(100) NULL AFTER estado,
  ADD COLUMN ultimo_http_status INT NULL AFTER ultimo_error,
  ADD COLUMN ultimo_codigo_error VARCHAR(50) NULL AFTER ultimo_http_status;
