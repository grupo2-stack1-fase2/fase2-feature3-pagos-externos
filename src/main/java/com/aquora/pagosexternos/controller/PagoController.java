package com.aquora.pagosexternos.controller;

import com.aquora.pagosexternos.model.Pago;
import com.aquora.pagosexternos.producer.PagoProducer;
import com.aquora.pagosexternos.repository.PagoRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pagos-externos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoProducer pagoProducer;
    private final PagoRepository pagoRepository;

    @Operation(summary = "Dispara manualmente la revision de pagos pendientes, sin esperar los 30s del scheduler")
    @PostMapping("/procesar")
    public String procesarAhora() {
        pagoProducer.revisarPagosPendientes();
        return "Revision de pagos pendientes ejecutada.";
    }

    @Operation(summary = "Lista el estado de envio de todos los pagos (pendiente / en_cola / enviado / fallido)")
    @GetMapping("/estado")
    public List<Pago> verEstado() {
        return pagoRepository.findAll();
    }

    @Operation(summary = "Lista solo los pagos en un estado especifico, ej: /estado/pendiente")
    @GetMapping("/estado/{estado}")
    public List<Pago> verEstadoFiltrado(@PathVariable String estado) {
        return pagoRepository.findByEstadoEnvio(estado);
    }
}
