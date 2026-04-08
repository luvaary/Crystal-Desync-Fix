package com.cpvpdesyncfix.feature;

import com.cpvpdesyncfix.config.ModConfig;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CrystalSyncManager {
    private static final long DEFAULT_EVENT_RETENTION_MS = 20_000L;
    private static final long DESYNC_EVENT_WINDOW_MS = 4_500L;
    private static final long UNSTABLE_AFTER_RAPID_MS = 450L;
    private static final int MAX_EVENT_LOG_SIZE = 256;

    private final Map<Integer, CrystalRecord> records = new HashMap<>();
    private final Set<Integer> visibleIdsScratch = new HashSet<>();
    private final Deque<CrystalEvent> eventLog = new ArrayDeque<>();
    private final Deque<Long> desyncEventTimes = new ArrayDeque<>();

    private long unstableUntilMs = 0L;
    private long worldTickIndex = 0L;

    public synchronized void scanWorld(ClientWorld world, ModConfig config) {
        if (world == null) {
            return;
        }

        long now = System.currentTimeMillis();
        trimRecordCapacity(config, now);
        worldTickIndex++;
        visibleIdsScratch.clear();

        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) {
                continue;
            }

            int id = crystal.getId();
            visibleIdsScratch.add(id);

            CrystalRecord record = records.computeIfAbsent(id, CrystalRecord::new);
            boolean wasVisible = record.visible;
            record.markVisible(now, worldTickIndex, crystal.getX(), crystal.getY(), crystal.getZ());

            if (!wasVisible) {
                if (record.predictedBreakUntilMs > now) {
                    markDesync(now);
                    addEvent(now, EventType.BOUNCE, id, "reappeared before predicted break confirmation");
                    unstableUntilMs = Math.max(unstableUntilMs, now + UNSTABLE_AFTER_RAPID_MS + 150L);
                } else {
                    addEvent(now, EventType.SPAWN, id, "appeared");
                }
            }
        }

        for (CrystalRecord record : records.values()) {
            if (visibleIdsScratch.contains(record.entityId) || !record.visible) {
                continue;
            }

            record.visible = false;
            record.lastBecameInvisibleAtMs = now;
            record.visibilityTransitions++;

            boolean predicted = record.predictedBreakUntilMs > now;
            if (predicted) {
                record.predictedBreakUntilMs = 0L;
                record.confirmedBreakCount++;
                addEvent(now, EventType.CONFIRM, record.entityId, "server removed after local break");
                continue;
            }

            long visibleLifetimeMs = now - record.lastBecameVisibleAtMs;
            if (visibleLifetimeMs <= Math.max(50, config.rapidUpdateWindowMs)) {
                record.rapidTransitions++;
                markDesync(now);
                addEvent(now, EventType.RAPID, record.entityId, "flickered within " + visibleLifetimeMs + "ms");
                unstableUntilMs = Math.max(unstableUntilMs, now + UNSTABLE_AFTER_RAPID_MS);
            } else {
                addEvent(now, EventType.REMOVE, record.entityId, "removed");
            }
        }

        clearExpiredPredictions(now);
        trimEventHistory(now, config);
    }

    public synchronized void predictLocalBreak(EndCrystalEntity crystal, ModConfig config) {
        if (crystal == null) {
            return;
        }

        long now = System.currentTimeMillis();
        int id = crystal.getId();
        CrystalRecord record = records.computeIfAbsent(id, CrystalRecord::new);

        int maxSuppression = Math.max(100, config.maxSuppressionMs);
        int suppressionMs = Math.max(80, Math.min(maxSuppression, config.crystalSuppressionMs));

        record.predictedBreakUntilMs = now + suppressionMs;
        record.predictedBreakCount++;
        if (record.firstSeenAtMs == 0L) {
            record.firstSeenAtMs = now;
            record.lastSeenAtMs = now;
        }

        unstableUntilMs = Math.max(unstableUntilMs, now + suppressionMs);
        addEvent(now, EventType.PREDICT, id, "local break sent");
    }

    public synchronized boolean shouldRender(EndCrystalEntity crystal, ModConfig config) {
        if (crystal == null) {
            return true;
        }

        CrystalRecord record = records.get(crystal.getId());
        if (record == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (record.predictedBreakUntilMs > now) {
            return false;
        }

        if (record.predictedBreakUntilMs != 0L) {
            record.predictedBreakUntilMs = 0L;
            record.timedOutPredictionCount++;
            markDesync(now);
            addEvent(now, EventType.TIMEOUT, record.entityId, "prediction timeout while still visible");
            unstableUntilMs = Math.max(unstableUntilMs, now + 180L);
        }

        return true;
    }

    public synchronized void cleanup(ModConfig config) {
        long now = System.currentTimeMillis();
        clearExpiredPredictions(now);
        trimEventHistory(now, config);
        trimDesyncWindow(now);
        trimRecordCapacity(config, now);

        int retentionMs = Math.max(1_500, config.crystalRecordRetentionMs);
        Iterator<Map.Entry<Integer, CrystalRecord>> iterator = records.entrySet().iterator();
        while (iterator.hasNext()) {
            CrystalRecord record = iterator.next().getValue();
            if (record.visible || record.predictedBreakUntilMs > now) {
                continue;
            }
            long staleFor = now - Math.max(record.lastSeenAtMs, record.lastBecameInvisibleAtMs);
            if (staleFor >= retentionMs) {
                iterator.remove();
            }
        }

        while (eventLog.size() > MAX_EVENT_LOG_SIZE) {
            eventLog.pollFirst();
        }
    }

    public synchronized double instabilityFactor() {
        long now = System.currentTimeMillis();
        trimDesyncWindow(now);

        int pendingPredictions = 0;
        for (CrystalRecord record : records.values()) {
            if (record.predictedBreakUntilMs > now) {
                pendingPredictions++;
            }
        }

        double desyncPressure = clamp01(desyncEventTimes.size() / 4.0D);
        double pendingPressure = clamp01(pendingPredictions / 3.0D);
        double rapidPressure = now < unstableUntilMs ? 0.25D : 0.0D;

        return clamp01((desyncPressure * 0.60D) + (pendingPressure * 0.25D) + rapidPressure);
    }

    public synchronized String backgroundStatus() {
        long now = System.currentTimeMillis();

        int visible = 0;
        int predicted = 0;
        for (CrystalRecord record : records.values()) {
            if (record.visible) {
                visible++;
            }
            if (record.predictedBreakUntilMs > now) {
                predicted++;
            }
        }

        String state;
        if (predicted > 0) {
            state = "WAITING_SERVER";
        } else if (now < unstableUntilMs) {
            state = "UNSTABLE";
        } else {
            state = "SYNCED";
        }

        return state
                + " visible=" + visible
                + " predictions=" + predicted
                + " desyncScore=" + String.format(Locale.ROOT, "%.2f", instabilityFactor());
    }

    private void clearExpiredPredictions(long now) {
        for (CrystalRecord record : records.values()) {
            if (record.predictedBreakUntilMs == 0L || record.predictedBreakUntilMs > now) {
                continue;
            }
            record.predictedBreakUntilMs = 0L;
            if (record.visible) {
                record.timedOutPredictionCount++;
                markDesync(now);
                addEvent(now, EventType.TIMEOUT, record.entityId, "prediction expired while entity remained visible");
                unstableUntilMs = Math.max(unstableUntilMs, now + 160L);
            }
        }
    }

    private void trimRecordCapacity(ModConfig config, long now) {
        int maxRecords = Math.max(128, config.maxTrackedCrystals);
        if (records.size() <= maxRecords) {
            return;
        }

        int toRemove = records.size() - maxRecords;
        Iterator<Map.Entry<Integer, CrystalRecord>> iterator = records.entrySet().iterator();
        while (iterator.hasNext() && toRemove > 0) {
            CrystalRecord record = iterator.next().getValue();
            if (!record.visible && record.predictedBreakUntilMs <= now) {
                iterator.remove();
                toRemove--;
            }
        }

        while (toRemove > 0 && !records.isEmpty()) {
            Integer oldestId = findOldestRecordId();
            if (oldestId == null) {
                break;
            }
            records.remove(oldestId);
            toRemove--;
        }
    }

    private Integer findOldestRecordId() {
        Integer oldestId = null;
        long oldestSeen = Long.MAX_VALUE;

        for (Map.Entry<Integer, CrystalRecord> entry : records.entrySet()) {
            CrystalRecord record = entry.getValue();
            long seen = Math.max(record.lastSeenAtMs, record.lastBecameInvisibleAtMs);
            if (seen < oldestSeen) {
                oldestSeen = seen;
                oldestId = entry.getKey();
            }
        }

        return oldestId;
    }

    private void trimEventHistory(long now, ModConfig config) {
        long retentionMs = Math.max(DEFAULT_EVENT_RETENTION_MS, Math.max(1_000, config.crystalRecordRetentionMs));
        while (!eventLog.isEmpty()) {
            CrystalEvent first = eventLog.peekFirst();
            if (first == null || now - first.timestampMs <= retentionMs) {
                break;
            }
            eventLog.pollFirst();
        }
    }

    private void trimDesyncWindow(long now) {
        while (!desyncEventTimes.isEmpty()) {
            Long first = desyncEventTimes.peekFirst();
            if (first == null || now - first <= DESYNC_EVENT_WINDOW_MS) {
                break;
            }
            desyncEventTimes.pollFirst();
        }
    }

    private void markDesync(long now) {
        desyncEventTimes.addLast(now);
        trimDesyncWindow(now);
    }

    private void addEvent(long now, EventType type, int entityId, String detail) {
        eventLog.addLast(new CrystalEvent(now, type, entityId, detail));
    }

    private double clamp01(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }

    private enum EventType {
        SPAWN,
        REMOVE,
        PREDICT,
        CONFIRM,
        RAPID,
        TIMEOUT,
        BOUNCE
    }

    private static final class CrystalRecord {
        private final int entityId;

        private boolean visible;
        private long firstSeenAtMs;
        private long lastSeenAtMs;
        private long lastSeenTick;
        private long lastBecameVisibleAtMs;
        private long lastBecameInvisibleAtMs;

        private long predictedBreakUntilMs;

        private int visibilityTransitions;
        private int rapidTransitions;
        private int predictedBreakCount;
        private int confirmedBreakCount;
        private int timedOutPredictionCount;

        private double lastSeenX;
        private double lastSeenY;
        private double lastSeenZ;

        private CrystalRecord(int entityId) {
            this.entityId = entityId;
        }

        private void markVisible(long now, long tick, double x, double y, double z) {
            if (!visible) {
                visible = true;
                visibilityTransitions++;
                lastBecameVisibleAtMs = now;
                if (firstSeenAtMs == 0L) {
                    firstSeenAtMs = now;
                }
            }

            lastSeenAtMs = now;
            lastSeenTick = tick;
            lastSeenX = x;
            lastSeenY = y;
            lastSeenZ = z;
        }
    }

    private record CrystalEvent(long timestampMs, EventType type, int entityId, String detail) {
    }
}
