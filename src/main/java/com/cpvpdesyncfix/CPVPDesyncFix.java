package com.cpvpdesyncfix;

import com.cpvpdesyncfix.config.ConfigManager;
import com.cpvpdesyncfix.config.ModConfig;
import com.cpvpdesyncfix.feature.CrystalSyncManager;
import com.cpvpdesyncfix.feature.InterpolationSmoother;
import com.cpvpdesyncfix.util.LatencyJitterTracker;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CPVPDesyncFix implements ClientModInitializer {
    public static final String MOD_ID = "cpvpdesyncfix";
    private static final Logger LOGGER = LoggerFactory.getLogger("CPVP Desync Fix");
    private static final long ERROR_LOG_COOLDOWN_MS = 5_000L;

    private static ModConfig config = new ModConfig();
    private static long lastErrorLogMs;
    private static long tickIndex;

    private static final CrystalSyncManager CRYSTAL_SYNC_MANAGER = new CrystalSyncManager();
    private static final LatencyJitterTracker LATENCY_JITTER_TRACKER = new LatencyJitterTracker();
    private static final InterpolationSmoother INTERPOLATION_SMOOTHER = new InterpolationSmoother(
            LATENCY_JITTER_TRACKER,
            CRYSTAL_SYNC_MANAGER
    );

    @Override
    public void onInitializeClient() {
        config = ConfigManager.load();
        config.sanitize();
        LOGGER.info("{} initialized in background-only mode", MOD_ID);
    }

    public static ModConfig config() {
        return config;
    }

    public static CrystalSyncManager crystalSyncManager() {
        return CRYSTAL_SYNC_MANAGER;
    }

    public static LatencyJitterTracker latencyJitterTracker() {
        return LATENCY_JITTER_TRACKER;
    }

    public static void saveConfig() {
        ConfigManager.save(config);
    }

    public static void onClientTick(MinecraftClient client) {
        if (client == null) {
            return;
        }

        try {
            LATENCY_JITTER_TRACKER.tick(client, config);
            CRYSTAL_SYNC_MANAGER.cleanup(config);
            INTERPOLATION_SMOOTHER.tick(config);

            if (config.enableDebugLogging) {
                tickIndex++;
                int interval = Math.max(40, config.debugLogIntervalTicks);
                if (tickIndex % interval == 0) {
                    LOGGER.info(
                            "cpvp status={} net={}",
                            CRYSTAL_SYNC_MANAGER.backgroundStatus(),
                            LATENCY_JITTER_TRACKER.statusSummary()
                    );
                }
            }
        } catch (Throwable exception) {
            logFeatureFailure("onClientTick", exception);
        }
    }

    public static void onClientWorldEntityTick(ClientWorld world) {
        if (world == null) {
            return;
        }
        try {
            CRYSTAL_SYNC_MANAGER.scanWorld(world, config);
        } catch (Throwable exception) {
            logFeatureFailure("onClientWorldEntityTick", exception);
        }
    }

    public static void onLocalAttack(MinecraftClient client) {
        try {
            if (client == null || client.crosshairTarget == null) {
                return;
            }
            if (!(client.crosshairTarget instanceof EntityHitResult entityHitResult)) {
                return;
            }

            Entity target = entityHitResult.getEntity();
            if (target instanceof EndCrystalEntity endCrystalEntity) {
                CRYSTAL_SYNC_MANAGER.predictLocalBreak(endCrystalEntity, config);
            }
        } catch (Throwable exception) {
            logFeatureFailure("onLocalAttack", exception);
        }
    }

    public static boolean shouldRenderCrystal(EndCrystalEntity crystal) {
        try {
            if (!config.enableCrystalCleanup) {
                return true;
            }
            return CRYSTAL_SYNC_MANAGER.shouldRender(crystal, config);
        } catch (Throwable exception) {
            logFeatureFailure("shouldRenderCrystal", exception);
            return true;
        }
    }

    public static int adjustInterpolationSteps(Entity entity, int interpolationSteps, double x, double y, double z) {
        try {
            return INTERPOLATION_SMOOTHER.adjustInterpolationSteps(entity, interpolationSteps, x, y, z, config);
        } catch (Throwable exception) {
            logFeatureFailure("adjustInterpolationSteps", exception);
            return interpolationSteps;
        }
    }

    public static Logger logger() {
        return LOGGER;
    }

    private static void logFeatureFailure(String area, Throwable exception) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLogMs < ERROR_LOG_COOLDOWN_MS) {
            return;
        }
        lastErrorLogMs = now;
        LOGGER.error("{} failed in {}", MOD_ID, area, exception);
    }
}
