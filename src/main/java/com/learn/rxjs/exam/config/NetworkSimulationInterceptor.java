package com.learn.rxjs.exam.config;

import com.learn.rxjs.exam.service.ApiException;
import com.learn.rxjs.exam.service.ChaosService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Lets any /api call be slowed down or failed on demand:
 * ?delayMs=1500 adds latency to this request, ?failWith=500 makes it fail with that status,
 * and the global switches in ChaosService apply to every request.
 */
@Component
public class NetworkSimulationInterceptor implements HandlerInterceptor {

    private static final int MAX_DELAY_MS = 15000;

    private final ChaosService chaos;

    public NetworkSimulationInterceptor(ChaosService chaos) {
        this.chaos = chaos;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        long delay = chaos.extraLatencyMs() + parseInt(request.getParameter("delayMs"), 0);
        if (delay > 0) {
            try {
                Thread.sleep(Math.min(delay, MAX_DELAY_MS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (chaos.consumeRequestFailure()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SIMULATED_FAILURE", "Simulated network failure");
        }
        int failWith = parseInt(request.getParameter("failWith"), 0);
        if (failWith >= 400 && failWith <= 599) {
            HttpStatus status = HttpStatus.resolve(failWith);
            throw new ApiException(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR, "SIMULATED_FAILURE",
                    "Simulated failure requested with failWith=" + failWith);
        }
        return true;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
