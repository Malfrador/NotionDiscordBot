package de.malfrador.reporting;

import notion.api.v1.model.common.PropertyType;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a property for the reporting discord form, with its Notion PropertyType and available values
 */
public record ReportingProperty(String id, PropertyType type, @Nullable String... availableValues) {
}
