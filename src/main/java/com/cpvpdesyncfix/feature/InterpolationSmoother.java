package com.cpvpdesyncfix.feature;

import com.cpvpdesyncfix.config.ModConfig;
import com.cpvpdesyncfix.util.LatencyJitterTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class InterpolationSmoother {
    private final LatencyJitterTracker latencyJitterTracker;
    private final CrystalSyncManager crystalSyncManager;
    private final Map<Integer, EntityMotionState> stateByEntityId = new HashMap<>();

    public InterpolationSmoother(LatencyJitterTracker latencyJitterTracker, CrystalSyncManager crystalSyncManager) {
        this.latencyJitterTracker = latencyJitterTracker;
        this.crystalSyncManager = crystalSyncManager;
    }

    public void tick(ModConfig config) {
        long now = System.currentTimeMillis();
        int retentionMs = Math.max(2_500, config.motionStateRetentionMs);
        Iterator<Map.Entry<Integer, EntityMotionState>> iterator = stateByEntityId.entrySet().iterator();
        while (iterator.hasNext()) {
            EntityMotionState state = iterator.next().getValue();
            if (now - state.lastUpdateMs > retentionMs) {
                iterator.remove();
            }
        }

        int maxStates = Math.max(128, config.maxEntityMotionStates);
        if (stateByEntityId.size() > maxStates) {
            int toRemove = stateByEntityId.size() - maxStates;
            Iterator<Integer> keyIterator = stateByEntityId.keySet().iterator();
            while (toRemove > 0 && keyIterator.hasNext()) {
                keyIterator.next();
                keyIterator.remove();
                toRemove--;
            }
        }
    }

    public int adjustInterpolationSteps(Entity entity, int interpolationSteps, double x, double y, double z, ModConfig config) {
        if (!config.enableInterpolationSmoothing) {
            return interpolationSteps;
        }

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return interpolationSteps;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.equals(entity)) {
            return interpolationSteps;
        }

        if (!isRelevantEntity(entity)) {
            return interpolationSteps;
        }

        long now = System.currentTimeMillis();
        EntityMotionState motionState = stateByEntityId.computeIfAbsent(entity.getId(), ignored -> new EntityMotionState());

        int base = Math.max(1, config.baseInterpolationSteps);
        int max = Math.max(base, config.maxInterpolationSteps);
        int adjusted = Math.max(interpolationSteps, base);

        double snapDistance = Math.sqrt(entity.squaredDistanceTo(x, y, z));

        double packetDistance = 0.0D;
        if (motionState.hasTarget) {
            double dx = x - motionState.lastTargetX;
            double dy = y - motionState.lastTargetY;
            double dz = z - motionState.lastTargetZ;
            packetDistance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        }

        long dtMs = Math.max(1L, now - motionState.lastPacketMs);
        double packetSpeedPerTick = packetDistance / Math.max(0.05D, dtMs / 50.0D);
        double acceleration = Math.abs(packetSpeedPerTick - motionState.speedEma);

        motionState.speedEma = ema(motionState.speedEma, packetSpeedPerTick, 0.23D);
        motionState.accelerationEma = ema(motionState.accelerationEma, acceleration, 0.30D);

        double threshold = Math.max(0.25D, config.largeSnapThreshold);
        if (snapDistance >= threshold) {
            latencyJitterTracker.recordSnap(snapDistance);
        }

        double latencyInstability = latencyJitterTracker.combinedInstabilityScore();
        double crystalInstability = crystalSyncManager.instabilityFactor();
        double adaptationStrength = clamp(config.adaptationStrength, 0.25D, 2.0D);

        double normalizedSnap = clamp((snapDistance - (threshold * 0.65D)) / threshold, 0.0D, 1.8D);
        double normalizedAccel = clamp((motionState.accelerationEma - 0.35D) / 2.2D, 0.0D, 1.5D);
        double normalizedSpeed = clamp(motionState.speedEma / 6.0D, 0.0D, 1.0D);

        double extraStepBudget =
                (latencyInstability * 2.4D)
                        + (crystalInstability * 1.7D)
                        + (normalizedSnap * 2.1D)
                        + (normalizedAccel * 1.2D)
                        + (normalizedSpeed * 0.7D);
        extraStepBudget *= adaptationStrength;

        if (!config.experimentalAdaptiveInterpolation) {
            extraStepBudget *= 0.58D;
        }

        int targetSteps = base + (int) Math.round(extraStepBudget);

        if (config.experimentalAdaptiveInterpolation) {
            int extraBudget = Math.max(0, max - base);
            int extraFromSnap = (int) Math.min(extraBudget, Math.floor(snapDistance / threshold));
            targetSteps = Math.max(targetSteps, base + extraFromSnap);
        }

        double smoothingAlpha = snapDistance >= threshold ? 0.62D : 0.30D;
        motionState.smoothedStepTarget = ema(motionState.smoothedStepTarget, targetSteps, smoothingAlpha);
        adjusted = Math.max(adjusted, (int) Math.round(motionState.smoothedStepTarget));

        motionState.lastPacketMs = now;
        motionState.lastUpdateMs = now;
        motionState.lastTargetX = x;
        motionState.lastTargetY = y;
        motionState.lastTargetZ = z;
        motionState.hasTarget = true;

        return Math.min(adjusted, max);
    }

    private boolean isRelevantEntity(Entity entity) {
        return entity instanceof PlayerEntity
                || entity instanceof EndCrystalEntity
                || entity instanceof TntEntity
                || entity instanceof PersistentProjectileEntity;
    }

    private double ema(double previous, double sample, double alpha) {
        return previous + (sample - previous) * alpha;
    }

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static final class EntityMotionState {
        private boolean hasTarget;
        private double lastTargetX;
        private double lastTargetY;
        private double lastTargetZ;

        private long lastPacketMs;
        private long lastUpdateMs;

        private double speedEma;
        private double accelerationEma;
        private double smoothedStepTarget;
    }
}
