package com.kickoffsim.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class ServiceExecutionLoggingAspect {

    @Around("execution(* com.kickoffsim.service.impl..*(..))")
    public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String signature = joinPoint.getSignature().toShortString();
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            log.info("{} completed in {} ms", signature, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            log.warn("{} failed after {} ms: {}", signature, System.currentTimeMillis() - start, ex.getMessage());
            throw ex;
        }
    }
}
