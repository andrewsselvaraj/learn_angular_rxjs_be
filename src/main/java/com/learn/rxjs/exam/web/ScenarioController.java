package com.learn.rxjs.exam.web;

import com.learn.rxjs.exam.model.RxjsScenario;
import com.learn.rxjs.exam.service.ApiException;
import com.learn.rxjs.exam.web.dto.ScenarioInfo;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catalogue of the nine RxJS scenarios and which endpoints back each one. */
@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    @GetMapping
    public List<ScenarioInfo> scenarios() {
        return Arrays.stream(RxjsScenario.values()).map(ScenarioInfo::of).toList();
    }

    @GetMapping("/{number}")
    public ScenarioInfo scenario(@PathVariable int number) {
        return Arrays.stream(RxjsScenario.values())
                .filter(s -> s.number() == number)
                .findFirst()
                .map(ScenarioInfo::of)
                .orElseThrow(() -> ApiException.notFound("Scenario", String.valueOf(number)));
    }
}
