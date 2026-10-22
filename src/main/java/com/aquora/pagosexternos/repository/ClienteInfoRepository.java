package com.aquora.pagosexternos.repository;

import com.aquora.pagosexternos.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Consulta de solo lectura para traer el nombre real del cliente dueno de un
 * pago (via lecturas -> contadores -> clientes), necesario para
 * payer.fullName del contrato del banco. No modifica nada de esas tablas.
 */
public interface ClienteInfoRepository extends JpaRepository<Pago, Long> {

    @Query(value = "SELECT cl.nombre " +
            "FROM pagos p " +
            "JOIN lecturas l ON l.id = p.lectura_id " +
            "JOIN contadores c ON c.id = l.contador_id " +
            "JOIN clientes cl ON cl.id = c.cliente_id " +
            "WHERE p.id = :pagoId",
            nativeQuery = true)
    Optional<String> buscarNombreClientePorPago(@Param("pagoId") Long pagoId);
}
