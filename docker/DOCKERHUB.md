<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay IBAN Batch

[![GitHub](https://img.shields.io/badge/GitHub-link--it%2Fgovpay--iban--batch-blue?logo=github)](https://github.com/link-it/govpay-iban-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

Batch Spring Boot per la **verifica attivazione degli IBAN** su pagoPA.

## Cos'è GovPay IBAN Batch

GovPay IBAN Batch è un componente del progetto [GovPay](https://github.com/link-it/govpay) che si occupa della verifica automatica dell'attivazione degli IBAN sul servizio pagoPA.

### Funzionalità principali

- Verifica automatica attivazione IBAN su pagoPA
- Supporto multi-database: PostgreSQL, MySQL/MariaDB, Oracle
- Modalità di deployment flessibili (daemon o esecuzione singola)
- Integrazione opzionale con GDE (Giornale degli Eventi)
- Health check e monitoraggio tramite Spring Boot Actuator
- Gestione automatica del recovery per job bloccati

## Versioni disponibili

- `latest` - ultima versione stabile

Storico completo delle modifiche consultabile nel [ChangeLog](https://github.com/link-it/govpay-iban-batch/blob/main/ChangeLog) del progetto.

## Quick Start

```bash
docker pull linkitaly/govpay-iban-batch:latest
```

## Documentazione

- [README e istruzioni di configurazione](https://github.com/link-it/govpay-iban-batch/blob/main/README.md)
- [Documentazione Docker](https://github.com/link-it/govpay-iban-batch/blob/main/docker/DOCKER.md)
- [Dockerfile](https://github.com/link-it/govpay-iban-batch/blob/main/docker/govpay-iban/Dockerfile.github)

## Licenza

GovPay IBAN Batch è rilasciato con licenza [GPL v3](https://www.gnu.org/licenses/gpl-3.0).

## Supporto

- **Issues**: [GitHub Issues](https://github.com/link-it/govpay-iban-batch/issues)
- **GovPay**: [govpay.readthedocs.io](https://govpay.readthedocs.io/)

---

Sviluppato da [Link.it s.r.l.](https://www.link.it)
