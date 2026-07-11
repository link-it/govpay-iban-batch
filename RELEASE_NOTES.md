# Release Notes

## 2.0.0 — 2026-07-11

Release maggiore: migrazione a **Spring Boot 4.1 / Spring Framework 7**. Comporta breaking change (Jackson 3, Spring Batch 6, Hibernate 7): vedere la sezione *Compatibilità*.

### Aggiornamenti dipendenze
- `govpay-bom` aggiornato a **2.0.1** (parent BOM).
- `govpay-common` aggiornato a **2.0.0**.
- Stack di runtime: Spring Boot **4.1.0**, Spring Framework **7.0.8**, Spring Batch **6.0.4**, Hibernate **7.4.1**, Jackson **3.1.4**, JUnit **6.0.3** (Java 21).

### Jackson 2 → Jackson 3
- Migrazione al namespace `tools.jackson.*` (le annotazioni restano su `com.fasterxml.jackson.annotation`).
- Gli `ObjectMapper` custom (`WebConfig`, `IbanPagopaApiClientConfig`) sono ora costruiti tramite `JsonMapper.builder()`, dato che in Jackson 3 l'`ObjectMapper` è immutabile.
- Le feature enum/date sono passate a `EnumFeature` / `DateTimeFeature` (`READ/WRITE_ENUMS_USING_TO_STRING`, `WRITE_DATES_WITH_ZONE_ID`, `WRITE_DATES_AS_TIMESTAMPS`).
- Il supporto `java.time` è integrato e auto-registrato: rimossa la dipendenza `jackson-datatype-jsr310`.

### Spring Framework 7
- Sostituito `MappingJackson2HttpMessageConverter` con `JacksonJsonHttpMessageConverter` (converter Jackson 3) in `IbanPagopaApiService`.

### Spring Batch 6
- Aggiornati i package riorganizzati: `core.job.*`, `core.step.*`, `core.job.parameters.*`, `core.listener.*`, `core.partition.Partitioner`, eccezioni in `core.launch.*`, `infrastructure.item.*`, `infrastructure.repeat.*`.
- `JobConcurrencyService` usa solo `JobRepository`; `JobExecutionHelper` e `AbstractBatchController` (govpay-common) usano `JobOperator` / `JobRepository`.
- `JobExecution.getJobId()` → `getJobInstanceId()`; nuovo costruttore `JobExecution(long, JobInstance, JobParameters)`.

### Spring Boot 4
- `@EntityScan` spostato in `org.springframework.boot.persistence.autoconfigure`.
- Il bean `taskExecutor` è stato spostato in `BatchInfraConfig` per evitare un ciclo di creazione bean: in Boot 4 la `entityManagerFactoryBuilder` inietta la mappa di tutti i bean `AsyncTaskExecutor`, forzandone l'istanziazione durante il setup JPA.

### Build
- OpenAPI Generator: attivati `useSpringBoot4` e `useJackson3`; versione del plugin allineata a quella del BOM (`openapi.tool.codegen.version`).
- Hibernate 7: sostituito `hibernate-jpamodelgen` con `org.hibernate.orm:hibernate-processor` (dipendenza e annotation processor path).

### Compatibilità
**Breaking change.** Aggiornamento non drop-in rispetto alla 1.x:
- Richiede un runtime con stack Spring Boot 4 / Jackson 3; eventuali estensioni o integrazioni che dipendono dal namespace `com.fasterxml.jackson.databind`/`core` o dai package Spring Batch 5 vanno adeguate.
- `govpay-common` **2.0.0** (Spring Boot 4) è obbligatorio: le versioni 1.x non sono compatibili.
- Nessuna modifica funzionale al flusso del batch né allo schema del database.

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
