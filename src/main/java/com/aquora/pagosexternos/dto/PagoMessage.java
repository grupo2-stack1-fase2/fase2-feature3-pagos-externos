package com.aquora.pagosexternos.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Esto es lo que viaja dentro de la cola (el mensaje), no el objeto Pago completo.
// Se manda solo lo que el Consumer realmente necesita para "enviar" el pago afuera.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PagoMessage {
    private Long pagoId;
    private Double monto;
    private String metodo;
}
