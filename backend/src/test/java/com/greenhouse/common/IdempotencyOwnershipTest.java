package com.greenhouse.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// reserve() used to return void and swallow the duplicate-key violation, so
// two simultaneous deliveries of one MCP request both ran the action. Database
// constraints masked it for commands; nothing masked it for executions.
//
// Run against a real database, because the guarantee IS the unique constraint -
// a mock would prove nothing.
@SpringBootTest(properties = {
        "greenhouse.evaluation.enabled=false",
        "greenhouse.daily-briefing.enabled=false",
        "greenhouse.outcome-evaluation.enabled=false",
        "greenhouse.notifications.enabled=false"
})
class IdempotencyOwnershipTest {

    @Autowired private IdempotencyService idempotencyService;
    @Autowired private IdempotentRequestRepository requestRepository;

    private final List<String> keys = new ArrayList<>();

    private String newKey() {
        String key = "ownership-test-" + System.nanoTime();
        keys.add(key);
        return key;
    }

    @AfterEach
    void cleanUp() {
        keys.forEach(key -> requestRepository.findByIdempotencyKey(key)
                .ifPresent(requestRepository::delete));
        keys.clear();
    }

    @Test
    void theFirstCallerAcquiresTheReservation() {
        String key = newKey();
        String fingerprint = idempotencyService.fingerprint("test_tool", "args");

        assertThat(idempotencyService.reserve(key, "test_tool", fingerprint))
                .isEqualTo(IdempotencyService.Reservation.ACQUIRED);
    }

    @Test
    void aSecondCallerWhileTheFirstIsStillRunningIsToldToWait() {
        String key = newKey();
        String fingerprint = idempotencyService.fingerprint("test_tool", "args");

        assertThat(idempotencyService.reserve(key, "test_tool", fingerprint))
                .isEqualTo(IdempotencyService.Reservation.ACQUIRED);
        assertThat(idempotencyService.reserve(key, "test_tool", fingerprint))
                .isEqualTo(IdempotencyService.Reservation.IN_PROGRESS);
    }

    @Test
    void aRetryAfterCompletionReplaysRatherThanReacquiring() {
        String key = newKey();
        String fingerprint = idempotencyService.fingerprint("test_tool", "args");

        idempotencyService.reserve(key, "test_tool", fingerprint);
        idempotencyService.complete(key, "{\"done\":true}");

        assertThat(idempotencyService.reserve(key, "test_tool", fingerprint))
                .isEqualTo(IdempotencyService.Reservation.ALREADY_COMPLETED);
        assertThat(idempotencyService.findCompletedResult(key, "test_tool", fingerprint))
                .contains("{\"done\":true}");
    }

    @Test
    void theSameKeyWithDifferentArgumentsIsAConflictNotARetry() {
        String key = newKey();
        idempotencyService.reserve(key, "test_tool", idempotencyService.fingerprint("test_tool", "args"));

        assertThat(idempotencyService.reserve(
                key, "test_tool", idempotencyService.fingerprint("test_tool", "DIFFERENT")))
                .isEqualTo(IdempotencyService.Reservation.CONFLICT);
    }

    // THE regression test: many threads deliver the same request at once, and
    // exactly one may run the action.
    @Test
    void exactlyOneOfManyConcurrentDeliveriesRunsTheAction() throws Exception {
        String key = newKey();
        String fingerprint = idempotencyService.fingerprint("test_tool", "args");

        int threads = 8;
        CyclicBarrier startTogether = new CyclicBarrier(threads);
        AtomicInteger actionsRun = new AtomicInteger();

        List<Callable<IdempotencyService.Reservation>> callables = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            callables.add(() -> {
                startTogether.await();
                IdempotencyService.Reservation reservation =
                        idempotencyService.reserve(key, "test_tool", fingerprint);
                if (reservation == IdempotencyService.Reservation.ACQUIRED) {
                    // Standing in for the domain action - recording an
                    // execution, issuing a command, changing a profile.
                    actionsRun.incrementAndGet();
                }
                return reservation;
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<IdempotencyService.Reservation>> futures = pool.invokeAll(callables);
            List<IdempotencyService.Reservation> outcomes = new ArrayList<>();
            for (Future<IdempotencyService.Reservation> future : futures) {
                outcomes.add(future.get());
            }

            assertThat(actionsRun.get())
                    .as("exactly one concurrent caller may run the action")
                    .isEqualTo(1);
            assertThat(outcomes).filteredOn(r -> r == IdempotencyService.Reservation.ACQUIRED).hasSize(1);
            assertThat(outcomes).filteredOn(r -> r == IdempotencyService.Reservation.IN_PROGRESS)
                    .hasSize(threads - 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void executeRefusesToRunAnActionItDoesNotOwn() {
        String key = newKey();
        idempotencyService.reserve(key, "test_tool", idempotencyService.fingerprint("test_tool", "args"));

        AtomicInteger ran = new AtomicInteger();

        assertThatThrownBy(() -> idempotencyService.execute(
                key, "test_tool", "args", () -> {
                    ran.incrementAndGet();
                    return "should not happen";
                }))
                .isInstanceOf(IdempotencyInProgressException.class);

        assertThat(ran.get()).isZero();
    }

    @Test
    void executeReplaysACompletedRequestWithoutRunningItAgain() {
        String key = newKey();
        AtomicInteger ran = new AtomicInteger();

        idempotencyService.execute(key, "test_tool", "args", () -> {
            ran.incrementAndGet();
            return "first";
        });
        idempotencyService.complete(key, "\"first\"");

        var outcome = idempotencyService.execute(key, "test_tool", "args", () -> {
            ran.incrementAndGet();
            return "second";
        });

        assertThat(outcome.replayed()).isTrue();
        assertThat(outcome.storedResultJson()).isEqualTo("\"first\"");
        assertThat(ran.get()).isEqualTo(1);
    }
}
