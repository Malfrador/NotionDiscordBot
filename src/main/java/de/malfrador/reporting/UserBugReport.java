package de.malfrador.reporting;

import java.util.Map;

public record UserBugReport(String user, Map<String, String> data) {
}
