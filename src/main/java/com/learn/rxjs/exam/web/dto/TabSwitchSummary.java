package com.learn.rxjs.exam.web.dto;

import com.learn.rxjs.exam.model.TabSwitchEvent;
import java.util.List;

public record TabSwitchSummary(
        String sessionId,
        int count,
        int maxAllowed,
        int remainingAllowed,
        boolean limitExceeded,
        List<TabSwitchEvent> events) {
}
