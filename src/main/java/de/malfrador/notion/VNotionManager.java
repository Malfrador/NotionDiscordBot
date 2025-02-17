package de.malfrador.notion;

import de.malfrador.Main;
import de.malfrador.VConfig;
import de.malfrador.discord.DiscordProperty;
import de.malfrador.discord.VDiscordBot;
import de.malfrador.reporting.ReportingProperty;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import notion.api.v1.NotionClient;
import notion.api.v1.logging.NotionLogger;
import notion.api.v1.model.blocks.ParagraphBlock;
import notion.api.v1.model.common.PropertyType;
import notion.api.v1.model.databases.Database;
import notion.api.v1.model.databases.DatabaseProperty;
import notion.api.v1.model.databases.query.sort.QuerySort;
import notion.api.v1.model.databases.query.sort.QuerySortDirection;
import notion.api.v1.model.databases.query.sort.QuerySortTimestamp;
import notion.api.v1.model.pages.PageParent;
import notion.api.v1.model.pages.PageProperty;
import notion.api.v1.request.pages.CreatePageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VNotionManager {

    private static final Logger LOG = LoggerFactory.getLogger(VNotionManager.class);

    private final Map<String, String> lastPageIds = new HashMap<>();
    private final Instant startupTime = Instant.now();

    private final String token;
    private final VConfig config = Main.config;
    private Database database;
    private NotionClient client;
    private final List<DiscordProperty> discordProperties = new ArrayList<>();
    VDiscordBot discordBotThread;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();


    public VNotionManager(String token) {
        this.token = token;
    }

    public void start() {
        this.client = new NotionClient(token);
        client.setLogger(new DummyLogger());
        LOG.info("Logged in to Notion API with token {}", token);
        String dbUUID = config.notion.notionDatabaseUUID;
        if (dbUUID == null || dbUUID.isEmpty() || dbUUID.equals("notion-database-uuid")) {
            LOG.error("Please set the Notion database UUID in the config");
            return;
        }

        try {
            this.database = client.retrieveDatabase(dbUUID);
        } catch (Exception e) {
            LOG.error("Failed to retrieve Notion database with UUID {}, please check the config. Error: {}", dbUUID, e.getMessage());
            return;
        }

        loadProperties();
        runScheduler(); // Start the scheduler
    }

    public void runScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                for (String dbId : config.notion.monitoredDatabases) {
                    checkForNewPages(dbId);
                }
            } catch (Exception e) {
                LOG.error("Error while checking for new pages", e);
            }
        }, 0, config.notion.databaseQueryInterval, TimeUnit.SECONDS);
    }

    /*
     * Shutdown the scheduler, just in case
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Insert a new page into the Notion database.
     * This is used by the Discord bot to insert bug reports.
     */
    public void insertIntoDatabase(Map<String, String> properties) {
        if (database == null) {
            LOG.error("Notion database is not loaded yet, skipping insert");
            return;
        }
        Map<String, PageProperty> propertiesMap = new HashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            PropertyType propertyType = getPropertyType(entry.getKey());
            if (propertyType == null) {
                LOG.warn("Property {} not found in Notion database, skipping", entry.getKey());
                continue;
            }
            PageProperty property = new PageProperty();
            switch (propertyType) {
                case RichText:
                    PageProperty.RichText richText = new PageProperty.RichText();
                    richText.setText(new PageProperty.RichText.Text(entry.getValue()));
                    property.setRichText(Collections.singletonList(richText));
                    break;
                case Checkbox:
                    property.setCheckbox(Boolean.parseBoolean(entry.getValue()));
                    break;
                case Select:
                    DatabaseProperty.Select.Option option = database.getProperties().get(entry.getKey()).getSelect().getOptions().stream()
                            .filter(o -> o.getName().equals(entry.getValue()))
                            .findFirst()
                            .orElse(null);
                    property.setSelect(option);
                    break;
                case Title:
                    PageProperty.RichText titleText = new PageProperty.RichText();
                    titleText.setText(new PageProperty.RichText.Text(entry.getValue()));
                    property.setTitle(Collections.singletonList(titleText));
                    break;
                default:
                    LOG.warn("Property {} has unsupported type {}, skipping", entry.getKey(), propertyType);
            }
            propertiesMap.put(entry.getKey(), property);
        }
        PageParent parent = PageParent.database(database.getId());
        CreatePageRequest createPageRequest = new CreatePageRequest(parent, propertiesMap);
        client.createPage(createPageRequest);
    }

    /**
     * Get the property type of a property in the Notion database
     */
    private PropertyType getPropertyType(String notionID) {
        for (DiscordProperty property : discordProperties) {
            if (property.notionID().equals(notionID)) {
                return property.notionPropertyType();
            }
        }
        return null;
    }

    /**
     * Load the properties available in the Notion database
     */
    private void loadProperties() {
        List<ReportingProperty> properties = new ArrayList<>(List.of());
        for (Map.Entry<String, DatabaseProperty> entry : database.getProperties().entrySet()) {
            discordProperties.add(
                    new DiscordProperty(
                            entry.getKey(),
                            entry.getKey(),
                            entry.getValue().getType()
                    )
            );
            switch (entry.getValue().getType()) {
                case RichText, Checkbox -> properties.add(new ReportingProperty(entry.getKey(), entry.getValue().getType()));
                case Select -> {
                    List<String> values = new ArrayList<>(List.of());
                    if (entry.getValue().getSelect() == null || entry.getValue().getSelect().getOptions() == null) {
                        LOG.warn("Select property {} has no options. Add some in the Notion database", entry.getKey());
                        break;
                    }
                    for (DatabaseProperty.Select.Option option : entry.getValue().getSelect().getOptions()) {
                        values.add(option.getName());
                    }
                    properties.add(new ReportingProperty(entry.getKey(), entry.getValue().getType(), values.toArray(new String[0])));
                }
                default -> {} // Ignore unsupported property types that we aren't able to show on Discord anyway
            }
        }
        LOG.info("Loaded {} properties from Notion database", properties.size());
        discordBotThread = new VDiscordBot(config.global.discordToken, properties, this);
    }

    /*
     * Query notion for new pages in the database.
     * If a new page is found, notify Discord
     */
    private void checkForNewPages(String databaseId) {
        Instant lastKnownTime = lastPageIds.containsKey(databaseId)
                ? Instant.parse(lastPageIds.getOrDefault(databaseId, ""))
                : startupTime;  // Check for pages created after startup, so we don't spam the channel

        var sort = new QuerySort();
        sort.setTimestamp(QuerySortTimestamp.LastEditedTime);
        sort.setDirection(QuerySortDirection.Descending);
        List<QuerySort> sorts = Collections.singletonList(sort);

        var results = client.queryDatabase(databaseId, null, sorts, null, config.notion.databaseQuerySize);
        if (results.getResults().isEmpty()) {
            LOG.info("No pages found in database {}", databaseId);
            return;
        }

        String newestEditTime = results.getResults().getFirst().getLastEditedTime();

        // Check for new pages and edits
        for (var page : results.getResults()) {
            Instant pageEditTime = Instant.parse(page.getLastEditedTime());
            if (pageEditTime.compareTo(lastKnownTime) <= 0) {
                break;
            }
            notifyDiscord(page, databaseId);
        }

        lastPageIds.put(databaseId, newestEditTime);
    }

    /*
     * Notify Discord about a new page or edit in the Notion database.
     * This will create an embed message with the page title, content, and properties.
     */
    private void notifyDiscord(notion.api.v1.model.pages.Page page, String databaseId) {
        MessageChannel channel = discordBotThread.getJda()
                .getTextChannelById(config.discord.notificationChannelId);
        if (channel == null) {
            LOG.error("Notification channel not found");
            return;
        }

        String title = extractTitle(page);
        String url = "https://notion.so/" + page.getId().replace("-", "");

        // Get database name
        Database db = client.retrieveDatabase(databaseId);
        String databaseName = db.getTitle().stream()
                .findFirst()
                .map(t -> t.getPlainText())
                .orElse("Unknown Database");

        // Get page content from blocks
        StringBuilder contentBuilder = new StringBuilder();
        var blocks = client.retrieveBlockChildren(page.getId(), null, 5);
        blocks.getResults().forEach(block -> {
            if (block.asParagraph() != null) {
                ParagraphBlock paragraph = block.asParagraph();
                paragraph.getParagraph().getRichText().forEach(richText -> contentBuilder.append(richText.getPlainText()));
            }
        });

        // Get properties content
        page.getProperties().forEach((key, prop) -> {
            if (prop.getType() == PropertyType.RichText && prop.getRichText() != null) {
                String text = prop.getRichText().stream()
                        .map(t -> t.getPlainText())
                        .filter(t -> !t.isEmpty())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
                if (!text.isEmpty()) {
                    contentBuilder.append("**").append(key).append("**:\n")
                            .append(text).append("\n\n");
                }
            }
        });

        String content = contentBuilder.toString().trim();

        Instant lastEdited = Instant.parse(page.getLastEditedTime());
        Instant created = Instant.parse(page.getCreatedTime());
        boolean isEdit = lastEdited.isAfter(created);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title, url)
                .setColor(isEdit ? Color.YELLOW : Color.GREEN)
                .setTimestamp(Instant.now())
                .addField("Database", databaseName, false)
                .addField("Type", isEdit ? "Page Edit" : "New Page", true)
                .addField("Last Edited", lastEdited.toString(), true);

        if (!content.isEmpty()) {
            if (content.length() > 500) { // Let's not spam the channel with huge messages
                content = content.substring(0, 500) + "...";
            }
            embed.addField("Content", content, false);
        }

        channel.sendMessageEmbeds(embed.build()).queue();
        LOG.info("Notified Discord about {} {} in database {}",
                isEdit ? "edited" : "new", title, databaseId);
    }

    /*
     * Extract the title of a Notion page, as a plain text string
     */
    private String extractTitle(notion.api.v1.model.pages.Page page) {
        var titleProp = page.getProperties().values().stream()
                .filter(p -> p.getType() == PropertyType.Title)
                .findFirst();

        return titleProp.map(p -> p.getTitle().isEmpty() ? "Untitled" : p.getTitle().get(0).getPlainText())
                .orElse("New Page");
    }
}
