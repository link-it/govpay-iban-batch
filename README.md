<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - IBAN Batch

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-iban-batch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-iban-batch)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-iban-batch?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-iban-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-iban-batch/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Flink-it%2Fgovpay-bom%2Fmain%2Fpom.xml&query=%2F%2F*%5Blocal-name()%3D'spring-boot.version'%5D&label=Spring%20Boot&color=brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

Batch Spring Boot che confronta gli IBAN e l'anagrafica degli Enti Creditori registrati su pagoPA con quelli censiti in GovPay, aggiorna la cache usata dalla console per il censimento assistito e produce un report CSV per intermediario, inviabile via mail.

## Sommario

Il batch esegue un solo job Spring Batch, `ibanCheckJob`, sul database di GovPay. Per ogni intermediario che ha il connettore di backoffice EC configurato interroga l'API di backoffice di pagoPA per gli Enti Creditori (*Backoffice External EC*) e, in quattro step:

| Step | Cosa fa |
|------|---------|
| `ibanCheckAcquisitionStep` | Legge gli IBAN dell'intermediario (`GET /brokers/{brokerCode}/ibans`), li confronta con `iban_accredito` (abilitazione, descrizione, intestatario), aggiorna `pagopa_iban_cache` e scrive il report CSV dell'intermediario. Gli intermediari sono elaborati in parallelo, uno per partizione |
| `cleanupStaleIbanCacheStep` | Cancella da `pagopa_iban_cache` gli IBAN che pagoPA non ha più restituito. Gira solo se lo step precedente è terminato correttamente, così un disservizio di pagoPA non svuota la cache |
| `ecSyncAcquisitionStep` | Legge gli Enti Creditori dell'intermediario (`GET /brokers/{brokerCode}/creditor_institutions`), li confronta con `domini` (ragione sociale, aux digit, segregation code, CBILL, stazione) e aggiorna `pagopa_ec_cache` |
| `cleanupStaleEntiCreditoriStep` | Cancella da `pagopa_ec_cache` gli Enti Creditori che pagoPA non ha più restituito, solo se lo step precedente è terminato correttamente |

Ogni IBAN e ogni Ente Creditore riceve un esito (`checkStato`): `OK`, `NON_CENSITO` (assente in GovPay), `INFO_DIVERSE` (dati diversi, elencati in `checkMotivo`) o, solo per gli IBAN, `NON_ATTIVO`. L'esito è salvato nelle due tabelle di cache, che la [console](https://github.com/link-it/govpay-console-api) legge per mostrare i dati pagoPA e proporre il censimento di IBAN e domini. A fine job il batch può inviare i report via mail (vedi [Report e notifica via mail](#report-e-notifica-via-mail)).

Il job si avvia da uno scheduler interno (modalità di default), una sola volta con il profilo `cron`, oppure a richiesta con l'API REST descritta in [Avvio manuale via REST](#avvio-manuale-via-rest), che la console usa per l'operazione `IBAN_CHECK`. Le esecuzioni sono coordinate tra più nodi tramite i metadati Spring Batch sul database.

Storico delle modifiche nel [ChangeLog](ChangeLog).

## Integrazione con GovPay

