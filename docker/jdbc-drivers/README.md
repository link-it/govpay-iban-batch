# JDBC Drivers

I driver JDBC non sono inclusi nel fat jar dell'applicazione né nell'immagine e devono essere forniti esternamente.
Collocare i file JAR dei driver in questa directory e montarla nel container al path `/opt/jdbc-drivers`
(o nella directory indicata da `GOVPAY_DS_JDBC_LIBS`).

L'entrypoint del container imposta `LOADER_PATH` su quella directory, in modo che il `PropertiesLauncher`
di Spring Boot carichi i driver a runtime; la stessa directory è usata da SqlTool per creare lo schema
Spring Batch all'avvio.

## Driver supportati

Versioni gestite da [govpay-bom](https://github.com/link-it/govpay-bom). L'immagine supporta
`GOVPAY_DB_TYPE` uguale a `postgresql`, `mysql`, `mariadb` e `oracle`.

### PostgreSQL

```bash
wget https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar
```

**License**: BSD-2-Clause

### MySQL e MariaDB

L'entrypoint usa il driver MySQL (`com.mysql.cj.jdbc.Driver`) anche con `GOVPAY_DB_TYPE=mariadb`.

```bash
wget https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/26.10.0/mysql-connector-j-26.10.0.jar
```

**License**: GPL-2.0 con Universal FOSS Exception

### Oracle

```bash
wget https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.3.0.0/ojdbc11-23.26.3.0.0.jar
```

**License**: Oracle Free Use Terms and Conditions (FUTC)
**Note**: Verificare i termini di licenza Oracle prima dell'uso in produzione.

## Verifica

Dopo il download, verificare che il driver sia presente:

```bash
ls -lh *.jar
```

## Database multipli

Se necessario supportare più tipi di database, è possibile collocare più driver in questa directory.
L'applicazione caricherà quello appropriato in base alla configurazione `GOVPAY_DB_TYPE`.

Esempio:
```
jdbc-drivers/
├── postgresql-42.7.14.jar
├── mysql-connector-j-26.10.0.jar
└── ojdbc11-23.26.3.0.0.jar
```

## Troubleshooting

Se si ottiene l'errore "No suitable driver found", verificare che:

1. Il file JAR del driver sia presente in questa directory
2. Il file JAR abbia i permessi corretti (leggibile)
3. Il valore di `GOVPAY_DB_TYPE` corrisponda al database in uso
4. Il nome della classe driver sia corretto per il tipo di database
