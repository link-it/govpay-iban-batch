package it.govpay.iban.batch.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import it.govpay.iban.batch.entity.IbanCacheEntity;

@Repository
public interface IbanCacheRepository extends JpaRepository<IbanCacheEntity, Long> {

    Optional<IbanCacheEntity> findByCodDominioAndIban(String codDominio, String iban);

    @Modifying
    long deleteByDataUltimaVerificaBefore(OffsetDateTime threshold);
}
