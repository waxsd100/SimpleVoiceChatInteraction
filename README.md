*Read this in other languages: [日本語](README_ja.md)*

# Simple Voice Chat Interaction

This server side Forge mod allows Simple Voice Chat to interact with your Minecraft world.

## Features

- Talking in voice chat activates sculk sensors
- Talking in voice chat is detected by the warden
- Yelling in voice chat unleashes a **Sonic Shockwave** (Restricted to the Deep Dark biome and `deeperdarker:otherside` dimension)
  - **Phase 1 — Radial AoE**: Damages all nearby entities within a small radius around the player (0.5x base radius)
  - **Phase 2 — Sonic Beam**: Fires a Warden-style directional beam in the player's look direction with 3x base radius range and 1.5x damage
  - Beam hits apply knockback that scales proportionally to damage
  - Players hit by the beam are stunned: Slowness Lv.200 (immobilized), Blindness (fog), Jump suppression, and Darkness
  - Stun duration matches config value
- **Sculk vibrations along the entire shockwave path**
  - Sculk sensors/shriekers react at the player's position, along the beam (every 8 blocks), and at the impact point
  - This can chain into Warden summoning in Sculk Shrieker-heavy areas
- Dynamic shockwave radius and damage based on voice volume (Scales up to 200dB)
  - Exceeding 100dB triggers **Overdrive** mode, massively increasing multipliers
- **Voice Normalization (AGC)**: Automatically compensates for microphone sensitivity differences between players. Everyone's voice triggers sculk/shockwave at a consistent level regardless of their SVC settings
- **Wool Dampening**: Surrounding yourself with wool blocks reduces voice volume — just like vanilla sculk sensors are blocked by wool. Up to -20dB when fully enclosed in 6 directions
- Advanced noise suppression filters (Band-pass filter and Zero-crossing rate penalty to filter out keyboard types and white noise)
- **Mob Aggro propagation**: Hostile mobs like zombies and skeletons are attracted to the player's voice
- Bonus damage multipliers against monsters and wardens
- Optional support for group chat vibrations
- Voice volume is automatically reduced when whispering/sneaking
- Voice volume is automatically amplified while sprinting
- On-screen Voice Meter (BossBar) that automatically appears in the Deep Dark biome and `deeperdarker:otherside` dimension
  - Dynamic color changes, visual effects upon activation, and cooldown timer

## Commands

- `/voice_meter`: Manually toggle the Voice Meter UI
- `/voice_reload`: Reloads the configuration file from disk (Requires OP level 2)
- `/voice_debug <dB>`: Test voice triggers (Requires OP level 2)
- `/voice_monitor [player]`: Real-time monitoring of another player's voice volume (dB) via BossBar. Run without arguments to stop monitoring.

## Config Values

*config/simplevoicechatinteraction-common.toml*

