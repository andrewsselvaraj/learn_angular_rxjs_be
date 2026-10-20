package com.learn.rxjs.exam.service;

import com.learn.rxjs.exam.web.dto.ChaosConfig;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/** Switches for simulating a flaky network, so retry() and the offline banner can be practised. */
@Service
public class ChaosService {

    private final AtomicInteger failNextSaves = new AtomicInteger();
    private final AtomicInteger failNextRequests = new AtomicInteger();
    private final AtomicInteger extraLatencyMs = new AtomicInteger();

    public ChaosConfig current() {
        return new ChaosConfig(failNextSaves.get(), failNextRequests.get(), extraLatencyMs.get());
    }

    public ChaosConfig update(ChaosConfig config) {
        if (config.failNextSaves() != null) {
            failNextSaves.set(Math.max(0, config.failNextSaves()));
        }
        if (config.failNextRequests() != null) {
            failNextRequests.set(Math.max(0, config.failNextRequests()));
        }
        if (config.extraLatencyMs() != null) {
            extraLatencyMs.set(Math.clamp(config.extraLatencyMs(), 0, 15000));
        }
        return current();
    }

    public void reset() {
        failNextSaves.set(0);
        failNextRequests.set(0);
        extraLatencyMs.set(0);
    }

    public int extraLatencyMs() {
        return extraLatencyMs.get();
    }

    public boolean consumeSaveFailure() {
        return consume(failNextSaves);
    }

    public boolean consumeRequestFailure() {
        return consume(failNextRequests);
    }

    private static boolean consume(AtomicInteger counter) {
        return counter.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0;
    }
}
