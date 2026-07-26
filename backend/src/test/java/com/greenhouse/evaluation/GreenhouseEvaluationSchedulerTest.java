package com.greenhouse.evaluation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GreenhouseEvaluationSchedulerTest {

    @Test
    void reconcile_callsCoordinatorEvaluate() {
        GreenhouseEvaluationCoordinator coordinator = mock(GreenhouseEvaluationCoordinator.class);
        GreenhouseEvaluationScheduler scheduler = new GreenhouseEvaluationScheduler(coordinator);

        scheduler.reconcile();

        verify(coordinator).evaluate();
    }

    @Test
    void coordinatorException_isCaughtAndSubsequentInvocationsStillWork() {
        GreenhouseEvaluationCoordinator coordinator = mock(GreenhouseEvaluationCoordinator.class);
        when(coordinator.evaluate())
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(null);
        GreenhouseEvaluationScheduler scheduler = new GreenhouseEvaluationScheduler(coordinator);

        scheduler.reconcile();
        scheduler.reconcile();

        verify(coordinator, times(2)).evaluate();
    }

    @Test
    void overlappingEvaluation_isPrevented() throws InterruptedException {
        CountDownLatch coordinatorEntered = new CountDownLatch(1);
        CountDownLatch releaseCoordinator = new CountDownLatch(1);
        GreenhouseEvaluationCoordinator coordinator = mock(GreenhouseEvaluationCoordinator.class);
        when(coordinator.evaluate()).thenAnswer(invocation -> {
            coordinatorEntered.countDown();
            releaseCoordinator.await(2, TimeUnit.SECONDS);
            return null;
        });

        GreenhouseEvaluationScheduler scheduler = new GreenhouseEvaluationScheduler(coordinator);

        Thread firstRun = new Thread(scheduler::reconcile);
        firstRun.start();
        assertThat(coordinatorEntered.await(2, TimeUnit.SECONDS)).isTrue();

        // Second invocation while the first is still "running" - must be skipped, not queued.
        scheduler.reconcile();

        releaseCoordinator.countDown();
        firstRun.join(2000);

        verify(coordinator, times(1)).evaluate();
    }

    @Test
    void schedulerBean_isNotCreatedWhenDisabled() {
        new ApplicationContextRunner()
                .withBean(GreenhouseEvaluationCoordinator.class, () -> mock(GreenhouseEvaluationCoordinator.class))
                .withUserConfiguration(SchedulerImportConfig.class)
                .withPropertyValues("greenhouse.evaluation.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(GreenhouseEvaluationScheduler.class));
    }

    @Test
    void schedulerBean_isCreatedByDefault() {
        new ApplicationContextRunner()
                .withBean(GreenhouseEvaluationCoordinator.class, () -> mock(GreenhouseEvaluationCoordinator.class))
                .withUserConfiguration(SchedulerImportConfig.class)
                .run(context -> assertThat(context).hasSingleBean(GreenhouseEvaluationScheduler.class));
    }

    @Import(GreenhouseEvaluationScheduler.class)
    static class SchedulerImportConfig {
    }
}
