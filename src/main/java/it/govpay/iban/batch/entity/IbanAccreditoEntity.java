package it.govpay.iban.batch.entity;

import it.govpay.common.entity.DominioEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "IBAN_ACCREDITO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IbanAccreditoEntity {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_dominio", nullable = false)
    private DominioEntity dominio;

	@Column(name = "cod_iban", length = 255)
	private String codIban;

	@Column(name = "bic_accredito", length = 255)
	private String bicAccredito;

	@Column(name = "postale")
	private Boolean postale;

	@Column(name = "abilitato")
	private Boolean abilitato;

	@Column(name = "descrizione", length = 1024)
	private String descrizione;

	@Column(name = "intestatario", length = 1024)
	private String intestatario;

	@Column(name = "aut_stampa_poste", length = 255)
	private String autStampaPoste;
}
