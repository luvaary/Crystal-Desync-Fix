# CPVP Desync Fix

Author: Falthera

CPVP Desync Fix is a client-side Fabric mod for Minecraft 1.21.11 that improves Crystal PvP visual consistency under network delay without changing server truth or automating combat.

This build runs fully in the background with no HUD and no keybind toggles.

Logo: docs/logo.svg

## Safety and scope

This mod is intentionally limited to quality-of-life rendering and client feedback:

- No packet spoofing
- No fake hitbox manipulation
- No combat automation
- No reach, speed, rotation, aim, or hitbox cheats
- No server-side logic

## Features

1. Crystal visual desync cleanup
- Tracks crystal presence client-side.
- Predictively hides crystals immediately after local break input for a short timeout.
- Reconciles with server updates and logs desync-like events (rapid flicker/timeouts).

2. Entity interpolation smoothing
- Adjusts tracked interpolation steps for relevant entities (players, crystals, TNT).
- Reduces harsh visual snapback from delayed movement packets.
- Keeps server authority unchanged.

3. Advanced background adaptation
- No UI overlay and no hotkeys.
- Uses crystal instability plus ping/motion jitter as real-time signals.
- Dynamically adjusts interpolation budgets during packet bursts.

4. Config
- JSON config file in the Fabric config folder.
- Feature toggles for cleanup and interpolation with safe tuning parameters.
- No runtime keybind inputs.
- Includes config sanitization and competitive-safe clamping to avoid unstable values.

## Setup

1. Open the project in IntelliJ.
2. Ensure JDK 21 is selected.
3. Run:

   - gradlew genSources
   - gradlew runClient

This repository already includes a pinned Gradle wrapper for reproducible builds.

## Config file

Generated at:

- config/cpvp-desync-fix.json

All major features are enabled by default except the adaptive interpolation experiment.

## GitHub Actions CI and Release

This repository includes two workflows:

- CI build workflow: .github/workflows/ci.yml
- Release workflow: .github/workflows/release.yml

Both workflows compile against Fabric for Minecraft 1.21.11.

Release naming scheme:

- Tag: v<mod_version>-mc<mc_version>
- Release name: CPVP Desync Fix v<mod_version> for Fabric MC <mc_version>
- Artifact: <archives_base_name>-v<mod_version>-mc<mc_version>+build.<run_number>.jar

Example tag for the current config:

- v1.0.0-mc1.21.11

When you push a matching tag, the release workflow builds the mod, creates a checksum file, and publishes a GitHub Release automatically.

## Competitive readiness hardening

- Runtime fail-safe guards prevent one-off feature exceptions from crashing the client.
- Config values are sanitized and clamped on load.
- Competitive preset mode enforces stable lower/upper bounds for key smoothing timings.
- Internal state maps are capacity-limited to protect long-session memory stability.