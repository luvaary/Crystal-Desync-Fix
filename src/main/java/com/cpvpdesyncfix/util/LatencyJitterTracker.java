package com.cpvpdesyncfix.util;

import com.cpvpdesyncfix.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;

public final class LatencyJitterTracker {
    private static final int MAX_PING_SAMPLES = 20;
    private static final int MAX_MOTION_SAMPLES = 24;

    private final Deque<Integer> pingSamples = new ArrayDeque<>();
    private final Deque<Double> motionSamples = new ArrayDeque<>();

    private int tickCounter;

    private int averagePingMs;
    private int minPingMs;
    private int maxPingMs;
    private double pingJitterMs;
    private double motionJitter;

    public synchronized void tick(MinecraftClient client, ModConfig config) {
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        tickCounter++;
        int interval = Math.max(1, config.pingSampleIntervalTicks);
        if (tickCounter % interval != 0) {
            return;
        }

        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        if (entry == null) {
            return;
        }

        pingSamples.addLast(entry.getLatency());
        while (pingSamples.size() > MAX_PING_SAMPLES) {
            pingSamples.pollFirst();
        }

        recomputePingStats();
    }

    public synchronized void recordSnap(double snapDistance) {
        motionSamples.addLast(snapDistance);
        while (motionSamples.size() > MAX_MOTION_SAMPLES) {
            motionSamples.pollFirst();
        }
        recomputeMotionJitter();
    }

    public synchronized int averagePingMs() {
        return averagePingMs;
    }

    public synchronized int minPingMs() {
        return minPingMs;
    }

    public synchronized int maxPingMs() {
        return maxPingMs;
    }

    public synchronized double pingJitterMs() {
        return pingJitterMs;
    }

    public synchronized double motionJitter() {
        return motionJitter;
    }

    public synchronized double combinedInstabilityScore() {
        double pingComponent = clamp(pingJitterMs / 42.0D, 0.0D, 1.0D);
        double motionComponent = clamp(motionJitter / 2.75D, 0.0D, 1.0D);
        return clamp((pingComponent * 0.58D) + (motionComponent * 0.42D), 0.0D, 1.0D);
    }

    public synchronized String statusSummary() {
        return String.format(
                Locale.ROOT,
                "pingAvg=%d pingMin=%d pingMax=%d pingJitter=%.2f motionJitter=%.2f instability=%.2f",
                averagePingMs,
                minPingMs,
                maxPingMs,
                pingJitterMs,
                motionJitter,
                combinedInstabilityScore()
        );
    }

    private void recomputePingStats() {
        if (pingSamples.isEmpty()) {
            averagePingMs = 0;
            minPingMs = 0;
            maxPingMs = 0;
            pingJitterMs = 0.0D;
            return;
        }

        int total = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int ping : pingSamples) {
            total += ping;
            min = Math.min(min, ping);
            max = Math.max(max, ping);
        }
        averagePingMs = total / pingSamples.size();
        minPingMs = min;
        maxPingMs = max;

        if (pingSamples.size() < 2) {
            pingJitterMs = 0.0D;
            return;
        }

        double deltaTotal = 0.0D;
        Iterator<Integer> iterator = pingSamples.iterator();
        int previous = iterator.next();
        int deltas = 0;
        while (iterator.hasNext()) {
            int current = iterator.next();
            deltaTotal += Math.abs(current - previous);
            previous = current;
            deltas++;
        }

        pingJitterMs = deltas == 0 ? 0.0D : deltaTotal / deltas;
    }

    private void recomputeMotionJitter() {
        if (motionSamples.isEmpty()) {
            motionJitter = 0.0D;
            return;
        }

        double total = 0.0D;
        for (double sample : motionSamples) {
            total += sample;
        }
        motionJitter = total / motionSamples.size();
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
}
