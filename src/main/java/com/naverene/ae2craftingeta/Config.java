package com.naverene.ae2craftingeta;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    /** Seconds assumed for one batch of a processing pattern that has never been seen running. */
    public static double unknownBatchSeconds = 5.0;
    public static boolean showTerminalTooltips = true;
    public static boolean showCraftingScreenEta = true;
    public static boolean showStuckSection = true;
    public static boolean showMachinesSection = true;
    public static boolean showTpsNote = true;
    public static int maxItemsPerSection = 6;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        unknownBatchSeconds = configuration
            .get(
                Configuration.CATEGORY_GENERAL,
                "unknownBatchSeconds",
                unknownBatchSeconds,
                "Seconds assumed for one batch of a processing pattern that has never run yet",
                0.05,
                3600.0)
            .getDouble();
        showTerminalTooltips = configuration.getBoolean(
            "showTerminalTooltips",
            Configuration.CATEGORY_GENERAL,
            showTerminalTooltips,
            "Show how long an item last took to craft in ME terminal tooltips");
        showCraftingScreenEta = configuration.getBoolean(
            "showCraftingScreenEta",
            Configuration.CATEGORY_GENERAL,
            showCraftingScreenEta,
            "Show the estimated time on the crafting plan and crafting status screens");
        showStuckSection = configuration.getBoolean(
            "showStuckSection",
            Configuration.CATEGORY_GENERAL,
            showStuckSection,
            "List stuck items and why they are waiting in the crafting status screen's info tooltip");
        showMachinesSection = configuration.getBoolean(
            "showMachinesSection",
            Configuration.CATEGORY_GENERAL,
            showMachinesSection,
            "List what each item's machines are doing in the crafting status screen's info tooltip");
        showTpsNote = configuration.getBoolean(
            "showTpsNote",
            Configuration.CATEGORY_GENERAL,
            showTpsNote,
            "Remind that estimates assume 20 TPS in the info tooltips");
        maxItemsPerSection = configuration.getInt(
            "maxItemsPerSection",
            Configuration.CATEGORY_GENERAL,
            maxItemsPerSection,
            1,
            100,
            "Most items listed in each info tooltip section before the rest are summed up as \"and N more\"");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
