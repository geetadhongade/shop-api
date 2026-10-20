package com.example.shop.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Logs how long every controller and service method takes, and when one fails.
// It never logs method arguments or return values, so passwords, emails etc. never end up in the logs.
// The request ID is added to each line automatically (see RequestIdFilter).
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    // Calls slower than this are logged as a warning
    @Value("${shop.logging.slow-threshold-ms:500}")
    private long slowThresholdMs;

    // Runs around every method in our controller and service packages
    @Around("within(com.example.shop.controller..*) || within(com.example.shop.service..*)")
    public Object logMethodCall(ProceedingJoinPoint joinPoint) throws Throwable {
        // e.g. "ProductService.getProduct(..)"
        String methodName = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName() + "(..)";

        long startTime = System.currentTimeMillis();
        try {
            // Run the real method
            Object result = joinPoint.proceed();

            long durationMs = System.currentTimeMillis() - startTime;
            if (durationMs > slowThresholdMs) {
                log.warn("{} SLOW: completed in {} ms", methodName, durationMs);
            } else {
                log.info("{} completed in {} ms", methodName, durationMs);
            }
            return result;
        } catch (Throwable ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.warn("{} failed in {} ms: {} - {}", methodName, durationMs, ex.getClass().getSimpleName(), ex.getMessage());

            // Re-throw so GlobalExceptionHandler still turns it into a JSON error response
            throw ex;
        }
    }
}
