package com.naverene.ae2craftingeta;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    /** Seconds assumed for one batch of a processing pattern that has never been seen running. */
    public static double unknownBatchSeconds = 5.0;
    public static boolean showTerminalTooltips = true;
    public static boolean showCraftingScreenEta = true;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        unknownBatchSeconds = configuration.get(
                Configuration.CATEGORY_GENERAL,
                "unknownBatchSeconds",
                unknownBatchSeconds,
                "Seconds assumed for one batch of a processing pattern that has never run yet",
                0.05,
                3600.0).getDouble();
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

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
