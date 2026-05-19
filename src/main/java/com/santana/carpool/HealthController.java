package com.santana.carpool;

import io.sentry.Sentry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/test-sentry")
    public String testSentry() {
        try {
            throw new Exception("This is a test exception for Sentry.");
        } catch (Exception e) {
            Sentry.captureException(e);
            return "Exception captured and sent to Sentry. Check your Sentry dashboard.";
        }
    }
}
