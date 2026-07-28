package it.govpay.iban.batch.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import it.govpay.iban.batch.entity.EnteCreditoreCacheEntity;

@Repository
public interface EnteCreditoreCacheRepository extends JpaRepository<EnteCreditoreCacheEntity, Long> {

    Optional<EnteCreditoreCacheEntity> findByCodFiscale(String codFiscale);

    @Modifying
    long deleteByDataUltimoAggiornamentoBefore(OffsetDateTime threshold);
}
