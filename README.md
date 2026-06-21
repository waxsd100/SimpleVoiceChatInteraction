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

Main configuration keys:
| Name                                  | Default Value | Description                                                  |
|---------------------------------------|---------------|--------------------------------------------------------------|
| `noise_gate_threshold`                | `40.0`        | Noise gate threshold to cut off microphone background noise  |
| `advanced_noise_filtering`            | `true`        | Enable advanced band-pass & ZCR noise filtering              |
| `voice_normalization`                 | `true`        | Auto-compensate microphone sensitivity differences           |
| `voice_normalization_target`          | `70.0`        | Target baseline dB for normalization                         |
| `wool_dampening`                      | `true`        | Wool blocks reduce voice volume                              |
| `wool_dampening_max_db`               | `-20.0`       | Max dampening when fully enclosed in wool (dB)               |
| `minimum_activation_threshold`        | `60`          | Minimum audio level to activate sculk (dB SPL)               |
| `shockwave_threshold`                 | `85`          | Minimum audio level to trigger the shockwave (dB SPL)        |
| `shockwave_radius`                    | `2.0`         | Base radius of the shockwave effect at the threshold         |
| `shockwave_100db_radius`              | `10.0`        | Base radius of the shockwave effect at 100dB                 |
| `shockwave_damage`                    | `4.0`         | Base damage of the shockwave effect at the threshold         |
| `shockwave_100db_damage`              | `20.0`        | Base damage of the shockwave effect at 100dB                 |
| `shockwave_overdrive_multiplier`      | `3.0`         | Overdrive multiplier for volumes between 100dB and 200dB     |
| `shockwave_warden_damage_multiplier`  | `20.0`        | Damage multiplier against the Warden                         |
| `shockwave_cooldown`                  | `30000`       | Cooldown of the shockwave effect in milliseconds             |

### Shockwave & Sculk Reference (Default Config)

The following table shows what happens at each volume level with default settings:

| Volume | Sculk Reaction | Shockwave | AoE Radius | Beam Range | Damage | Notes |
|--------|:--------------:|:---------:|:----------:|:----------:|:------:|-------|
| **60 dB** (Normal voice) | ✅ | ❌ | — | — | — | Sculk sensors activate. No shockwave. |
| **85 dB** (Threshold) | ✅ | ✅ | **1.0 m** | **6.0 m** | **4.0** | Minimum shockwave. Sculk vibrations at player + along beam + impact. |
| **100 dB** (Loud yell) | ✅ | ✅ | **5.0 m** | **30.0 m** | **20.0** | Maximum before Overdrive. |
| **200 dB** (Overdrive max) | ✅ | ✅ | **15.0 m** | **90.0 m** | **60.0** | Overdrive ×3.0 applied. Massive area of effect. |

> **Sculk vibration points during shockwave:**
> - Player position (AoE center)
> - Every 8 blocks along the beam (matching sculk sensor detection range)
> - Beam impact point (end of beam)
>
> At 100dB, the beam generates ~4 vibration points. At 200dB, up to ~12 points — enough to chain-activate multiple shriekers and summon the Warden.

### Shockwave Scaling Formula

The shockwave scales dynamically based on voice volume. It starts scaling from the `shockwave_threshold` (85dB) up to 100dB linearly. Beyond 100dB, it scales using the Overdrive multiplier up to 200dB.

**Radial AoE Range** (0.5x base radius)
- Minimum volume (Threshold): Radius **1.0** block
- Maximum volume (100dB): Radius **5.0** blocks

**Sonic Beam Range** (3x base radius)
- Minimum volume (Threshold): **6.0** blocks
- Maximum volume (100dB): **30.0** blocks

### Voice Volume (dB SPL) Reference

- **200 dB**: Absolute maximum limit of the system.
- **100 dB**: Microphone limit (clipping). The sound of yelling directly into the microphone or tapping it.
- **80 to 90 dB**: Very loud voice. Shouting in surprise or laughing loudly.
- **60 to 70 dB**: Normal conversation level. Relaxed talking on Discord.
- **40 to 50 dB**: Quiet sounds/noise. Whispering, keyboard typing, mouse clicks, or background noises.
