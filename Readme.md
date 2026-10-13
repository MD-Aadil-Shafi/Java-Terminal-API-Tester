## Terminal based API load tester

Best used for GET APIs.

### How to use:

#### Compile:
```bash
mvn clean compile
```

#### Commands: 
URL followed by Requests, Concurrency, TimeoutSeconds

1. Test 100 requests with 10 concurrent workers:
```bash
mvn exec:java "-Dexec.mainClass=org.terminal.Main" "-Dexec.args=https://httpbin.org/get 100 10 10"
```

2. Test 2,000 requests with 100 concurrent workers:
```bash
mvn exec:java "-Dexec.mainClass=org.terminal.Main" "-Dexec.args=http://localhost:8080/api/health 2000 100 5" 
```

#### Example response in terminal:
```
========== LOAD TEST REPORT ==========
URL:          http://localhost:8080/api/health
Workers:      100
Requests:     2000
Successful:   1985
Failed:       15
Duration:     4200 ms
Throughput:   476.19 requests/sec
Average:      82.50 ms
P50 latency:  65 ms
P95 latency:  180 ms
P99 latency:  350 ms

HTTP STATUS COUNTS:
200 -> 1985
500 -> 10
503 -> 5
======================================
```