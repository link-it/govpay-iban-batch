<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - IBAN Batch

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-iban-batch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-iban-batch)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-iban-batch?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-iban-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-iban-batch/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-brightgreen.svg)](https://spring.io/projects/spring-boot)

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

### Driver JDBC

I driver JDBC **non sono inclusi** nel fat jar e devono essere forniti esternamente a runtime.
Creare una directory (es. `jdbc-drivers/`) e copiarvi il driver del database utilizzato:

| Database   | Driver                                                                                     |
|------------|--------------------------------------------------------------------------------------------|
| PostgreSQL | [postgresql-42.7.9.jar](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.9/) |
| MySQL      | [mysql-connector-j-9.6.0.jar](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.6.0/) |
| Oracle     | [ojdbc11-23.26.1.0.0.jar](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.1.0.0/) |
| SQL Server | [mssql-jdbc-12.8.2.jre11.jar](https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/12.8.2.jre11/) |
| H2         | [h2-2.4.240.jar](https://repo1.maven.org/maven2/com/h2database/h2/2.4.240/)              |

## Esecuzione

Il jar utilizza `PropertiesLauncher` (layout ZIP) e richiede la proprietà `loader.path` per indicare
la directory contenente i driver JDBC:

```bash
# Avvio applicazione (modalità schedulata - profilo default)
java -Dloader.path=./jdbc-drivers -jar target/govpay-iban-batch.jar

# Esecuzione singola (profilo cron - esegue una volta e termina)
java -Dloader.path=./jdbc-drivers -jar target/govpay-iban-batch.jar --spring.profiles.active=cron
```

## Configurazione Docker

Con Docker la variabile d'ambiente `LOADER_PATH` viene impostata automaticamente dall'entrypoint
alla directory `/opt/jdbc-drivers`. I driver devono essere montati come volume o copiati nell'immagine:

```bash
# Montaggio volume con il driver JDBC
docker run -d \
  -v ./jdbc-drivers:/opt/jdbc-drivers \
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
