# GovPay IBAN Batch - Immagine Docker

Immagine del batch che confronta gli IBAN e l'anagrafica degli Enti Creditori registrati su pagoPA con quelli censiti in GovPay (`ibanCheckJob`), aggiorna le cache `pagopa_iban_cache` e `pagopa_ec_cache` lette dalla console e produce un report CSV per intermediario. Cosa fa il batch, come si configura e l'elenco completo delle variabili d'ambiente sono nel [README](../README.md#esecuzione-con-docker): qui solo ciò che riguarda l'immagine.

## Contenuto

| Percorso | Contenuto |
|----------|-----------|
| `/opt/govpay-iban/govpay-iban-batch.jar` | Fat jar del batch |
| `/opt/jdbc-drivers` | Driver JDBC, da montare: non sono inclusi nell'immagine (vedi [jdbc-drivers/README.md](jdbc-drivers/README.md)) |
| `/opt/sql` | Script SQL di `sql.zip`: schema Spring Batch per database (`spring-batch/`) e svecchiamento dei metadati (`cleanup/`) |
| `/opt/hsqldb-<versione>` | SqlTool di HSQLDB, usato per creare lo schema Spring Batch all'avvio |
| `/var/log/govpay-iban/govpay-iban.log` | File di log dell'applicazione |
| `/tmp` | Directory di default dei report CSV (`GOVPAY_REPORT_DIR`): montare un volume e indicarlo, altrimenti i report si perdono al riavvio |

Il processo gira con l'utente `govpay`; l'entrypoint è `/usr/local/bin/entrypoint.sh`.

## Costruzione

```bash
cd docker
./build_image.sh                       # ultima release pubblicata su GitHub
./build_image.sh -v <versione>         # una release specifica
./build_image.sh -l ../target/govpay-iban-batch.jar -t govpay-iban-batch:locale   # jar compilato in locale
```

Con una release, jar e `sql.zip` sono scaricati da GitHub e verificati con lo sha256 letto dalle API delle release (`-s` e `-q` per indicarlo a mano). Con `-l` si usa `Dockerfile.daFile` e gli script SQL vengono da `../target/sql.zip`, prodotto da `mvn clean install -Pjar,dist`: senza, l'immagine non ha gli script per creare lo schema. `./build_image.sh -h` elenca tutte le opzioni.

## Modalità di esecuzione

| Modalità | Come si attiva | Comportamento |
|----------|----------------|---------------|
| Scheduler interno | default (`GOVPAY_IBAN_BATCH_USA_CRON` non impostata o falsa) | Il container resta attivo, esegue `ibanCheckJob` a intervalli ed espone API REST e actuator sulla porta `10001`. È la modalità da usare se la console avvia il batch (operazione `IBAN_CHECK`) |
| Esecuzione singola | `GOVPAY_IBAN_BATCH_USA_CRON=true` (anche `si`, `yes`, `1`) | Profilo `cron`: esegue `ibanCheckJob` una volta e termina, senza server HTTP. La pianificazione è esterna (cron di sistema, CronJob Kubernetes) |

Con lo scheduler interno l'intervallo si indica in minuti con `GOVPAY_IBAN_BATCH_INTERVALLO_CRON` (default `120`), che l'entrypoint converte nella proprietà `scheduler.ibanCheckJob.fixedDelayString`. Se è impostata direttamente `SCHEDULER_IBANCHECKJOB_FIXEDDELAYSTRING` (in millisecondi), vale quella e `GOVPAY_IBAN_BATCH_INTERVALLO_CRON` viene ignorata. Il valore usato è scritto nel log di avvio:

```
[INFO] ... - Porta HTTP: 10001, Intervallo scheduler: 120 minuti (7200000ms)
```

In esecuzione singola l'exit code è `0` anche quando il job fallisce: l'esito va letto nei log.

## Schema del database

Con `GOVPAY_IBAN_POP_DB_SKIP=FALSE` l'entrypoint attende il database, controlla se esiste la tabella `GOVPAY_IBAN_DB_CHECK_TABLE` (default `batch_job_execution_context`) e, se manca, crea le tabelle dei metadati Spring Batch con lo script `/opt/sql/spring-batch/schema-<database>.sql`. Le tabelle di GovPay non vengono create: sono degli script di installazione di GovPay. Anche `pagopa_iban_cache` e `pagopa_ec_cache` non vengono create: sono dello script `console-api-schema.sql` di [govpay-console-api](https://github.com/link-it/govpay-console-api).

## API pagoPA e Giornale degli Eventi

URL e credenziali dell'API di backoffice pagoPA non sono variabili del container: sono nel connettore di GovPay indicato dal campo `cod_connettore_backoffice_ec` di ogni intermediario. Allo stesso modo l'invio degli eventi al GDE si configura con il connettore `govpay_gde_api` di GovPay. Le variabili `GOVPAY_IBAN_ENV`, `GOVPAY_IBAN_CUSTOMURL` e `GOVPAY_IBAN_SUBSCRIPTIONKEY`, presenti in versioni precedenti di questa documentazione, non sono mai state lette; `GOVPAY_IBAN_GDE_URL` non ha effetto e, se impostata, l'entrypoint lo segnala con un avviso nel log.

## Scansione delle vulnerabilità

Le immagini pubblicate dalla pipeline sono scansionate prima della pubblicazione: vedi [SCANSIONE.md](SCANSIONE.md).
