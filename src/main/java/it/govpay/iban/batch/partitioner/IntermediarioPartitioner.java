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

/**
 * Partitioner che divide il lavoro per cod_intermediario.
 * Ogni partizione processa tutti gli IBAN di un singolo intermediario.
 */
@Component
@Slf4j
public class IntermediarioPartitioner implements Partitioner {

    private final IntermediarioRepository intermediarioRepository;

    public IntermediarioPartitioner(IntermediarioRepository inermediarioRepository) {
        this.intermediarioRepository = inermediarioRepository;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        // Recupera tutti i cod_intermediario presenti in INTERMEDIARI
        List<IntermediarioEntity> intermediari = intermediarioRepository.findAll();

        log.info("Creazione partizioni: trovati {} intermediari", intermediari.size());

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