| Componente | Quando serve | Obbligatorio | Ruolo |
|------------|--------------|--------------|-------|
| Database di GovPay | runtime | sì | Legge `intermediari` (connettore di backoffice EC), `connettori`, `iban_accredito`, `domini`, `stazioni` e `configurazione`. Le tabelle sono create dagli script di installazione di GovPay, il batch non crea lo schema e non modifica le anagrafiche |
| Tabelle `pagopa_iban_cache` e `pagopa_ec_cache` | runtime | sì | Cache scritte dal batch e lette dalla console. Sono di [govpay-console-api](https://github.com/link-it/govpay-console-api), che le crea con lo script `console-api-schema.sql` del suo `sql.zip` (con le sequence `seq_pagopa_iban_cache` e `seq_pagopa_ec_cache`) |
| Tabelle Spring Batch | runtime | sì | Metadati delle esecuzioni (`batch_job_*`, `batch_step_*`), usati anche per evitare esecuzioni concorrenti tra nodi. Lo schema è nel pacchetto `sql.zip` (vedi [Compilazione](#compilazione)) e l'immagine Docker può crearlo all'avvio |
| [govpay-bom](https://github.com/link-it/govpay-bom) | build | sì | Parent Maven: versioni delle dipendenze e dei plugin, profilo `dist` |
| [govpay-common](https://github.com/link-it/govpay-common) | build | sì | Libreria comune: connettori, entità di anagrafica, coordinamento dei job, API REST del batch, client GDE, invio mail, transaction id e livelli di log da database |
| API di backoffice pagoPA per gli Enti Creditori | runtime | sì | Fonte di IBAN e anagrafica degli Enti Creditori. URL e autenticazione (es. subscription key) sono nel connettore indicato dal campo `cod_connettore_backoffice_ec` dell'intermediario; gli intermediari senza connettore sono saltati con un avviso nel log |
| Server SMTP | runtime | no | Invio dei report, se `govpay.report.invia_mail=true`. Il server è quello della configurazione `mail_batch` di GovPay (tabella `configurazione`) |
| [govpay-gde-api](https://github.com/link-it/govpay-gde-api) | runtime | no | Riceve gli eventi delle chiamate a pagoPA (`getAllIbans`, `getBrokerInstitutions`) con `POST /eventi`, se il connettore `govpay_gde_api` è abilitato in GovPay. Un GDE non raggiungibile non blocca il batch |
| [govpay-console-api](https://github.com/link-it/govpay-console-api) | runtime | no | Legge le due cache e avvia o consulta il batch tramite l'API REST, come operazione `IBAN_CHECK` del catalogo `govpay.operazioni.catalogo` (di default `http://govpay-iban-batch:10001/api/batch`, la porta del container) |

Le modifiche ai livelli di log fatte dalla console (riga `log_level` della tabella `configurazione`) raggiungono il batch entro l'intervallo di refresh, senza riavvio.

## Compilazione

Requisiti: JDK 21 e Maven 3.8.1 o successivo.

```bash
mvn clean install -P jar     # fat jar, per esecuzione standalone e container (profilo di default)
mvn clean install -P war     # war, per il deploy su application server
```

Il profilo sceglie il packaging; il risultato è in `target/govpay-iban-batch.jar` o `target/govpay-iban-batch.war`. In entrambi i casi i driver JDBC non sono inclusi. La build esegue i test e l'analisi OWASP Dependency-Check delle dipendenze; per escludere l'analisi OWASP, per esempio in sviluppo:

```bash
mvn clean install -P jar -Dowasp.phase=none
```

Per produrre anche `target/sql.zip`, con lo schema Spring Batch per ogni database e gli script di svecchiamento dei metadati, usato dall'immagine Docker e allegato alle release:

```bash
mvn clean install -Pjar,dist
```

Il profilo `jar` va indicato insieme a `dist`: è quello che lascia i driver JDBC fuori dal jar.

## Configurazione

Le proprietà si impostano in `application.properties`, da riga di comando (`-D<proprietà>=<valore>`) o con le variabili d'ambiente equivalenti di Spring Boot (es. `govpay.report.dir` → `GOVPAY_REPORT_DIR`, `govpay.batch.thread-pool-size` → `GOVPAY_BATCH_THREADPOOLSIZE`, `scheduler.ibanCheckJob.fixedDelayString` → `SCHEDULER_IBANCHECKJOB_FIXEDDELAYSTRING`). Con l'immagine Docker si usano anche le variabili descritte in [Esecuzione con Docker](#esecuzione-con-docker).

### Job e scheduler

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `scheduler.ibanCheckJob.fixedDelayString` | `7200000` | Intervallo tra la fine di un'esecuzione e l'avvio della successiva, in ms (2 ore) |
| `scheduler.initialDelayString` | `1` | Ritardo della prima esecuzione dopo l'avvio, in ms |
| `govpay.batch.thread-pool-size` | `5` | Intermediari elaborati in parallelo negli step partizionati |
| `govpay.batch.check-iban-chunk-size` | `1` | IBAN elaborati per transazione |
| `govpay.batch.ec-sync-chunk-size` | `50` | Enti Creditori elaborati per transazione |
| `govpay.batch.page-size` | `100` | Elementi per pagina chiesti all'API di pagoPA (l'API accetta al massimo 100) |

Le chiamate a pagoPA non vengono ritentate: se la lettura di un intermediario fallisce, la sua partizione fallisce e i dati vengono ripresi all'esecuzione successiva.
| `govpay.batch.enabled` | `true` | Letta in `BatchProperties` ma non usata: per non schedulare il job usare il profilo `cron` o un profilo diverso da `default` |

### Report e notifica via mail

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.report.dir` | `/tmp` | Directory dei report CSV; deve esistere ed essere scrivibile |
| `govpay.report.invia_mail` | `false` | Invia i report via mail a fine job |
| `govpay.report.destinatario` | - | Destinatari della mail, separati da virgola (obbligatoria con l'invio abilitato) |
| `govpay.report.oggetto` | `Report Check IBAN` | Oggetto della mail |
| `govpay.report.messaggio` | `In allegato il report di verifica IBAN.` | Testo iniziale della mail, seguito dai conteggi per esito |

### Esecuzione e cluster

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.batch.cluster-id` | `GovPay-iban-Batch` | Identificativo del nodo, registrato nelle esecuzioni e negli eventi GDE; nel container è l'indirizzo IP |
| `govpay.batch.stale-threshold-minutes` | `120` | Minuti senza aggiornamenti dopo cui un'esecuzione in corso è considerata bloccata e può essere superata |
| `govpay.batch.job-repository.jdbc.enabled` | `true` | Metadati Spring Batch su database (da `govpay-common`); senza, il coordinamento tra nodi non funziona |
| `spring.batch.job.enabled` | `false` | Avvio automatico dei job da parte di Spring Boot: resta `false`, il job è avviato dallo scheduler o dal profilo `cron` |
| `spring.profiles.active` | `default` | `cron` per eseguire il job una volta e terminare (vedi [Esecuzione](#esecuzione)) |
| `server.port` | `8080` (`10001` nel container) | Porta HTTP dell'API REST del batch e degli endpoint actuator |
| `spring.application.name` | `govpay-iban-batch` | Nome dell'applicazione nei log |
| `spring.main.banner-mode` | `off` | Banner di Spring Boot all'avvio |
| `spring.jackson.default-property-inclusion` | `non_null` | Esclude i campi nulli dal JSON |
| `spring.jackson.time-zone` | `Europe/Rome` | Fuso orario dell'applicazione, delle date salvate nelle cache e di quelle scambiate con pagoPA |

### Connettori e client HTTP

URL e autenticazione dell'API di pagoPA e del GDE non sono proprietà del batch: sono nei connettori di GovPay (tabella `connettori`), indicati dal campo `cod_connettore_backoffice_ec` dell'intermediario e, per il GDE, dal connettore `govpay_gde_api`.

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.client.cache.enabled` | `false` | Carica i connettori all'avvio e li tiene in cache; la cache si svuota con `GET /api/batch/cache/clear` |
| `govpay.client.async.core-pool-size` | `10` | Thread minimi per l'invio asincrono degli eventi al GDE |
| `govpay.client.async.max-pool-size` | `50` | Thread massimi per l'invio asincrono degli eventi al GDE |
| `govpay.client.async.queue-capacity` | `100` | Eventi in coda in attesa di invio |
| `govpay.client.async.thread-name-prefix` | `async-http-` | Prefisso dei thread di invio |

### Database

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `spring.datasource.url` | - | URL JDBC del database di GovPay (obbligatoria) |
| `spring.datasource.username` | - | Utente del database |
| `spring.datasource.password` | - | Password del database |
| `spring.datasource.jndi-name` | - | Nome JNDI del datasource, per il deploy del war su application server (es. `java:comp/env/jdbc/govpay`); se impostata, le altre proprietà `spring.datasource.*` sono ignorate |
| `spring.datasource.driver-class-name` | dedotto dall'URL | Classe del driver JDBC |
| `spring.jpa.database-platform` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.properties.hibernate.dialect` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.hibernate.ddl-auto` | `none` (`create-drop` con database embedded) | Gestione dello schema: lasciare `none` o `validate`, lo schema è di GovPay |
| `spring.datasource.hikari.minimum-idle` | `10` (`2` nel container) | Connessioni minime inattive del pool |
| `spring.datasource.hikari.maximum-pool-size` | `10` | Connessioni massime del pool; con più intermediari in parallelo tenerla sopra `govpay.batch.thread-pool-size` |
| `spring.datasource.hikari.connection-timeout` | `30000` (`20000` nel container) | Attesa massima di una connessione, in ms |
| `spring.datasource.hikari.idle-timeout` | `600000` (`10000` nel container) | Tempo dopo cui una connessione inattiva viene chiusa, in ms |
| `spring.datasource.hikari.max-lifetime` | `1800000` (`600000` nel container) | Durata massima di una connessione, in ms |

I driver JDBC non sono inclusi né nel jar né nel war: vedi [Esecuzione](#esecuzione).

### Logging

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `logging.file.name` | - (solo console) | File di log; nel container `/var/log/govpay-iban/govpay-iban.log` |
| `logging.level.it.govpay` | `INFO` | Livello di log dei package GovPay |
| `logging.pattern.level` | `%5p [%X{transactionId:-},%X{correlationId:-}]` | Aggiunge a ogni riga transaction id e correlation id dell'esecuzione: il correlation id è quello della richiesta di avvio manuale, oppure generato per gli avvii schedulati |
| `govpay.logging.livelli-dinamici.enabled` | `true` | Livelli di log letti dalla riga `log_level` della tabella `configurazione` |
| `govpay.logging.livelli-dinamici.intervallo-refresh` | `60s` (variabile `LOG_LEVEL_REFRESH_INTERVAL`) | Intervallo di rilettura dei livelli dal database; `0` li legge solo all'avvio |
| `govpay.logging.livelli-dinamici.logger-gestiti[0]` | `it.govpay` | Prefissi dei logger modificabili da database |

Il profilo `dev` (`application-dev.properties`) porta a `DEBUG` i log di `it.govpay.iban.batch` e di Spring Batch.

### Monitoraggio

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `management.server.port` | porta del servizio | Porta dedicata agli endpoint actuator |
| `management.endpoints.web.base-path` | `/actuator` | Base path degli endpoint actuator |
| `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus` (variabile `MANAGEMENT_ENDPOINTS`) | Endpoint actuator esposti |
| `management.endpoint.health.show-details` | `when_authorized` | Dettagli dell'health: il servizio non ha autenticazione, quindi restituisce solo lo stato |
| `management.health.db.enabled` | `true` | Include la connessione al database nell'health |
| `management.metrics.tags.application` | `govpay-iban-batch` | Tag che identifica il servizio nelle metriche |

## Esecuzione

### Driver JDBC

I driver JDBC **non sono inclusi** nel fat jar e vanno forniti a runtime. Creare una directory (es. `jdbc-drivers/`) e copiarvi il driver del database utilizzato (versioni gestite da `govpay-bom`):

| Database   | Driver |
|------------|--------|
| PostgreSQL | [postgresql-42.7.14.jar](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar) |
| MySQL      | [mysql-connector-j-26.10.0.jar](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/26.10.0/mysql-connector-j-26.10.0.jar) |
| Oracle     | [ojdbc11-23.26.3.0.0.jar](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.3.0.0/ojdbc11-23.26.3.0.0.jar) |
| SQL Server | [mssql-jdbc-13.6.1.jre11.jar](https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/13.6.1.jre11/mssql-jdbc-13.6.1.jre11.jar) |
| H2         | [h2-2.5.252.jar](https://repo1.maven.org/maven2/com/h2database/h2/2.5.252/h2-2.5.252.jar) |

### Schema del database

Prima del primo avvio, nel database di GovPay devono esistere:

- le tabelle dei metadati Spring Batch: si creano con lo script `sql/spring-batch/schema-<database>.sql` di `sql.zip`. L'immagine Docker lo esegue da sola se `GOVPAY_IBAN_POP_DB_SKIP=FALSE`. Gli script in `sql/cleanup/` cancellano i metadati delle esecuzioni vecchie e vanno pianificati periodicamente;
- le tabelle `pagopa_iban_cache` e `pagopa_ec_cache`, create dallo script `console-api-schema.sql` di [govpay-console-api](https://github.com/link-it/govpay-console-api).

### Jar standalone

Il jar usa `PropertiesLauncher`: la proprietà `loader.path` indica la directory dei driver JDBC. Configurazione minima per PostgreSQL, con lo scheduler interno:

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -Dgovpay.report.dir=/var/govpay-iban/report \
     -jar target/govpay-iban-batch.jar
```

Modalità di esecuzione:

| Modalità | Come si attiva | Comportamento |
|----------|----------------|---------------|
| Scheduler interno | default (nessun profilo) | Il processo resta attivo, esegue `ibanCheckJob` ogni `scheduler.ibanCheckJob.fixedDelayString` ms ed espone API REST e actuator |
| Esecuzione singola | `--spring.profiles.active=cron` | Esegue `ibanCheckJob` una volta e termina: la pianificazione è affidata a un cron esterno. L'exit code è `0` anche se il job fallisce o è già in esecuzione su un altro nodo, `1` solo se non si riesce a superare un'esecuzione bloccata: l'esito va letto nei log |

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -Dgovpay.report.dir=/var/govpay-iban/report \
     -jar target/govpay-iban-batch.jar --spring.profiles.active=cron
```

### War su application server

Il war si produce con `mvn clean install -P war` (`target/govpay-iban-batch.war`): non è allegato alle release né incluso nell'immagine Docker. Richiede un application server Jakarta EE 11 (Servlet 6.1), per esempio Tomcat 11.

Il war usa il datasource dell'application server, indicato con `spring.datasource.jndi-name`; il driver JDBC va nelle librerie dell'application server. Le altre proprietà si passano come proprietà di sistema della JVM o variabili d'ambiente. Esempio per Tomcat, con il datasource definito in `conf/context.xml`:

```xml
<Resource name="jdbc/govpay" auth="Container" type="javax.sql.DataSource"
          driverClassName="org.postgresql.Driver"
          url="jdbc:postgresql://localhost:5432/govpay"
          username="govpay" password="<password>" maxTotal="10"/>
```

```bash
export CATALINA_OPTS="-Dspring.datasource.jndi-name=java:comp/env/jdbc/govpay -Dgovpay.report.dir=/var/govpay-iban/report"
cp target/govpay-iban-batch.war $CATALINA_BASE/webapps/
```

Il batch gira con lo scheduler interno; API REST e actuator rispondono sotto il context path del war (es. `http://localhost:8080/govpay-iban-batch/actuator/health`).

## Esecuzione con Docker

L'immagine è pubblicata su Docker Hub come [linkitaly/govpay-iban-batch](https://hub.docker.com/r/linkitaly/govpay-iban-batch) (release) e `linkitaly/govpay-iban-batch-dev` (sviluppo, a ogni push su `main`). Con lo scheduler interno il servizio risponde sulla porta `10001`; i driver JDBC si montano in `/opt/jdbc-drivers`. I report CSV vanno scritti su un volume, altrimenti si perdono al riavvio del container.

```bash
docker run -d --name govpay-iban-batch -p 10001:10001 \
  -v ./jdbc-drivers:/opt/jdbc-drivers \
  -v ./report:/var/govpay-iban/report \
  -e GOVPAY_DB_TYPE=postgresql \
  -e GOVPAY_DB_SERVER=postgres:5432 \
  -e GOVPAY_DB_NAME=govpay \
  -e GOVPAY_DB_USER=govpay \
  -e GOVPAY_DB_PASSWORD=<password> \
  -e GOVPAY_REPORT_DIR=/var/govpay-iban/report \
  linkitaly/govpay-iban-batch
```

La directory montata deve essere scrivibile dall'utente `govpay` del container.

Variabili d'ambiente dell'immagine, oltre alle proprietà Spring Boot in forma di variabile (es. `GOVPAY_REPORT_INVIA_MAIL=true`, `GOVPAY_REPORT_DESTINATARIO=operatore@ente.it`, `SCHEDULER_IBANCHECKJOB_FIXEDDELAYSTRING=3600000`):

| Variabile | Obbligatoria | Default | Descrizione |
|-----------|--------------|---------|-------------|
| `GOVPAY_DB_TYPE` | sì | - | Tipo di database: `postgresql`, `mysql`, `mariadb`, `oracle` |
| `GOVPAY_DB_SERVER` | sì | - | Server del database, `host` o `host:porta` (porta di default del database se omessa) |
| `GOVPAY_DB_NAME` | sì | - | Nome del database (per Oracle, service name o SID) |
| `GOVPAY_DB_USER` | sì | - | Utente del database |
| `GOVPAY_DB_PASSWORD` | sì | - | Password del database (se manca l'avvio prosegue con un avviso) |
| `GOVPAY_DS_CONN_PARAM` | no | - | Parametri aggiunti all'URL JDBC (es. `zeroDateTimeBehavior=convertToNull` per MySQL) |
| `GOVPAY_DS_JDBC_LIBS` | no | `/opt/jdbc-drivers` | Directory dei driver JDBC |
| `GOVPAY_ORACLE_JDBC_URL_TYPE` | no | `servicename` | Formato dell'URL Oracle: `servicename` o `sid` |
| `ORACLE_TNS_ADMIN` | no | - | Directory dei file TNS di Oracle |
| `GOVPAY_IBAN_MIN_POOL` | no | `2` | Connessioni minime inattive del pool |
| `GOVPAY_IBAN_MAX_POOL` | no | `10` | Connessioni massime del pool |
| `GOVPAY_IBAN_BATCH_USA_CRON` | no | - | `true`, `si`, `yes` o `1`: esecuzione singola con il profilo `cron`, senza server HTTP; il container termina a fine job e va pianificato dall'esterno (es. CronJob Kubernetes). Altrimenti scheduler interno |
| `GOVPAY_IBAN_BATCH_INTERVALLO_CRON` | no | `120` | Minuti tra un'esecuzione e l'altra con lo scheduler interno; un valore non numerico o zero blocca l'avvio |
| `SCHEDULER_IBANCHECKJOB_FIXEDDELAYSTRING` | no | da `GOVPAY_IBAN_BATCH_INTERVALLO_CRON` | Intervallo in millisecondi (`scheduler.ibanCheckJob.fixedDelayString`); se impostata ha la precedenza e `GOVPAY_IBAN_BATCH_INTERVALLO_CRON` viene ignorata |
| `SERVER_PORT` | no | `10001` | Porta HTTP con lo scheduler interno |
| `MANAGEMENT_SERVER_PORT` | no | porta del servizio | Porta dedicata agli endpoint actuator |
| `MANAGEMENT_ENDPOINTS` | no | `health,info,metrics,prometheus` | Endpoint actuator esposti |
| `LOG_LEVEL_REFRESH_INTERVAL` | no | `60s` | Intervallo di rilettura dei livelli di log dal database |
| `GOVPAY_IBAN_POP_DB_SKIP` | no | `TRUE` | `FALSE` crea all'avvio le tabelle Spring Batch, se la tabella `GOVPAY_IBAN_DB_CHECK_TABLE` non esiste. Le tabelle di cache non sono create |
| `GOVPAY_IBAN_DB_CHECK_TABLE` | no | `batch_job_execution_context` | Tabella che indica che lo schema Spring Batch è già presente |
| `GOVPAY_IBAN_LIVE_DB_CHECK_SKIP` | no | `FALSE` | Salta l'attesa della porta del database prima dell'inizializzazione |
| `GOVPAY_IBAN_LIVE_DB_CHECK_MAX_RETRY` | no | `30` | Tentativi di connessione alla porta del database |
| `GOVPAY_IBAN_LIVE_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_IBAN_LIVE_DB_CHECK_CONNECT_TIMEOUT` | no | `5` | Timeout di ogni tentativo di connessione, in secondi |
| `GOVPAY_IBAN_READY_DB_CHECK_SKIP` | no | `FALSE` | Salta la verifica della tabella e crea comunque lo schema |
| `GOVPAY_IBAN_READY_DB_CHECK_MAX_RETRY` | no | `5` | Tentativi della verifica della tabella |
| `GOVPAY_IBAN_READY_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_IBAN_JVM_MAX_RAM_PERCENTAGE` | no | `80` | `-XX:MaxRAMPercentage` della JVM |
| `GOVPAY_IBAN_JVM_INITIAL_RAM_PERCENTAGE` | no | - | `-XX:InitialRAMPercentage` della JVM |
| `GOVPAY_IBAN_JVM_MIN_RAM_PERCENTAGE` | no | - | `-XX:MinRAMPercentage` della JVM |
| `GOVPAY_IBAN_JVM_MAX_METASPACE_SIZE` | no | - | `-XX:MaxMetaspaceSize` della JVM |
| `GOVPAY_IBAN_JVM_MAX_DIRECT_MEMORY_SIZE` | no | - | `-XX:MaxDirectMemorySize` della JVM |
| `JAVA_OPTS` | no | - | Opzioni aggiuntive della JVM |

L'identificativo del nodo (`govpay.batch.cluster-id`) è impostato dall'entrypoint, con la variabile `GOVPAY_BATCH_CLUSTER_ID`, all'indirizzo IP del container o al suo hostname se l'IP non si ricava: un valore passato dall'esterno viene sovrascritto. Le altre variabili che compaiono negli script dell'immagine (es. `INTERVALLO_MINUTI`, `HSQLDB_SHA256`) sono interne e non vanno impostate. `GOVPAY_IBAN_GDE_URL`, citata in versioni precedenti della documentazione, non ha effetto: l'invio al GDE si configura con il connettore `govpay_gde_api` di GovPay e, se la variabile è impostata, l'entrypoint lo segnala con un avviso. Il log di avvio dell'entrypoint è nello standard output del container (`docker logs`).

Costruzione dell'immagine in locale, scansione delle vulnerabilità e pacchetti inclusi sono descritti in [docker/DOCKER.md](docker/DOCKER.md) e [docker/SCANSIONE.md](docker/SCANSIONE.md).

## Monitoraggio

Con lo scheduler interno gli endpoint actuator rispondono di default sulla porta del servizio (`8080` con il jar, `10001` nel container; con il war, sotto il context path dell'applicazione):

```bash
curl http://localhost:10001/actuator/health       # stato, compresa la connessione al database
curl http://localhost:10001/actuator/prometheus   # metriche in formato Prometheus
```

Per spostarli su una porta dedicata impostare `management.server.port` (nel container `MANAGEMENT_SERVER_PORT`). L'endpoint `loggers`, che permette di cambiare i livelli di log a caldo, non è esposto di default perché il servizio non ha autenticazione: abilitarlo solo insieme a una porta dedicata e non raggiungibile dall'esterno, per esempio `MANAGEMENT_SERVER_PORT=9090` e `MANAGEMENT_ENDPOINTS=health,info,metrics,prometheus,loggers`. In alternativa i livelli si cambiano dalla console, tramite la tabella `configurazione`.

Con il profilo `cron` non c'è server HTTP: l'esito si ricava dai log.

## Avvio manuale via REST

Con lo scheduler interno il batch espone, sotto `/api/batch`, l'API comune ai batch GovPay (`AbstractBatchController` di `govpay-common`). È l'API che govpay-console-api chiama per l'operazione `IBAN_CHECK`. Non ha autenticazione: va esposta solo sulla rete interna.

| Metodo e path | Descrizione |
|---------------|-------------|
| `GET /api/batch/run` | Avvia `ibanCheckJob` in modo asincrono (`202`, oppure `409` se è già in esecuzione) |
| `GET /api/batch/run?force=true` | Avvia il job anche se risulta un'esecuzione in corso |
| `GET /api/batch/status` | Stato corrente del job |
| `GET /api/batch/lastExecution` | Ultima esecuzione completata |
| `GET /api/batch/nextExecution` | Prossima esecuzione stimata |
| `GET /api/batch/info` | Nome e descrizione del batch |
| `GET /api/batch/executions` | Elenco paginato delle esecuzioni |
| `GET /api/batch/executions/{executionId}` | Dettaglio di un'esecuzione |
| `DELETE /api/batch/executions/{executionId}` | Richiede l'arresto di un'esecuzione (`202`; `409` se non è in corso, `404` se non esiste) |
| `GET /api/batch/cache/clear` | Svuota la cache dei connettori e dei client verso pagoPA |

```bash
curl http://localhost:10001/api/batch/run
curl http://localhost:10001/api/batch/status
```

## Report e notifica via mail

A ogni esecuzione il batch scrive in `govpay.report.dir` un report CSV per ogni intermediario che ha almeno un IBAN su pagoPA: `reportCheckIban-<codIntermediario>-<yyyyMMdd_HHmmss>.csv`, codifica UTF-8, con una riga di intestazione e un record per IBAN (colonne `codFiscale`, `name`, `iban`, `status`, `validityDate`, `descrizione`, `label`, `checkStato`, `checkMotivo`). Gli intermediari senza IBAN non producono file; i report non vengono cancellati dal batch.

Con `govpay.report.invia_mail=true` i report sono inviati a `govpay.report.destinatario`. La mail parte solo se:

- c'è almeno un destinatario;
- in GovPay è abilitata la configurazione `mail_batch` (tabella `configurazione`), da cui si legge il server SMTP;
- la verifica degli IBAN è terminata correttamente: con lo step fallito i report sarebbero parziali;
- c'è almeno un report con dati.

Altrimenti la mail non parte e il log ne riporta il motivo. Il testo contiene il messaggio configurato e il numero di IBAN per esito (`OK`, `NON_CENSITO`, `INFO_DIVERSE`, `NON_ATTIVO`), in totale e per intermediario.

## Elaborazione

### Verifica degli IBAN

- **Partitioner** (`IntermediarioPartitioner`): una partizione per ogni intermediario con `cod_connettore_backoffice_ec` valorizzato.
- **Reader** (`IbanCheckReader`): legge da pagoPA tutte le pagine degli IBAN dell'intermediario.
- **Processor** (`IbanCheckProcessor`): cerca l'IBAN in `iban_accredito` per dominio. Se manca l'esito è `NON_CENSITO`; altrimenti confronta abilitazione (stato pagoPA `ATTIVO` con data di validità raggiunta), descrizione e intestatario, con esito `OK`, `INFO_DIVERSE` o `NON_ATTIVO`. Uno stato pagoPA non riconosciuto è trattato come non attivo e segnalato nel log e in `checkMotivo`.
- **Writer** (`IbanCheckWriter`): aggiorna `pagopa_iban_cache` (chiave dominio e IBAN) e scrive la riga del report.

Le chiamate a pagoPA producono un evento nel GDE con esito e numero di IBAN ricevuti. Le date restituite da pagoPA sono accettate con millisecondi da 1 a 9 cifre, con o senza ora.

### Sincronizzazione degli Enti Creditori

Stesso partitioner: il reader (`EcSyncReader`) legge gli Enti Creditori dell'intermediario, il processor (`EcCheckProcessor`) li confronta con `domini` per codice fiscale (esito `NON_CENSITO`, `OK` o `INFO_DIVERSE`), il writer (`EcSyncWriter`) aggiorna `pagopa_ec_cache` (chiave codice fiscale).

## Licenza

GovPay IBAN Batch è distribuito con licenza [GNU General Public License v3.0](LICENSE).
