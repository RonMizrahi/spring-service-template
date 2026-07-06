package com.example.template.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import com.example.template.config.PerformanceMonitoringConfig.BusinessMetrics;

import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * AOP aspect that records authentication and external-API metrics without touching business logic.
 * Pointcuts must match the real service package ({@code com.example.template.service}).
 */
@Aspect
@Component
@Slf4j
public class PerformanceMonitoringAspect {

    private final BusinessMetrics businessMetrics;

    public PerformanceMonitoringAspect(BusinessMetrics businessMetrics) {
        this.businessMetrics = businessMetrics;
    }

    /**
     * Records login attempts, successes, failures and timing around AuthService.login.
     */
    @Around("execution(* com.example.template.service.AuthService.login(..))")
    public Object monitorAuthentication(ProceedingJoinPoint joinPoint) throws Throwable {
        businessMetrics.recordLoginAttempt();
        Timer.Sample authTimer = businessMetrics.startAuthenticationTimer();
        try {
            Object result = joinPoint.proceed();
            businessMetrics.recordLoginSuccess();
            return result;
        } catch (Exception e) {
            businessMetrics.recordLoginFailure();
            throw e;
        } finally {
            businessMetrics.recordAuthenticationTime(authTimer);
        }
    }

    /**
     * Records call count and timing around ExternalApiService.call* methods.
     */
    @Around("execution(* com.example.template.service.ExternalApiService.call*(..))")
    public Object monitorExternalApiCalls(ProceedingJoinPoint joinPoint) throws Throwable {
        businessMetrics.recordExternalApiCall();
        Timer.Sample apiTimer = businessMetrics.startExternalApiTimer();
        try {
            return joinPoint.proceed();
        } finally {
            businessMetrics.recordExternalApiTime(apiTimer);
        }
    }
}
