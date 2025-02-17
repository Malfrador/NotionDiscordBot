# Discord / Notion bot
A bot that handles bug reporting from Discord via forms, as well as sending notifications to Discord for Notion updates.

The bug reporting form will be automatically populated from the properties in the configured Notion database. Currently, `Text`, `Select` and `Checkbox` are supported. Up to four properties are supported, 
as Discord limits the number of components to five (four properties plus a "Submit" button).

Sadly, Notion does not provide any Webhooks or other notification mechanisms, so for the notification feature of the bot will poll the database for changes every `database-query-interval` seconds.

This project uses [JDA](https://github.com/discord-jda/JDA) for Discord integration and [Notion SDK JVM](https://github.com/seratch/notion-sdk-jvm) for Notion integration.
[Logback](http://logback.qos.ch/) is used for logging. A `latest.log` file will be created in the same directory as the jar file.

![Photos_V0rW05l5Kj](https://github.com/user-attachments/assets/50109f9d-324d-4800-a171-6229569906b6)


## Usage
Requirements:
* Java 21

Run the jar file with `java -jar discord-notion-bot.jar`. 
The bot will create a config.yml file in the same directory as the jar file if it does not exist.
Configure the Discord and Notion tokens as well as the channel IDs.
## Configuration 
The config file uses YAML, so make sure the indentation is correct when editing it.
```yaml
discord-token: # Add the discord token here. https://discord.com/developers/applications
notion-token: # Add the notion token here: https://www.notion.so/my-integrations
discord-bot-setup:
    discord-reporting-channel-id: # Channel ID for the bug reporting channel. Right-click on the channel to copy the ID
    discord-reporting-message-id: # Used by the bot to store the bug reporting message. Do not change this value manually
    discord-notification-channel-id: # Channel ID for the notion notification channel. 
discord-messages:
    reporting-text: # The text shown in the bug reporting message
notion-setup:
    monitored-databases: # A list of notion database ID
    - # Database UUID. The UUID is the first part of the "Share" URL e.g., https://www.notion.so/19c52a80a64580659aefd31dabb24fba
    database-query-interval: # How often to query the database for the notification channel, in seconds
    database-query-size: # How many items to query from the database at a time
    notion-database-uuid: # The UUID of the database for the bug reporting. The UUID is the first part of the "Share" URL e.g., https://www.notion.so/19c52a80a64580659aefd31dabb24fba
```

## Building
This project uses Gradle. The Gradle wrapper is included in the repository, so you do not need to install anything. If no JDK 21 is found, the wrapper will download it for you.

Run `gradlew.bat shadowJar` (or `./gradlew shadowJar` on Linux) to build the jar file. The jar file will be located in `build/libs/`.
