package de.malfrador.reporting;

import notion.api.v1.model.common.PropertyType;
import org.jetbrains.annotations.Nullable;

public record ReportingProperty(String id, PropertyType type, @Nullable String... availableValues) {
}
