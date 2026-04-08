package com.cpvpdesyncfix.config;

public final class ModConfig {
    public static final int CURRENT_CONFIG_REVISION = 2;

    public int configRevision = CURRENT_CONFIG_REVISION;

    public boolean enableCrystalCleanup = true;
    public boolean enableInterpolationSmoothing = true;
    public boolean enableDebugLogging = false;
    public boolean useCompetitivePreset = true;

    public boolean experimentalAdaptiveInterpolation = false;

    public int crystalSuppressionMs = 275;
    public int rapidUpdateWindowMs = 220;
    public int crystalRecordRetentionMs = 12_000;
    public int maxSuppressionMs = 450;
    public int maxTrackedCrystals = 512;

    public int baseInterpolationSteps = 3;
    public int maxInterpolationSteps = 9;
    public double largeSnapThreshold = 1.8D;
    public int motionStateRetentionMs = 10_000;
    public int maxEntityMotionStates = 1200;
    public double adaptationStrength = 1.0D;

    public int pingSampleIntervalTicks = 10;
    public int debugLogIntervalTicks = 200;

    public boolean sanitize() {
        boolean changed = false;

        if (configRevision != CURRENT_CONFIG_REVISION) {
            configRevision = CURRENT_CONFIG_REVISION;
            changed = true;
        }

        changed |= clampIntField("crystalSuppressionMs", 80, 600);
        changed |= clampIntField("rapidUpdateWindowMs", 60, 1_000);
        changed |= clampIntField("crystalRecordRetentionMs", 1_500, 120_000);
        changed |= clampIntField("maxSuppressionMs", 100, 2_000);
        changed |= clampIntField("maxTrackedCrystals", 128, 4_096);

        changed |= clampIntField("baseInterpolationSteps", 1, 20);
        changed |= clampIntField("maxInterpolationSteps", 1, 30);
        changed |= clampDoubleField("largeSnapThreshold", 0.25D, 12.0D);
        changed |= clampIntField("motionStateRetentionMs", 2_000, 120_000);
        changed |= clampIntField("maxEntityMotionStates", 128, 10_000);
        changed |= clampDoubleField("adaptationStrength", 0.25D, 2.0D);

        changed |= clampIntField("pingSampleIntervalTicks", 1, 40);
        changed |= clampIntField("debugLogIntervalTicks", 40, 2_400);

        if (maxSuppressionMs < crystalSuppressionMs) {
            maxSuppressionMs = crystalSuppressionMs;
            changed = true;
        }

        if (maxInterpolationSteps < baseInterpolationSteps) {
            maxInterpolationSteps = baseInterpolationSteps;
            changed = true;
        }

        if (useCompetitivePreset) {
            // Keep defaults stable for competitive sessions while still configurable.
            if (baseInterpolationSteps < 3) {
                baseInterpolationSteps = 3;
                changed = true;
            }
            if (maxInterpolationSteps < 8) {
                maxInterpolationSteps = 8;
                changed = true;
            }
            if (crystalSuppressionMs < 240) {
                crystalSuppressionMs = 240;
                changed = true;
            }
            if (rapidUpdateWindowMs < 180) {
                rapidUpdateWindowMs = 180;
                changed = true;
            }
            if (adaptationStrength < 0.90D) {
                adaptationStrength = 0.90D;
                changed = true;
            }
            if (adaptationStrength > 1.35D) {
                adaptationStrength = 1.35D;
                changed = true;
            }
            if (pingSampleIntervalTicks > 12) {
                pingSampleIntervalTicks = 12;
                changed = true;
            }
        }

        return changed;
    }

    private boolean clampIntField(String fieldName, int min, int max) {
        int value;
        switch (fieldName) {
            case "crystalSuppressionMs" -> value = crystalSuppressionMs;
            case "rapidUpdateWindowMs" -> value = rapidUpdateWindowMs;
            case "crystalRecordRetentionMs" -> value = crystalRecordRetentionMs;
            case "maxSuppressionMs" -> value = maxSuppressionMs;
            case "maxTrackedCrystals" -> value = maxTrackedCrystals;
            case "baseInterpolationSteps" -> value = baseInterpolationSteps;
            case "maxInterpolationSteps" -> value = maxInterpolationSteps;
            case "motionStateRetentionMs" -> value = motionStateRetentionMs;
            case "maxEntityMotionStates" -> value = maxEntityMotionStates;
            case "pingSampleIntervalTicks" -> value = pingSampleIntervalTicks;
            case "debugLogIntervalTicks" -> value = debugLogIntervalTicks;
            default -> throw new IllegalArgumentException("Unknown int config field: " + fieldName);
        }

        int clamped = Math.max(min, Math.min(max, value));
        if (clamped == value) {
            return false;
        }

        switch (fieldName) {
            case "crystalSuppressionMs" -> crystalSuppressionMs = clamped;
            case "rapidUpdateWindowMs" -> rapidUpdateWindowMs = clamped;
            case "crystalRecordRetentionMs" -> crystalRecordRetentionMs = clamped;
            case "maxSuppressionMs" -> maxSuppressionMs = clamped;
            case "maxTrackedCrystals" -> maxTrackedCrystals = clamped;
            case "baseInterpolationSteps" -> baseInterpolationSteps = clamped;
            case "maxInterpolationSteps" -> maxInterpolationSteps = clamped;
            case "motionStateRetentionMs" -> motionStateRetentionMs = clamped;
            case "maxEntityMotionStates" -> maxEntityMotionStates = clamped;
            case "pingSampleIntervalTicks" -> pingSampleIntervalTicks = clamped;
            case "debugLogIntervalTicks" -> debugLogIntervalTicks = clamped;
            default -> throw new IllegalArgumentException("Unknown int config field: " + fieldName);
        }
        return true;
    }

    private boolean clampDoubleField(String fieldName, double min, double max) {
        double value;
        switch (fieldName) {
            case "largeSnapThreshold" -> value = largeSnapThreshold;
            case "adaptationStrength" -> value = adaptationStrength;
            default -> throw new IllegalArgumentException("Unknown double config field: " + fieldName);
        }

        double clamped = Math.max(min, Math.min(max, value));
        if (Double.compare(clamped, value) == 0) {
            return false;
        }

        switch (fieldName) {
            case "largeSnapThreshold" -> largeSnapThreshold = clamped;
            case "adaptationStrength" -> adaptationStrength = clamped;
            default -> throw new IllegalArgumentException("Unknown double config field: " + fieldName);
        }
        return true;
    }
}
