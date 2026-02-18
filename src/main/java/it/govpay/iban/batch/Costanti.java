package it.govpay.iban.batch;

public class Costanti {
	
	private Costanti() {
		// Costruttore privato per evitare istanziazione
	}

	// Job parameters per gestione multi-nodo
    public static final String GOVPAY_BATCH_JOB_ID = "JobID";
    public static final String GOVPAY_BATCH_JOB_PARAMETER_WHEN = "When";
    public static final String GOVPAY_BATCH_JOB_PARAMETER_CLUSTER_ID = "ClusterID"; 

    public static final String PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    // Pattern con millisecondi variabili (1-9 cifre) per deserializzazione sicura da pagoPA
    public static final String PATTERN_YYYY_MM_DD_T_HH_MM_SS_MILLIS_VARIABILI_XXX = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSSSSS][.SSSSSSSS][.SSSSSSS][.SSSSSS][.SSSSS][.SSSS][.SSS][.SS][.S]XXX";

    // Pattern per serializzazione date alle API esterne (3 cifre millisecondi senza timezone)
    public static final String PATTERN_DATA_JSON_YYYY_MM_DD_T_HH_MM_SS_SSS = "yyyy-MM-dd'T'HH:mm:ss.SSS";

	public static final String MSG_PAYLOAD_NON_SERIALIZZABILE = "Payload non serializzabile";

	// Nome job IBAN check
	public static final String IBAN_CHECK_JOB_NAME = "ibanCheckJob";

	// Operazioni IBAN API (da openapi_backoffice_external_ec.json)
    public static final String OPERATION_GET_ALL_IBANS = "getAllIbans";

    // Non sono URI completi (mancano protocollo e host) ma template che vengono
    // combinati con il baseUrl del connettore configurato in DB.
    // Soppressione S1075: path template API fissi, non URI configurabili
    @SuppressWarnings("java:S1075")
    public static final String PATH_GET_ALL_IBANS = "/brokers/{brokerCode}/ibans";

    public static final String CHECK_OK = "OK";
    public static final String CHECK_NON_CENSITO = "NON_CENSITO";
    public static final String CHECK_INFO_DIVERSE = "INFO_DIVERSE";
    public static final String CHECK_NON_ATTIVO = "NON_ATTIVO";
}
