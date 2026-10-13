package org.terminal;

import java.time.Duration;

public class Main {

    public static void main(String[] args) throws Exception {
        String url = args.length > 0
                ? args[0]
                : "https://httpbin.org/get";

        int requests = args.length > 1
                ? Integer.parseInt(args[1])
                : 100;

        int concurrency = args.length > 2
                ? Integer.parseInt(args[2])
                : 10;

        int timeoutSeconds = args.length > 3
                ? Integer.parseInt(args[3])
                : 10;

        ApiLoadTester tester = new ApiLoadTester(
                concurrency,
                requests,
                Duration.ofSeconds(timeoutSeconds)
        );

        tester.run(url);
    }
}
