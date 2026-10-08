package com.aquora.pagosexternos.banco;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

// Simula 3 "bancos" o sistemas externos distintos (del diagrama: "Bancos").
// Mas adelante, si se requiere, cada metodo puede reemplazarse por una llamada HTTP real
// con RestTemplate/WebClient a una API de prueba. Por ahora solo simula exito/fallo al azar
// para poder probar todo el flujo de la cola sin depender de un servicio externo real.
@Slf4j
@Component
public class BancoSimulado {

    private final Random random = new Random();

    public boolean enviarABancoA(Long pagoId, Double monto) {
        return simularEnvio("Banco A", pagoId, monto);
    }

    public boolean enviarABancoB(Long pagoId, Double monto) {
        return simularEnvio("Banco B", pagoId, monto);
    }

    public boolean enviarABancoC(Long pagoId, Double monto) {
        return simularEnvio("Banco C", pagoId, monto);
    }

    private boolean simularEnvio(String banco, Long pagoId, Double monto) {
        try {
            Thread.sleep(300); // simula latencia de red
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        boolean exito = random.nextInt(10) < 8; // ~80% de exito, para poder ver tambien el caso de fallo
        log.info("[{}] intento de envio pago {} (monto {}) -> {}", banco, pagoId, monto, exito ? "EXITO" : "FALLO");
        return exito;
    }
}
