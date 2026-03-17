package it.govpay.iban.batch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.govpay.iban.batch.entity.IbanAccreditoEntity;

@Repository
public interface IbanAccreditoRepository extends JpaRepository<IbanAccreditoEntity, Long> {

    List<IbanAccreditoEntity> findByCodIbanAndDominioCodDominio(String codIban, String codDominio);

}
