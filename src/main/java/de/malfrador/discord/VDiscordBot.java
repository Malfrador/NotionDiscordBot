package de.malfrador.discord;

import de.malfrador.Main;
import de.malfrador.VConfig;
import de.malfrador.notion.VNotionManager;
import de.malfrador.reporting.ReportingProperty;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import notion.api.v1.model.common.PropertyType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VDiscordBot implements EventListener {

    private static final Logger LOG = LoggerFactory.getLogger(VDiscordBot.class);

    private final String token;
    private final VConfig config = Main.config;
    private JDA jda;
    private final VNotionManager notionManager;
    private final List<ReportingProperty> properties;
    private final Map<String, Map<String, String>> selectedByDiscordUser = new HashMap<>();

    public VDiscordBot(String token, List<ReportingProperty> properties, VNotionManager notionManager) {
        this.token = token;
        this.properties = properties;
        this.notionManager = notionManager;
        setup();
    }

    private void setup() {
        try {
            jda = JDABuilder.createLight(token, EnumSet.of(GatewayIntent.GUILD_MESSAGES)).build();
            jda.addEventListener(this);
            jda.awaitReady();
            MessageChannel reportingChannel = jda.getTextChannelById(config.discord.discordReportingChannelID);
            if (reportingChannel == null) {
                throw new RuntimeException("Failed to find reporting channel with ID " + config.discord.discordReportingChannelID + ", please check the config");
            }
            // Check if we have an existing message to use with the ID saved in the config
            String configMsgID = config.discord.discordReportingMessageID;
            Message message = null;
            if (configMsgID != null && !configMsgID.isEmpty() && !configMsgID.equals("discord-reporting-message-id")) {
                try {
                    message = reportingChannel.retrieveMessageById(configMsgID).complete();
                } catch (Exception e) {
                    LOG.warn("Failed to retrieve existing reporting message with ID {}, it might have been deleted. Creating new message...", configMsgID);
                }
            }
            if (message == null) {
                LOG.info("No existing reporting message found, creating a new one");
                MessageCreateBuilder builder = new MessageCreateBuilder();
                builder.setContent(config.discordMessages.reportingText);
                builder.setComponents(createSelectionsFromProperties());
                reportingChannel.sendMessage(builder.build()).submit().thenAccept(msg -> {
                    config.discord.discordReportingMessageID = msg.getId();
                    config.saveConfig();
                });
            }

            LOG.info("Discord bot ready and waiting");
        } catch (InterruptedException e) {
            LOG.error("Discord bot thread interrupted", e);
        } catch (Exception e) {
            LOG.error("Failed to start Discord bot", e);
        }
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof ButtonInteractionEvent buttonInteractionEvent) {
            if (buttonInteractionEvent.getComponentId().equals("finishReport")) {
                Map<String, String> properties = selectedByDiscordUser.get(buttonInteractionEvent.getUser().getId());
                if (properties == null) {
                    buttonInteractionEvent.reply("You have not selected any properties yet").setEphemeral(true).queue();
                    return;
                }
                // Insert the properties into the Notion database
                Map<String, String> props = selectedByDiscordUser.get(buttonInteractionEvent.getUser().getId());
                props.put("Name", buttonInteractionEvent.getUser().getAsTag());
                notionManager.insertIntoDatabase(props);
                buttonInteractionEvent.reply("Report submitted!").setEphemeral(true).queue();
            } else {
                // Check if this is a RichText property button
                ReportingProperty property = properties.stream()
                        .filter(p -> p.id().equals(buttonInteractionEvent.getComponentId()))
                        .findFirst()
                        .orElse(null);

                if (property != null && property.type() == PropertyType.RichText) {
                    // Create a modal for the user to enter text. This is a workaround for the lack of text input in normal components
                    TextInput textInput = TextInput.create(property.id(), property.id(), TextInputStyle.PARAGRAPH)
                            .setPlaceholder("Enter your text here...")
                            .setMinLength(1)
                            .setMaxLength(2000)
                            .build();

                    Modal modal = Modal.create("text_" + property.id(), "Enter " + property.id())
                            .addActionRow(textInput)
                            .build();

                    buttonInteractionEvent.replyModal(modal).queue();
                    return;
                }

                selectForUser(buttonInteractionEvent.getUser().getId(), buttonInteractionEvent.getComponentId(), buttonInteractionEvent.getComponentId());
                buttonInteractionEvent.deferEdit().queue();
            }
        }
        if (event instanceof StringSelectInteractionEvent selectInteractionEvent) {
            selectForUser(selectInteractionEvent.getUser().getId(), selectInteractionEvent.getComponentId(), selectInteractionEvent.getSelectedOptions().get(0).getValue());
            selectInteractionEvent.deferEdit().queue();
            // If all properties are selected, enable the finish button
            if (selectedByDiscordUser.get(selectInteractionEvent.getUser().getId()).size() == properties.size()) {
                updateComponentsWithSelections(selectInteractionEvent.getMessage(), selectInteractionEvent.getUser().getId());
            }
        }
        if (event instanceof ModalInteractionEvent modalEvent) {
            String propertyId = modalEvent.getModalId().substring(5); // Remove "text_" prefix
            String text = modalEvent.getValue(propertyId).getAsString();

            selectForUser(modalEvent.getUser().getId(), propertyId, text);
            modalEvent.deferEdit().queue();

            // Check if all properties are now selected
            if (selectedByDiscordUser.get(modalEvent.getUser().getId()).size() == properties.size()) {
                Message message = modalEvent.getMessage();
                if (message != null) {
                    updateComponentsWithSelections(message, modalEvent.getUser().getId());
                }
            }
        }
    }

    /**
     * Update the message components with the user's selections.
     * The user would not be able to see what they have selected after we update the message otherwise.
     */
    private void updateComponentsWithSelections(Message message, String userId) {
        List<ActionRow> updatedRows = new ArrayList<>();
        Map<String, String> userSelections = selectedByDiscordUser.get(userId);

        for (ActionRow row : message.getActionRows()) {
            List<ItemComponent> components = new ArrayList<>();
            for (var component : row.getComponents()) {
                if (component instanceof Button button && button.getId().equals("finishReport")) {
                    components.add(button.asEnabled());
                } else if (component instanceof StringSelectMenu menu) {
                    String selectedValue = userSelections.get(menu.getId());
                    if (selectedValue != null) {
                        components.add(menu.createCopy()
                                .setDefaultValues(Collections.singleton(selectedValue))
                                .build());
                    } else {
                        components.add(component);
                    }
                } else {
                    components.add(component);
                }
            }
            updatedRows.add(ActionRow.of(components));
        }
        message.editMessageComponents(updatedRows).queue();
    }

    /**
     * Store the selected property for a Discord user in a map
     */
    private void selectForUser(String userId, String notionPropId, String value) {
        Map<String, String> userSelections =
                selectedByDiscordUser.computeIfAbsent(userId, k -> new HashMap<>());
        userSelections.put(notionPropId, value);
    }


    /**
     * Create Discord selection components based on the properties available in the Notion database
     */
    private Collection<ActionRow> createSelectionsFromProperties() {
        List<ActionRow> rows = new ArrayList<>();
        for (ReportingProperty property : properties) {
            ActionRow row = null;
            switch (property.type()) {
                case Select:
                    List<SelectOption> options = new ArrayList<>();
                    if (property.availableValues() == null) {
                        LOG.warn("Select property {} has no options. Add some in the Notion database", property.id());
                        break;
                    }
                    for (String value : property.availableValues()) {
                        options.add(SelectOption.of(value, value));
                    }
                    row = ActionRow.of(
                            StringSelectMenu.create(property.id())
                                    .addOptions(options)
                                    .setPlaceholder(property.id())
                                    .build()
                    );
                    break;
                case Checkbox:
                    row = ActionRow.of(
                            StringSelectMenu.create(property.id())
                                    .addOption("Yes", "yes")
                                    .addOption("No", "no")
                                    .setPlaceholder(property.id())
                                    .build()
                    );
                    break;
                case RichText:
                    row = ActionRow.of(
                            Button.primary(property.id(), property.id())
                                    .withEmoji(Emoji.fromUnicode("📝"))
                    );
                default:
                    break;
            }
            rows.add(row);
        }
        Button finishButton = Button.primary("finishReport", "Finish report")
                .withEmoji(Emoji.fromUnicode("✅"))
                .asDisabled();
        rows.add(ActionRow.of(finishButton));
        return rows;
    }

    public JDA getJda() {
        return jda;
    }
}
