package de.malfrador;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ClassEscapesDefinedScope")
public class VConfig {

    private static final Logger LOG = LoggerFactory.getLogger(VConfig.class);

    private YamlConfigurationLoader loader;

    private CommentedConfigurationNode root;
    public BotConfig global;
    public BotConfig.DiscordBotSetup discord;
    public BotConfig.DiscordMessages discordMessages;
    public BotConfig.NotionSetup notion;

    public VConfig() {
        loadConfig();
    }

    private void loadConfig() {
        loader = YamlConfigurationLoader.builder()
                .file(new File("config.yml"))
                .nodeStyle(NodeStyle.BLOCK)
                .defaultOptions(opts -> opts.shouldCopyDefaults(true))
                .build();
        try {
            root = loader.load();
            LOG.info("Loaded config.yml");
        } catch (IOException e) {
            LOG.error("Failed to load config.yml", e);
            throw new RuntimeException(e);
        }
        try {
            global = root.get(BotConfig.class);
            notion = global.notionSetup;
            discord = global.discordBotSetup;
            discordMessages = global.discordMessages;
        } catch (SerializationException e) {
            LOG.error("Failed to deserialize config.yml", e);
            throw new RuntimeException(e);
        }
        saveConfig();
    }

    public void saveConfig() {
        try {
            root.set(BotConfig.class, global);
        } catch (SerializationException e) {
            LOG.error("Failed to serialize config.yml", e);
            throw new RuntimeException(e);
        }
        try {
            loader.save(root);
        } catch (ConfigurateException e) {
            LOG.error("Failed to save config.yml", e);
            throw new RuntimeException(e);
        }
        LOG.info("Saved config.yml");
    }

    @ConfigSerializable
    public static class BotConfig {
        public String discordToken = "your-discord-token";
        public String notionToken = "your-notion-token";

        public DiscordBotSetup discordBotSetup = new DiscordBotSetup();
        public DiscordMessages discordMessages = new DiscordMessages();
        public NotionSetup notionSetup = new NotionSetup();

        @ConfigSerializable
        public static class DiscordBotSetup {
            @Setting(value = "discord-reporting-channel-id")
            public String discordReportingChannelID = "discord-reporting-channel-id";
            @Setting(value = "discord-reporting-message-id")
            public String discordReportingMessageID = "discord-reporting-message-id";
            @Setting(value = "discord-notification-channel-id")
            public String notificationChannelId = "discord-notification-channel-id";
        }

        @ConfigSerializable
        public static class NotionSetup {
            @Setting(value = "notion-database-uuid")
            public String notionDatabaseUUID = "notion-database-uuid";
            public List<String> monitoredDatabases = new ArrayList<>();
            public int databaseQueryInterval = 60;
            public int databaseQuerySize = 5;
        }

        @ConfigSerializable
        public static class DiscordMessages {
            public String reportingText = "Please click the button below to start reporting a bug";
        }

    }

}
