package com.learn.rxjs.exam.model;

import java.util.List;

/** A question as sent to the browser. The correct answer is never included. */
public record Question(String id, int number, String topic, String text, List<Option> options, int marks) {
}
