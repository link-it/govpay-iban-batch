<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay IBAN Batch

[![GitHub](https://img.shields.io/badge/GitHub-link--it%2Fgovpay--iban--batch-blue?logo=github)](https://github.com/link-it/govpay-iban-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

Batch Spring Boot di [GovPay](https://github.com/link-it/govpay) che confronta gli IBAN e l'anagrafica degli Enti Creditori registrati su pagoPA con quelli censiti in GovPay, aggiorna la cache usata dalla console per il censimento assistito e produce un report CSV per intermediario, inviabile via mail.

## Funzionalità

- Lettura di IBAN ed Enti Creditori dall'API di backoffice pagoPA, per ogni intermediario con il connettore di backoffice EC configurato in GovPay
- Confronto con IBAN di accredito e domini di GovPay, con esito per elemento (`OK`, `NON_CENSITO`, `INFO_DIVERSE`, `NON_ATTIVO`)
- Aggiornamento delle tabelle `pagopa_iban_cache` e `pagopa_ec_cache`, lette da govpay-console-api
- Report CSV per intermediario, con invio via mail tramite la configurazione `mail_batch` di GovPay
- Scheduler interno (default) oppure esecuzione singola da pianificare all'esterno (`GOVPAY_IBAN_BATCH_USA_CRON=true`)
- Coordinamento tra più nodi tramite i metadati Spring Batch sul database
- Database PostgreSQL, MySQL/MariaDB e Oracle; driver JDBC da montare in `/opt/jdbc-drivers`
- Health check, metriche Prometheus e API REST per l'avvio manuale sulla porta `10001`

## Avvio

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

L'elenco completo delle variabili d'ambiente è nel [README, sezione Esecuzione con Docker](https://github.com/link-it/govpay-iban-batch/blob/main/README.md#esecuzione-con-docker).

## Tag

- `latest`: ultima release
- `<versione>`: una release specifica, come da [release GitHub](https://github.com/link-it/govpay-iban-batch/releases)

Le immagini di sviluppo, costruite a ogni push su `main`, sono pubblicate come `linkitaly/govpay-iban-batch-dev`. Storico delle modifiche nel [ChangeLog](https://github.com/link-it/govpay-iban-batch/blob/main/ChangeLog).

## Documentazione

- [README](https://github.com/link-it/govpay-iban-batch/blob/main/README.md): configurazione, esecuzione, monitoraggio, API REST
- [Immagine Docker](https://github.com/link-it/govpay-iban-batch/blob/main/docker/DOCKER.md): contenuto, costruzione, modalità di esecuzione
- [Scansione dell'immagine](https://github.com/link-it/govpay-iban-batch/blob/main/docker/SCANSIONE.md)
- [Dockerfile](https://github.com/link-it/govpay-iban-batch/blob/main/docker/govpay-iban/Dockerfile.github)

## Licenza

GovPay IBAN Batch è rilasciato con licenza [GPL v3](https://www.gnu.org/licenses/gpl-3.0).

---

Sviluppato da [Link.it s.r.l.](https://www.link.it)
