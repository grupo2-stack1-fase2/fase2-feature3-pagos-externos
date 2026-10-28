package com.aquora.pagosexternos.repository;

import com.aquora.pagosexternos.model.EnvioDestino;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnvioDestinoRepository extends JpaRepository<EnvioDestino, Long> {

    List<EnvioDestino> findByPagoId(Long pagoId);

    Optional<EnvioDestino> findByPagoIdAndDestino(Long pagoId, String destino);
}
