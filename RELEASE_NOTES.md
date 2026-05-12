# Release Notes

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
