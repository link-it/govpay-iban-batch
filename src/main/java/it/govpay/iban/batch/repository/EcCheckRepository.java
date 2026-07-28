package it.govpay.iban.batch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import it.govpay.iban.batch.entity.EcCheckEntity;

@Repository
public interface EcCheckRepository extends JpaRepository<EcCheckEntity, Long> {
    /**
     * Delete all records from PAGOPA_EC_CHECK
     */
    @Modifying
    @Query("DELETE FROM EcCheckEntity")
    void deleteAllRecords();
}
