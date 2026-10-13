package org.terminal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ApiLoadTester {

    private final HttpClient httpClient;
    private final int concurrency;
    private final int totalRequests;
    private final Duration requestTimeout;

    public ApiLoadTester(
            int concurrency,
            int totalRequests,
            Duration requestTimeout
    ) {
        if (concurrency < 1 || totalRequests < 1) {
            throw new IllegalArgumentException(
                    "Concurrency and request count must be positive"
            );
        }

        this.concurrency = concurrency;
        this.totalRequests = totalRequests;
        this.requestTimeout = requestTimeout;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void run(String url) throws InterruptedException {
        URI uri = URI.create(url);

        // Validate before starting worker threads.
        if (!uri.getScheme().equalsIgnoreCase("http")
                && !uri.getScheme().equalsIgnoreCase("https")) {
            throw new IllegalArgumentException(
                    "Only HTTP and HTTPS URLs are supported"
            );
        }

        //for thread pool
        ExecutorService executor =
                Executors.newFixedThreadPool(concurrency);

        //to collect completed tasks as they finish instead of waiting for results in submission order.
        CompletionService<RequestResult> completionService =
                new ExecutorCompletionService<>(executor);

        AtomicInteger submitted = new AtomicInteger();

        List<RequestResult> results =
                new ArrayList<>(totalRequests);

        //measures elapsed time for each request and for the entire test.
        long testStart = System.nanoTime();

        try {
            // Submit all requests. The pool limits active workers.
            for (int i = 1; i <= totalRequests; i++) {
                final int requestId = i;

                completionService.submit(() -> {
                    submitted.incrementAndGet();
                    return sendRequest(requestId, uri);
                });
            }

            // Collect completed requests, not submission order.
            for (int i = 0; i < totalRequests; i++) {
                try {
                    Future<RequestResult> future =
                            completionService.take();

                    results.add(future.get());

                } catch (ExecutionException e) {
                    // Unexpected task-level failure.
                    results.add(new RequestResult(
                            -1, 0, 0, false,
                            rootMessage(e)
                    ));
                }
            }
        } finally {
            executor.shutdown();

            if (!executor.awaitTermination(
                    10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }

        long elapsedNanos = System.nanoTime() - testStart;

        printReport(url, results, elapsedNanos);
    }

    private RequestResult sendRequest(
            int requestId,
            URI uri
    ) {
        long start = System.nanoTime();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );

            long latencyMs = elapsedMillis(start);

            int status = response.statusCode();
            boolean success = status >= 200 && status < 300;

            return new RequestResult(
                    requestId,
                    status,
                    latencyMs,
                    success,
                    success ? null : "HTTP " + status
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return new RequestResult(
                    requestId, 0, elapsedMillis(start),
                    false, "Interrupted"
            );

        } catch (Exception e) {
            return new RequestResult(
                    requestId, 0, elapsedMillis(start),
                    false, rootMessage(e)
            );
        }
    }

    private long elapsedMillis(long start) {
        return TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - start
        );
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;

        while (current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }

        String message = current.getMessage();

        return current.getClass().getSimpleName()
                + (message == null ? "" : ": " + message);
    }

    private void printReport(
            String url,
            List<RequestResult> results,
            long elapsedNanos
    ) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(
                elapsedNanos
        );

        long successful = results.stream()
                .filter(RequestResult::success)
                .count();

        long failed = results.size() - successful;

        List<Long> latencies = results.stream()
                .map(RequestResult::latencyMs)
                .sorted()
                .toList();

        double seconds = elapsedNanos / 1_000_000_000.0;
        double throughput = seconds > 0
                ? results.size() / seconds
                : 0;

        double average = latencies.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        System.out.println("\n========== LOAD TEST REPORT ==========");
        System.out.println("URL:          " + url);
        System.out.println("Workers:      " + concurrency);
        System.out.println("Requests:     " + results.size());
        System.out.println("Successful:   " + successful);
        System.out.println("Failed:       " + failed);
        System.out.println("Duration:     " + elapsedMs + " ms");
        System.out.printf("Throughput:   %.2f requests/sec%n",
                throughput);
        System.out.printf("Average:      %.2f ms%n", average);
        System.out.println("P50 latency:  " + percentile(latencies, 50)
                + " ms");
        System.out.println("P95 latency:  " + percentile(latencies, 95)
                + " ms");
        System.out.println("P99 latency:  " + percentile(latencies, 99)
                + " ms");

        Map<Integer, Long> statusCounts = results.stream()
                .filter(r -> r.statusCode() != 0)
                .collect(Collectors.groupingBy(
                        RequestResult::statusCode,
                        TreeMap::new,
                        Collectors.counting()
                ));

        System.out.println("\nHTTP STATUS COUNTS:");
        statusCounts.forEach((status, count) ->
                System.out.println(status + " -> " + count));

        System.out.println("\nSAMPLE FAILURES:");
        results.stream()
                .filter(r -> !r.success())
                .limit(10)
                .forEach(r -> System.out.printf(
                        "Request %d | %s | %s%n",
                        r.requestId(),
                        r.statusCode() == 0
                                ? "No HTTP response"
                                : "HTTP " + r.statusCode(),
                        r.error()
                ));

        System.out.println("======================================");
    }

    private long percentile(List<Long> sorted, int percentile) {
        if (sorted.isEmpty()) {
            return 0;
        }

        int index = (int) Math.ceil(
                percentile / 100.0 * sorted.size()
        ) - 1;

        return sorted.get(Math.max(0, index));
    }
}
