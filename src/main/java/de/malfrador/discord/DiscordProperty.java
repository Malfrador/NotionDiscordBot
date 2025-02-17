package de.malfrador.discord;

import notion.api.v1.model.common.PropertyType;

public record DiscordProperty(String discordComponentID, String notionID, PropertyType notionPropertyType) {
}
