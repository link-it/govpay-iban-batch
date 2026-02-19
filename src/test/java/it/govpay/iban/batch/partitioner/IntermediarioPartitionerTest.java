package it.govpay.iban.batch.partitioner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.ExecutionContext;

import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.repository.IntermediarioRepository;

@ExtendWith(MockitoExtension.class)
class IntermediarioPartitionerTest {

    @Mock
    private IntermediarioRepository intermediarioRepository;

    private IntermediarioPartitioner partitioner;

    @BeforeEach
    void setUp() {
        partitioner = new IntermediarioPartitioner(intermediarioRepository);
    }

    private IntermediarioEntity createIntermediario(String codIntermediario) {
        return IntermediarioEntity.builder()
                .codIntermediario(codIntermediario)
                .abilitato(true)
                .build();
    }

    @Test
    void partition_withMultipleIntermediaries_shouldCreateOnePartitionEach() {
        List<IntermediarioEntity> intermediari = List.of(
                createIntermediario("11111111111"),
                createIntermediario("22222222222"),
                createIntermediario("33333333333"));
        when(intermediarioRepository.findAll()).thenReturn(intermediari);

        Map<String, ExecutionContext> partitions = partitioner.partition(5);

        assertEquals(3, partitions.size());
        assertTrue(partitions.containsKey("partition-11111111111"));
        assertTrue(partitions.containsKey("partition-22222222222"));
        assertTrue(partitions.containsKey("partition-33333333333"));
    }

    @Test
    void partition_shouldSetContextValues() {
        List<IntermediarioEntity> intermediari = List.of(
                createIntermediario("11111111111"),
                createIntermediario("22222222222"));
        when(intermediarioRepository.findAll()).thenReturn(intermediari);

        Map<String, ExecutionContext> partitions = partitioner.partition(5);

        ExecutionContext ctx1 = partitions.get("partition-11111111111");
        assertNotNull(ctx1);
        assertEquals("11111111111", ctx1.getString("codIntermediario"));
        assertEquals(1, ctx1.getInt("partitionNumber"));
        assertEquals(2, ctx1.getInt("totalPartitions"));

        ExecutionContext ctx2 = partitions.get("partition-22222222222");
        assertNotNull(ctx2);
        assertEquals("22222222222", ctx2.getString("codIntermediario"));
        assertEquals(2, ctx2.getInt("partitionNumber"));
        assertEquals(2, ctx2.getInt("totalPartitions"));
    }

    @Test
    void partition_withNoIntermediaries_shouldReturnEmptyMap() {
        when(intermediarioRepository.findAll()).thenReturn(Collections.emptyList());

        Map<String, ExecutionContext> partitions = partitioner.partition(5);

        assertTrue(partitions.isEmpty());
    }

    @Test
    void partition_withSingleIntermediary_shouldCreateSinglePartition() {
        when(intermediarioRepository.findAll()).thenReturn(List.of(createIntermediario("99999999999")));

        Map<String, ExecutionContext> partitions = partitioner.partition(1);

        assertEquals(1, partitions.size());
        ExecutionContext ctx = partitions.get("partition-99999999999");
        assertNotNull(ctx);
        assertEquals(1, ctx.getInt("partitionNumber"));
        assertEquals(1, ctx.getInt("totalPartitions"));
    }
}
