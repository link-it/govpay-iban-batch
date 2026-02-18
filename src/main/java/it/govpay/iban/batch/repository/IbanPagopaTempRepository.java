package it.govpay.iban.batch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import it.govpay.iban.batch.entity.IbanPagopaTempEntity;

@Repository
public interface IbanPagopaTempRepository extends JpaRepository<IbanPagopaTempEntity, Long> {
    /**
     * Delete all records from IBAN_PAGOPA_TEMP
     */
    @Modifying
    @Query("DELETE FROM IbanPagopaTempEntity")
    void deleteAllRecords();

}
