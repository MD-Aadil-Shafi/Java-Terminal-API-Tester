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