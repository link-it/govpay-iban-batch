package it.govpay.iban.batch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import it.govpay.iban.batch.entity.PagopaIbanCheckEntity;

@Repository
public interface PagopaIbanCheckRepository extends JpaRepository<PagopaIbanCheckEntity, Long> {
    /**
     * Delete all records from PAGOPA_IBAN_CHECK
     */
    @Modifying
    @Query("DELETE FROM PagopaIbanCheckEntity")
    void deleteAllRecords();

}
