package com.aquora.pagosexternos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Mapea la tabla `pagos` que ya existe en AQUORA.
// Solo se agregan los 3 campos nuevos de la migracion (estado_envio, fecha_envio, intentos_envio).
// No se tocan ni se borran los campos que ya usaba AQUORA.
@Entity
@Table(name = "pagos")
@Getter
@Setter
public class Pago {

    @Id
    private Long id;

    @Column(name = "lectura_id")
    private Long lecturaId;

    private Double monto;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    private String metodo;

    @Column(name = "usuario_registro_id")
    private Long usuarioRegistroId;

    // --- columnas nuevas agregadas por el script sql/agregar_columna_envio.sql ---
    @Column(name = "estado_envio")
    private String estadoEnvio; // pendiente | en_cola | enviado | fallido

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;

    @Column(name = "intentos_envio")
    private Integer intentosEnvio;
}
