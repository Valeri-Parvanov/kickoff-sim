package com.kickoffsim.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceExecutionLoggingAspectTest {

    private final ServiceExecutionLoggingAspect aspect = new ServiceExecutionLoggingAspect();

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    @BeforeEach
    void setUp() {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("TeamServiceImpl.createTeam(..)");
    }

    @Test
    void logExecution_returnsProceedResult_onSuccess() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.logExecution(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(joinPoint).proceed();
    }

    @Test
    void logExecution_rethrowsAndLogs_onFailure() throws Throwable {
        RuntimeException failure = new RuntimeException("boom");
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.logExecution(joinPoint))
                .isSameAs(failure);
    }
}
