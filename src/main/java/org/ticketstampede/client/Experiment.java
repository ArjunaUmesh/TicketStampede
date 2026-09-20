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
        objectMapper.findAndRegisterModules();
        objectMapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );
        BuyerClient buyerClient = new BuyerClient(URI.create("http://localhost:8080"), objectMapper);
        WorkloadRunner workloadRunner = new WorkloadRunner(buyerClient, objectMapper);
        int ticketCapacity = 100;
        int buyerCount = 20;
        List<Integer> concurrencyLevels = List.of(20);
        Path resultPath = Path.of("results", "baseline-50k_5.csv");
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
