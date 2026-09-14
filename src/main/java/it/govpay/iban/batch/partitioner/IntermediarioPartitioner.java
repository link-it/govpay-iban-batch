package it.govpay.iban.batch.partitioner;

import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.repository.IntermediarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Partitioner che divide il lavoro per cod_intermediario.
 * Ogni partizione processa tutti gli IBAN di un singolo intermediario.
 * <p>
 * Sono considerati solo gli intermediari che hanno configurato il connettore di
 * backoffice EC: e' l'unico modo per raggiungere le API pagoPA, quindi senza di
 * esso non c'e' nulla da interrogare.
 */
@Component
@Slf4j
public class IntermediarioPartitioner implements Partitioner {

    private final IntermediarioRepository intermediarioRepository;

    public IntermediarioPartitioner(IntermediarioRepository inermediarioRepository) {
        this.intermediarioRepository = inermediarioRepository;
    }

    /**
     * Un intermediario e' interrogabile solo se ha un connettore di backoffice EC.
     */
    private static boolean haConnettoreBackofficeEc(IntermediarioEntity intermediario) {
        String codConnettore = intermediario.getCodConnettoreBackofficeEc();
        return codConnettore != null && !codConnettore.isBlank();
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        // Recupera tutti i cod_intermediario presenti in INTERMEDIARI
        List<IntermediarioEntity> tuttiGliIntermediari = intermediarioRepository.findAll();

        // Senza connettore di backoffice EC la risoluzione fallirebbe a meta' partizione,
        // facendo cadere con se' l'intero job: un intermediario non configurato per il
        // controllo IBAN va saltato, non trattato come errore.
        List<IntermediarioEntity> intermediari = tuttiGliIntermediari.stream()
                .filter(IntermediarioPartitioner::haConnettoreBackofficeEc)
                .toList();

        int esclusi = tuttiGliIntermediari.size() - intermediari.size();
        if (esclusi > 0) {
            String codiciEsclusi = tuttiGliIntermediari.stream()
                    .filter(intermediario -> !haConnettoreBackofficeEc(intermediario))
                    .map(IntermediarioEntity::getCodIntermediario)
                    .collect(Collectors.joining(", "));
            log.warn("Esclusi {} intermediari su {} privi di connettore di backoffice EC: {}",
                esclusi, tuttiGliIntermediari.size(), codiciEsclusi);
        }

        log.info("Creazione partizioni: {} intermediari con connettore di backoffice EC", intermediari.size());

        if (intermediari.isEmpty()) {
            log.warn("Nessun intermediario con connettore di backoffice EC configurato: nessun IBAN verra' verificato");
        }

        Map<String, ExecutionContext> partitions = new HashMap<>();

        for (int i = 0; i < intermediari.size(); i++) {
            String codIntermediario = intermediari.get(i).getCodIntermediario();

            ExecutionContext context = new ExecutionContext();
            context.putString("codIntermediario", codIntermediario);
            context.putInt("partitionNumber", i + 1);
            context.putInt("totalPartitions", intermediari.size());

            // Nome partizione: partition-codIntermediario
            String partitionName = "partition-" + codIntermediario;
            partitions.put(partitionName, context);

            log.debug("Creata partizione #{} per intermediario: {}", i + 1, codIntermediario);
        }

        log.info("Partizioni create: {} (gridSize richiesto: {})", partitions.size(), gridSize);
        return partitions;
    }
}
