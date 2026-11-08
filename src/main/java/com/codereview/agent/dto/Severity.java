package com.codereview.agent.dto;

/** Closed set of severities so Claude cannot invent labels like "kinda bad". */
public enum Severity {
    CRITICAL, HIGH, MEDIUM, LOW, INFO
}