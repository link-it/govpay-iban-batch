# Release Notes

## 1.0.3 — 2026-09-14

Release di manutenzione: correzione della paginazione verso le API di backoffice pagoPA.

### Correzioni
- **`limit` fuori range**: il default di `govpay.batch.page-size` passa da `1000` a `100`. La specifica di backoffice pagoPA dichiara il parametro `limit` con `maximum: 100`, quindi `getBrokerIbans` rispondeva `400 Bad Request` (`getBrokerIbans.limit: must be less than or equal to 100`) facendo fallire lo step `ibanCheckAcquisitionStep` del job `ibanCheckJob`.
- **Prima pagina persa**: le pagine dell'API sono 0-based (`Page value starts from 0` sul parametro di query, `0 is the first page` su `PageInfo.page`), mentre il ciclo di `IbanPagopaApiService` partiva da 1. La pagina 0 non veniva mai richiesta: i primi `page-size` IBAN di ogni intermediario venivano scartati senza alcuna segnalazione e, con una sola pagina di risultati, la lista tornava vuota.
- **Chiamata fuori range in coda**: la condizione di uscita `page < totalPages`, valutata su un indice 1-based, faceva richiedere la pagina `totalPages` (inesistente in numerazione 0-based). Il ciclo esce ora su `currentPage + 1 < totalPages`, valutata sulla pagina richiesta e non su quella riportata in risposta, così da restare monotona e terminare anche se l'API non rimanda indietro fedelmente il numero di pagina.

### Compatibilità
Nessuna breaking change. Aggiornamento drop-in rispetto alla 1.0.2.

**Attenzione**: chi avesse sovrascritto `govpay.batch.page-size` con un valore superiore a 100 (via properties o variabile d'ambiente) deve riportarlo a un valore ≤ 100, altrimenti l'errore 400 si ripresenta nonostante l'aggiornamento.

**Nota sui dati**: dopo l'aggiornamento il primo giro del job acquisisce IBAN che le versioni precedenti non avevano mai letto, quindi è atteso un incremento delle righe elaborate rispetto alle esecuzioni storiche.

## 1.0.2 — 2026-05-12

Release di manutenzione: pulizia configurazione logging.

### Configurazione
- Rimosse le direttive `logging.level.*` da `application.properties`. La configurazione di logging non è più hard-coded e viene demandata al runtime (variabili d'ambiente, profili dedicati, configurazione esterna).
- Spostati i livelli `DEBUG` per `it.govpay.iban.batch` e `org.springframework.batch` nel nuovo profilo `application-dev.properties`.
- Il livello `root=INFO` è stato rimosso in quanto coincide con il default Spring Boot.

### Compatibilità
**Attenzione**: chi faceva affidamento sui livelli DEBUG implicitamente attivi deve ora abilitare il profilo `dev` (es. `-Dspring.profiles.active=dev`) o impostare i livelli via variabili d'ambiente / configurazione esterna.

## 1.0.1 — 2026-05-05

Prima release di manutenzione: aggiornamento dipendenze GovPay e potenziamento della pipeline di build/release.

### Aggiornamenti dipendenze
- `govpay-bom` aggiornato a **1.1.3** (parent BOM).
- `govpay-common` aggiornato a **1.1.2**.

### Pipeline
- **SBOM CycloneDX**: aggiunto job `sbom` che genera l'SBOM aggregato (formati `json` + `xml`, schema 1.6) tramite `cyclonedx-maven-plugin`. Eseguito su push su `main`/tag o su richiesta esplicita (`vars.FORCE_SBOM_JOB`); disattivabile con `vars.DISABLE_SBOM_JOB`. L'SBOM viene incluso nel ZIP `release-reports` sotto `reports/sbom/`.
- **OSV Scanner**: aggiunto job `osv-scan` (Google OSV Scanner) eseguito su `main`/tag con fallimento bloccante. Il report SARIF è incluso nel ZIP `release-reports` sotto `reports/osv/`.
- **Cache OWASP Dependency-Check**: chiave basata sulla data e flag `NOUPDATE_FLAG` per saltare l'aggiornamento NVD quando la cache è della stessa giornata.
- **Workflow `refresh-owasp-db`**: aggiornamento notturno della cache NVD per ridurre la latenza dei job di build.
- **Reports ZIP unico**: tutti i report (OWASP, JaCoCo, OSV, licenze, SBOM) collezionati in `release-reports-<tag>.zip` allegato alla GitHub Release.
- **Bump action GitHub**: `actions/upload-artifact` e `actions/download-artifact` portati a v7.

### Codice
- `GdeService`: aggiunto metodo `getConfigurazioneComponente` con delega a `GdeUtils`.
- Aggiunti script SQL di svecchiamento delle tabelle Spring Batch (`spring-batch-cleanup.sql`) per tutti i database supportati (PostgreSQL, MySQL, Oracle, SQL Server, HSQLDB).

### Compatibilità
Nessuna breaking change. Aggiornamento drop-in rispetto alla 1.0.0.
