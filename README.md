<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - IBAN Batch

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-iban-batch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-iban-batch)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-iban-batch?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-iban-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-iban-batch/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)](https://spring.io/projects/spring-boot)

## Sommario

Batch Spring Boot per la verifica automatica dell'attivazione degli IBAN su pagoPA.
Il sistema verifica lo stato di attivazione degli IBAN configurati interrogando le API pagoPA e aggiorna le informazioni nel database GovPay.

## Funzionalita' principali

- **Verifica IBAN**: Controllo automatico dell'attivazione degli IBAN su pagoPA
- **Riconciliazione**: Aggiornamento automatico dello stato IBAN nel database GovPay
- **Multi-database**: Supporto per PostgreSQL, MySQL/MariaDB, Oracle, SQL Server, HSQLDB
- **Schedulazione**: Esecuzione periodica configurabile o trigger manuale
- **Retry automatico**: Gestione errori con retry e backoff esponenziale
- **Containerizzazione**: Immagine Docker pronta per il deploy

## Requisiti

- Java 21+
- Maven 3.6.3+
- Database supportato (PostgreSQL, MySQL, Oracle, SQL Server, H2)

## Compilazione

```bash
mvn clean install
```

## Esecuzione

```bash
# Avvio applicazione
java -jar target/govpay-iban-batch.jar

# Con profilo specifico
java -jar target/govpay-iban-batch.jar --spring.profiles.active=prod
```

## Configurazione Docker

```bash
docker run -d \
  -e GOVPAY_DB_TYPE=postgresql \
  -e GOVPAY_DB_SERVER=db-host:5432 \
  -e GOVPAY_DB_NAME=govpay \
  -e GOVPAY_DB_USER=govpay \
  -e GOVPAY_DB_PASSWORD=secret \
  -e GOVPAY_IBAN_API_ENV=uat \
  -e GOVPAY_IBAN_API_SUBSCRIPTIONKEY=your-key \
  linkitaly/govpay-iban-batch:latest
```

## Database supportati

| Database | Versione minima |
|----------|-----------------|
| PostgreSQL | 9.6+ |
| MySQL | 5.7+ |
| MariaDB | 10.3+ |
| Oracle | 11g+ |
| SQL Server | 2016+ |
| HSQLDB/H2 | (sviluppo) |

## Documentazione

- **[ChangeLog](ChangeLog)** - Storia delle modifiche e release
- **[Wiki](https://github.com/link-it/govpay-iban-batch/wiki)** - Documentazione completa

## License

Questo progetto e' distribuito sotto licenza GPL v3. Vedere il file [LICENSE](LICENSE) per i dettagli.

## Contatti

- **Progetto**: [GovPay IBAN Batch](https://github.com/link-it/govpay-iban-batch)
- **Organizzazione**: [Link.it](https://www.link.it)

---

Questo progetto e' parte dell'ecosistema [GovPay](https://www.govpay.it) per la gestione dei pagamenti della Pubblica Amministrazione italiana tramite pagoPA.
