package org.ticketstampede.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ticketstampede.dto.WorkloadResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Experiment {

    public static void main(String[] args) throws Exception
    {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );
        BuyerClient buyerClient = new BuyerClient(URI.create("http://localhost:8080"), objectMapper);
        WorkloadRunner workloadRunner = new WorkloadRunner(buyerClient, objectMapper);
        int ticketCapacity = 100;
        int buyerCount = 50000;
        List<Integer> concurrencyLevels = List.of(
                10, 20, 30, 40, 50,
                60, 70, 80, 90, 100,
                110, 120, 130, 140, 150,
                160, 170, 180, 190,200,
                225, 250, 275, 300
        );
        Path resultPath = Path.of("results", "optimized_v2_sweep.csv");
        Files.createDirectories(resultPath.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(resultPath))
        {
            writeHeader(writer);
            for(int concurrency : concurrencyLevels)
            {
                WorkloadResult result =
                        workloadRunner.runBuyWorkload(
                                ticketCapacity,
                                buyerCount,
                                concurrency
                        );
                writeResult(writer, result);
            }
        }
    }

    private static void writeHeader(BufferedWriter writer) throws IOException
    {
        writer.write(
                "ticketCapacity," +
                        "buyerCount," +
                        "concurrency," +
                        "httpRequestsCount," +
                        "errorCount," +
                        "durationSeconds," +
                        "throughput," +
                        "p50Millis," +
                        "p99Millis," +
                        "invariantsPassed"
        );

        writer.newLine();
    }

    private static void writeResult(
            BufferedWriter writer,
            WorkloadResult result
    ) throws IOException
    {
        writer.write(
                result.ticketCapacity() + "," +
                        result.buyerCount() + "," +
                        result.concurrency() + "," +
                        result.httpRequestsCount() + "," +
                        result.errors() + "," +
                        result.durationSeconds() + "," +
                        result.throughput() + "," +
                        result.p50Millis() + "," +
                        result.p99Millis() + "," +
                        result.invariantsPassed()
        );

        writer.newLine();
        writer.flush();
    }
}
