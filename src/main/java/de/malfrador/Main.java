package de.malfrador;

import de.malfrador.notion.VNotionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    public static final VConfig config = new VConfig();
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        VNotionManager notionManager= new VNotionManager(config.global.notionToken);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            notionManager.shutdown();
            LOG.info("Notion manager shutdown complete");
        }));
        notionManager.start();
    }

}