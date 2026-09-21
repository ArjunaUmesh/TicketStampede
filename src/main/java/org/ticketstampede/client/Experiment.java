package org.ticketstampede.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ticketstampede.dto.WorkloadResult;

import java.net.URI;
import java.util.List;

public class Experiment {

    public static void main(String[] args) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );
        BuyerClient buyerClient = new BuyerClient(URI.create("http://localhost:8080"), objectMapper);
        WorkloadRunner workloadRunner = new WorkloadRunner(buyerClient, objectMapper);
        int ticketCapacity = 5;
        int buyerCount = 30;
        List<Integer> concurrencyLevels = List.of(10);
        for (int concurrency : concurrencyLevels) {
            WorkloadResult result =
                    workloadRunner.runBuyWorkload(
                            ticketCapacity,
                            buyerCount,
                            concurrency
                    );
        }
    }
}
