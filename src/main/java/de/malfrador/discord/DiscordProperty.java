package de.malfrador.discord;

import notion.api.v1.model.common.PropertyType;

/**
 * Used for mapping Discord components to Notion properties
 */
public record DiscordProperty(String discordComponentID, String notionID, PropertyType notionPropertyType) {
}
