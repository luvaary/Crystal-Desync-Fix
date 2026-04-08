package com.cpvpdesyncfix.config;

import com.cpvpdesyncfix.CPVPDesyncFix;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cpvp-desync-fix.json");

    private ConfigManager() {
    }

    public static ModConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            ModConfig defaultConfig = new ModConfig();
            defaultConfig.sanitize();
            save(defaultConfig);
            return defaultConfig;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
            if (loaded == null) {
                CPVPDesyncFix.logger().warn("Config file was empty, using defaults.");
                loaded = new ModConfig();
            }

            boolean changed = loaded.sanitize();
            if (changed) {
                save(loaded);
            }
            return loaded;
        } catch (IOException | JsonSyntaxException exception) {
            CPVPDesyncFix.logger().error("Failed to load config, using defaults.", exception);
            ModConfig fallback = new ModConfig();
            fallback.sanitize();
            return fallback;
        }
    }

    public static void save(ModConfig config) {
        if (config == null) {
            return;
        }

        config.sanitize();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            CPVPDesyncFix.logger().error("Failed to save config.", exception);
        }
    }
}