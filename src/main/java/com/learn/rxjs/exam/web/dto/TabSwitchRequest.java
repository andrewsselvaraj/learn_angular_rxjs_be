package com.learn.rxjs.exam.web.dto;

/** hiddenAt is the client time (ISO-8601) when document.hidden became true. Optional. */
public record TabSwitchRequest(String hiddenAt) {
}
