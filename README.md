# CPVP Desync Fix

[![CI](https://github.com/luvaary/Crystal-Desync-Fix/actions/workflows/ci.yml/badge.svg)](https://github.com/luvaary/Crystal-Desync-Fix/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/luvaary/Crystal-Desync-Fix?display_name=tag&sort=semver)](https://github.com/luvaary/Crystal-Desync-Fix/releases)
[![Downloads](https://img.shields.io/github/downloads/luvaary/Crystal-Desync-Fix/total)](https://github.com/luvaary/Crystal-Desync-Fix/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-2ea043)](https://www.minecraft.net/)
[![Fabric Loader](https://img.shields.io/badge/Fabric%20Loader-0.18.6-f6c915)](https://fabricmc.net/)

![CPVP Desync Fix End Crystal Logo](docs/logo.svg)

Author: Falthera (luvaary)

CPVP Desync Fix is a client-side Fabric mod for Minecraft 1.21.11 focused on crystal combat visual consistency. It improves responsiveness and smoothness under real network jitter without changing server authority or automating combat.

## Why This Mod

- Cleans up stale crystal rendering after fast place and break exchanges.
- Reduces movement jitter and visual snapback for relevant entities.
- Runs fully in the background with no HUD noise and no hotkeys required.
- Stays within fair-play constraints suitable for competitive environments.

## Safety Scope

- No packet spoofing.
- No fake hitbox manipulation.
- No combat automation.
- No reach, speed, rotation, or aim cheats.
- No server-side logic.

## Core Features

1. Crystal visual desync cleanup
- Tracks crystal lifecycle state client-side.
- Predictively suppresses short-lived ghost visuals after local break input.
- Reconciles predictions against server-confirmed removal and timeout paths.

2. Entity interpolation smoothing
- Adjusts interpolation budget for relevant entities only.
- Adapts to live ping jitter and motion jitter signals.
- Preserves server truth while improving render continuity.

3. Competitive stability hardening
- Runtime fail-safe guards prevent one feature exception from crashing client tick.
- Config sanitization clamps unstable values on load and save.
- Internal state maps are capacity-limited for long-session stability.

## Requirements

- Minecraft: 1.21.11
- Java: 21
- Fabric Loader: 0.18.6+
- Fabric API: 0.141.3+1.21.11

## Quick Start

1. Open the project in IntelliJ.
2. Select JDK 21.
3. Run:

```bash
./gradlew genSources
./gradlew runClient
```

Windows:

```powershell
./gradlew.bat genSources
./gradlew.bat runClient
```

## Configuration

Config path:

- config/cpvp-desync-fix.json

Defaults are tuned for competitive stability, with adaptive interpolation experimental mode disabled by default.

## CI and Release Channels

### Auto Release From CI

CI now auto-publishes a GitHub prerelease on successful pushes to main and master.

Auto release naming:

- Tag: auto-v<mod_version>-mc<mc_version>-run.<run_number>.<run_attempt>
- Artifact: <archives_base_name>-ci-v<mod_version>-mc<mc_version>+run.<run_number>.jar

### Stable Tagged Release

The stable release workflow remains available for versioned releases.

Stable release naming:

- Tag: v<mod_version>-mc<mc_version>
- Release name: CPVP Desync Fix v<mod_version> for Fabric MC <mc_version>
- Artifact: <archives_base_name>-v<mod_version>-mc<mc_version>+build.<run_number>.jar

## Workflows

- CI: .github/workflows/ci.yml
- Stable release: .github/workflows/release.yml