| Name                                  | Default Value | Description                                                  |
|---------------------------------------|---------------|--------------------------------------------------------------|
| `group_interaction`                   | `true`        | Whether group voice chat triggers sculk vibrations & shockwaves |
| `whisper_volume_multiplier`           | `0.5`         | Voice volume multiplier while whispering (0.0–1.0)           |
| `sneak_volume_multiplier`             | `0.5`         | Voice volume multiplier while sneaking (0.0–1.0)             |
| `sprint_volume_multiplier`            | `2.5`         | Voice volume multiplier while sprinting (0.0–10.0)           |
| `microphone_base_value`               | `100.0`       | Base value for dBFS → dB SPL conversion (0.0–200.0)          |
| `microphone_multiplier`               | `2.0`         | Multiplier applied to raw dBFS level (0.1–10.0)              |
| `noise_gate_threshold`                | `40.0`        | Noise gate threshold to cut off microphone background noise   |
| `advanced_noise_filtering`            | `true`        | Enable advanced band-pass & ZCR noise filtering               |
| `voice_normalization`                 | `true`        | Auto-compensate microphone sensitivity differences            |
| `voice_normalization_target`          | `70.0`        | Target baseline dB for normalization (30.0–150.0)             |
| `voice_normalization_max_offset`      | `30.0`        | Max normalization offset in dB (0.0–100.0)                    |
| `wool_dampening`                      | `true`        | Wool blocks reduce voice volume                               |
| `wool_dampening_max_db`               | `-20.0`       | Max dampening when fully enclosed in wool (dB)                |
| `voice_sculk_frequency`               | `7`           | Sculk sensor redstone signal strength for voice (1–15)        |
| `minimum_activation_threshold`        | `60`          | Minimum audio level to activate sculk (dB SPL)                |
| `mob_aggro`                           | `true`        | Enable hostile mobs being attracted by voice                  |
| `mob_aggro_min_radius`                | `16.0`        | Minimum radius for mob aggro (blocks)                         |
| `mob_aggro_max_radius`                | `64.0`        | Maximum radius for mob aggro (blocks)                         |
| `shockwave_enabled`                   | `true`        | Enable/disable the Sonic Shockwave feature                    |
| `shockwave_require_deep_dark`         | `true`        | Restrict shockwave to Deep Dark / otherside dimension         |
| `shockwave_threshold`                 | `85`          | Minimum audio level to trigger the shockwave (dB SPL)         |
| `shockwave_radius`                    | `4.0`         | Base radius at the threshold (blocks)                         |
| `shockwave_100db_radius`              | `10.0`        | Base radius at 100dB (blocks)                                 |
| `shockwave_damage`                    | `4.0`         | Base damage at the threshold                                  |
| `shockwave_100db_damage`              | `8.0`         | Base damage at 100dB                                          |
| `shockwave_overdrive_multiplier`      | `2.0`         | Overdrive multiplier for volumes between 100dB and 200dB      |
| `shockwave_player_damage_multiplier`  | `0.5`         | Damage multiplier against players                             |
| `shockwave_monster_damage_multiplier` | `10.0`        | Damage multiplier against monsters                            |
| `shockwave_warden_damage_multiplier`  | `20.0`        | Damage multiplier against the Warden                          |
| `shockwave_knockback_horizontal`      | `2.3`         | Horizontal knockback strength                                 |
| `shockwave_knockback_vertical`        | `0.4`         | Vertical knockback strength                                   |
| `shockwave_darkness_duration`         | `60`          | Darkness effect duration in ticks (20 ticks = 1 sec)          |
| `shockwave_cooldown`                  | `30000`       | Cooldown of the shockwave effect in milliseconds              |
| `shockwave_break_glass`               | `true`        | Enable glass and ice breaking on shockwave / loud yell        |
| `shockwave_break_glass_threshold`     | `100.0`       | Minimum audio level to break glass (dB SPL)                   |

### Shockwave & Sculk Reference (Default Config)

The following table shows what happens at each volume level with default settings:

| Volume | Sculk Reaction | Shockwave | AoE Radius | Beam Range | Notes |
|--------|:--------------:|:---------:|:----------:|:----------:|-------|
| **60 dB** (Normal voice) | ✅ | ❌ | — | — | Sculk sensors activate. No shockwave. |
| **85 dB** (Threshold) | ✅ | ✅ | **2.0 m** | **12.0 m** | Minimum shockwave. Sculk vibrations at player + along beam + impact. |
| **100 dB** (Loud yell) | ✅ | ✅ | **5.0 m** | **30.0 m** | Maximum before Overdrive. |
| **200 dB** (Overdrive max) | ✅ | ✅ | **10.0 m** | **60.0 m** | Overdrive ×2.0 applied. Massive area of effect. |

> **Sculk vibration points during shockwave:**
> - Player position (AoE center)
> - Every 8 blocks along the beam (matching sculk sensor detection range)
> - Beam impact point (end of beam)
>
> At 100dB, the beam generates ~4 vibration points. At 200dB, up to ~8 points — enough to chain-activate multiple shriekers and summon the Warden.

### Shockwave Scaling Formula

The shockwave scales dynamically based on voice volume. It starts scaling from the `shockwave_threshold` (85dB) up to 100dB linearly. Beyond 100dB, it scales using the Overdrive multiplier up to 200dB.

**Radial AoE Range** (0.5x base radius)
- Minimum volume (Threshold): Radius **2.0** blocks
- Maximum volume (100dB): Radius **5.0** blocks

**Sonic Beam Range** (3x base radius)
- Minimum volume (Threshold): **12.0** blocks
- Maximum volume (100dB): **30.0** blocks

**Overdrive Coefficient** (100dB〜200dB, default `overdrive_multiplier = 2.0`)

