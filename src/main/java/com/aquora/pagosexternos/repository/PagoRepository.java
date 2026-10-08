package com.aquora.pagosexternos.repository;

import com.aquora.pagosexternos.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    List<Pago> findByEstadoEnvio(String estadoEnvio);

    // Actualizacion atomica: solo marca como "en_cola" los pagos que SIGUEN en "pendiente".
    // Esto es lo que evita que, si el Producer corre dos veces casi al mismo tiempo
    // (o hay mas de una instancia corriendo), el mismo pago se publique dos veces en la cola.
    @Modifying
    @Transactional
    @Query("UPDATE Pago p SET p.estadoEnvio = 'en_cola' WHERE p.id = :id AND p.estadoEnvio = 'pendiente'")
    int marcarEnCola(@Param("id") Long id);
}
