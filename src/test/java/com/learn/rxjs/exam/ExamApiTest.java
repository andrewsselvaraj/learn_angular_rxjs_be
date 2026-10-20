package com.learn.rxjs.exam;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.simulation.enabled=false", "app.simulation.submit-delay-ms=0"})
@AutoConfigureMockMvc
class ExamApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @BeforeEach
    void reset() throws Exception {
        mvc.perform(post("/api/admin/reset")).andExpect(status().isOk());
    }

    @Test
    void startScreenSourcesAreAvailable() throws Exception {
        mvc.perform(get("/api/exams/EXAM-101/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)))
                .andExpect(jsonPath("$[0].correctOption").doesNotExist());
        mvc.perform(get("/api/students/me")).andExpect(jsonPath("$.id").value("S1001"));
        mvc.perform(get("/api/exams/EXAM-101/settings"))
                .andExpect(jsonPath("$.durationSeconds").value(1800))
                .andExpect(jsonPath("$.warningAtSeconds").value(300));
        mvc.perform(get("/api/exams/NOPE/settings")).andExpect(status().isNotFound());
    }

    @Test
    void saveRetriesAfterSimulatedFailures() throws Exception {
        String sessionId = startSession();
        mvc.perform(put("/api/chaos").contentType(MediaType.APPLICATION_JSON).content("{\"failNextSaves\":2}"));
        String body = "{\"answers\":{\"Q1\":\"B\",\"Q2\":\"D\"}}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/sessions/{id}/answers", sessionId).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isServiceUnavailable());
        }
        mvc.perform(post("/api/sessions/{id}/answers", sessionId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answeredCount").value(2));
        mvc.perform(get("/api/sessions/{id}/state", sessionId))
                .andExpect(jsonPath("$.answers.Q2").value("D"))
                .andExpect(jsonPath("$.totalQuestions").value(20));
    }

    @Test
    void submitIsIdempotentAndCountsRequests() throws Exception {
        String sessionId = startSession();
        mvc.perform(post("/api/sessions/{id}/submit", sessionId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"Q1\":\"B\"},\"reason\":\"TIMER\"}"))
                .andExpect(jsonPath("$.alreadySubmitted").value(false))
                .andExpect(jsonPath("$.result.score").value(5))
                .andExpect(jsonPath("$.result.reason").value("TIMER"));
        mvc.perform(post("/api/sessions/{id}/submit", sessionId))
                .andExpect(jsonPath("$.alreadySubmitted").value(true))
                .andExpect(jsonPath("$.submitRequestCount").value(2));
        mvc.perform(post("/api/sessions/{id}/answers", sessionId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"Q2\":\"A\"}}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/exams/EXAM-101/leaderboard"))
                .andExpect(jsonPath("$.entries[?(@.studentId=='S1001')].status").value("SUBMITTED"));
    }

    @Test
    void tabSwitchesAndWarningAreTracked() throws Exception {
        String sessionId = startSession();
        mvc.perform(post("/api/sessions/{id}/events/tab-switch", sessionId)).andExpect(jsonPath("$.count").value(1));
        mvc.perform(post("/api/sessions/{id}/events/tab-switch", sessionId)).andExpect(jsonPath("$.count").value(2));
        mvc.perform(post("/api/sessions/{id}/events/time-warning", sessionId)).andExpect(jsonPath("$.firstTime").value(true));
        mvc.perform(post("/api/sessions/{id}/events/time-warning", sessionId)).andExpect(jsonPath("$.firstTime").value(false));
    }

    @Test
    void scenarioCatalogueListsAllNine() throws Exception {
        mvc.perform(get("/api/scenarios"))
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[0].title").value("Countdown timer"))
                .andExpect(jsonPath("$[5].rxjs").value("submitClicks$.pipe(exhaustMap(() => http.post('/submit', answers)))"));
        mvc.perform(get("/api/scenarios/8")).andExpect(jsonPath("$.key").value("FIVE_MINUTE_WARNING"));
        mvc.perform(get("/api/scenarios/10")).andExpect(status().isNotFound());
    }

    @Test
    void corsAllowsAngularDevServer() throws Exception {
        mvc.perform(options("/api/exams/EXAM-101/questions")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    private String startSession() throws Exception {
        String response = mvc.perform(post("/api/exams/EXAM-101/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"S1001\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("sessionId").asText();
    }
}
