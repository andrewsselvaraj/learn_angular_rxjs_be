package com.learn.rxjs.exam.web.dto;

import com.learn.rxjs.exam.model.RxjsScenario;
import java.util.List;

public record ScenarioInfo(int number, String key, String title, String description, String rxjs, List<String> endpoints) {

    public static ScenarioInfo of(RxjsScenario s) {
        return new ScenarioInfo(s.number(), s.name(), s.title(), s.description(), s.rxjs(), s.endpoints());
    }
}