All radius and damage values at 100dB are further multiplied by this coefficient:

| Volume | Overdrive Coefficient | Base Radius | AoE Radius | Beam Range |
|:------:|:---------------------:|:-----------:|:----------:|:----------:|
| **100 dB** | ×1.0 | 10.0 | **5.0 m** | **30.0 m** |
| **120 dB** | ×1.2 | 12.0 | **6.0 m** | **36.0 m** |
| **150 dB** | ×1.5 | 15.0 | **7.5 m** | **45.0 m** |
| **200 dB** | ×2.0 | 20.0 | **10.0 m** | **60.0 m** |

**Radial AoE Damage** (At Default Settings)

| Target Entity | Multiplier | 85 dB (Threshold) | 100 dB | 200 dB (Overdrive) |
|---|:---:|:---:|:---:|:---:|
| **Player** | `0.5x` | **2.0** (❤️×1) | **4.0** (❤️×2) | **8.0** (❤️×4) |
| **Normal (Animals, etc)** | `1.0x` | **4.0** (❤️×2) | **8.0** (❤️×4) | **16.0** (❤️×8) |
| **Monster** | `10.0x` | **40.0** (❤️×20) | **80.0** (❤️×40) | **160.0** (❤️×80) |
| **Warden** | `20.0x` | **80.0** (❤️×40) | **160.0** (❤️×80) | **320.0** (❤️×160) |

**Sonic Beam Damage (1.5x AoE)** (At Default Settings)

| Target Entity | Multiplier | 85 dB (Threshold) | 100 dB | 200 dB (Overdrive) |
|---|:---:|:---:|:---:|:---:|
| **Player** | `0.5x` | **3.0** (❤️×1.5) | **6.0** (❤️×3) | **12.0** (❤️×6) |
| **Normal (Animals, etc)** | `1.0x` | **6.0** (❤️×3) | **12.0** (❤️×6) | **24.0** (❤️×12) |
| **Monster** | `10.0x` | **60.0** (❤️×30) | **120.0** (❤️×60) | **240.0** (❤️×120) |
| **Warden** | `20.0x` | **120.0** (❤️×60) | **240.0** (❤️×120) | **480.0** (❤️×240) |

### Voice Volume (dB SPL) Reference

#### In-game Scale (Microphone Input)

| dB | Description |
|:---:|---|
| **200 dB** | System maximum limit. Overdrive ceiling. |
| **100 dB** | Microphone clipping. Yelling directly into the mic or tapping it. |
| **80 – 90 dB** | Very loud voice. Shouting in surprise or laughing hard. |
| **60 – 70 dB** | Normal conversation. Relaxed talking on Discord. |
| **40 – 50 dB** | Quiet sounds. Whispering, keyboard typing, mouse clicks, background noise. |

#### Real-World dB Reference

> Decibels (dB) use a **logarithmic scale** — every **+10 dB** sounds roughly **twice as loud** to the human ear.

| dB | Real-World Example |
|:---:|---|
| **200 dB** | Exceeds the acoustic limit. Only achievable as a pressure blast (e.g. nuclear detonation at close range). |
| **194 dB** | **Theoretical limit of sound in air.** Sound is a pressure wave; at 1 atm, the lowest trough of the wave reaches vacuum — beyond this point the wave can no longer compress and becomes a **shockwave**. |
| **160 – 180 dB** | Lightning strike at close range. Large explosions. The 1883 Krakatoa eruption produced ~180 dB at 160 km away. |
| **140 – 150 dB** | Standing next to a jet engine at full thrust. **Irreversible hearing damage** — eardrum rupture, internal bleeding, lung damage at sustained exposure. |
| **130 dB** | Human pain threshold. Physical discomfort becomes unbearable. |
| **120 dB** | Threshold of pain onset. Jet aircraft takeoff at 300m. Prolonged exposure causes permanent hearing loss. |
| **100 – 110 dB** | Rock concert front row. Chainsaw. Prolonged exposure requires hearing protection. |
| **80 – 90 dB** | Heavy traffic. Vacuum cleaner. Shouting at 1 meter distance. |
| **60 – 70 dB** | Normal conversation at 1 meter. Background music. |
| **40 – 50 dB** | Quiet office. Whispered conversation. |
| **20 – 30 dB** | Rustling leaves. Quiet bedroom at night. |
