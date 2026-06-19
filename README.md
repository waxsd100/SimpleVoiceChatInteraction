*Read this in other languages: [日本語](README_ja.md)*

# Simple Voice Chat Interaction

This server side Forge mod allows Simple Voice Chat to interact with your Minecraft world.

## Features

- Talking in voice chat activates sculk sensors
- Talking in voice chat is detected by the warden
- Yelling in voice chat unleashes a sonic shockwave that damages nearby entities (Restricted to the Deep Dark biome and `deeperdarker:otherside` dimension)
- Dynamic shockwave radius and damage based on voice volume
- Bonus damage multipliers against monsters and wardens
- Optional support for group chat vibrations
- Voice volume is automatically reduced when whispering (Configurable multiplier)
- Voice volume is automatically reduced while sneaking (Configurable multiplier)
- Voice volume is automatically amplified while sprinting (Configurable multiplier)
- On-screen Voice Meter (BossBar) that automatically appears in the Deep Dark biome and `deeperdarker:otherside` dimension
- Commands:
  - `/voice_debug <dB>`: Test voice triggers
  - `/voice_meter`: Manually toggle the Voice Meter UI
  - `/voice_reload`: Reloads the configuration file from disk (Requires OP level 2)

## Config Values

*config/simplevoicechatinteraction-common.toml*

| Name                                  | Default Value | Description                                                  |
|---------------------------------------|---------------|--------------------------------------------------------------|
| `group_interaction`                   | `true`        | If talking in groups should trigger vibrations               |
| `whisper_volume_multiplier`           | `0.5`         | Voice volume multiplier while whispering (0.5 = approx -6dB) |
| `sneak_volume_multiplier`             | `0.5`         | Voice volume multiplier while sneaking (0.5 = approx -6dB)   |
| `sprint_volume_multiplier`            | `1.5`         | Voice volume multiplier while sprinting (1.5 = approx +3.5dB)|
| `voice_sculk_frequency`               | `7`           | The sculk sensor frequency emitted by voice (1-15)           |
| `minimum_activation_threshold`        | `50`          | Minimum audio level to activate sculk (dB SPL)               |
| `shockwave_enabled`                   | `true`        | Whether the shockwave effect is enabled                      |
| `shockwave_threshold`                 | `100`         | Audio level required to trigger a shockwave (dB SPL)         |
| `shockwave_radius`                    | `10.0`        | Base radius of the shockwave effect                          |
| `shockwave_max_radius_multiplier`     | `2.0`         | Max radius multiplier at maximum volume (100dB)              |
| `shockwave_damage`                    | `4.0`         | Base damage of the shockwave effect                          |
| `shockwave_max_damage_multiplier`     | `2.0`         | Max damage multiplier at maximum volume (100dB)              |
| `shockwave_player_damage_multiplier`  | `0.5`         | Damage multiplier against players                            |
| `shockwave_monster_damage_multiplier` | `5.0`         | Damage multiplier against monsters                           |
| `shockwave_warden_damage_multiplier`  | `10.0`        | Damage multiplier against wardens                            |
| `shockwave_cooldown`                  | `30000`       | Cooldown of the shockwave effect in milliseconds (30s)       |
| `shockwave_darkness_duration`         | `60`          | Duration of the darkness effect applied to players in ticks  |

### Shockwave Scaling (Radius & Damage)

The shockwave scales dynamically based on voice volume. At minimum volume (e.g., `80dB`), the multiplier is 1.0x. At maximum volume (`100dB`), it scales up to the max multipliers configured.

**Radius**
- Minimum volume: `10.0` blocks
- Maximum volume: `20.0` blocks

**Damage Multipliers & Values** (At Default Settings)

| Target Entity | Base Multiplier | Min Damage (Threshold) | Max Damage (100dB) | Notes |
|---|---|---|---|---|
| **Player** | `0.5x` | **2.0** (❤️x1) | **4.0** (❤️x2) | Reduced friendly fire |
| **Normal (Animals, etc)** | `1.0x` | **4.0** (❤️x2) | **8.0** (❤️x4) | Standard damage |
| **Monster** | `5.0x` | **20.0** (❤️x10) | **40.0** (❤️x20) | Very effective against normal mobs |
| **Warden** | `10.0x` | **40.0** (❤️x20) | **80.0** (❤️x40) | Extreme damage against the Warden |

### Voice Volume (dB SPL) Reference

- **100 dB**: Microphone limit (clipping). The sound of yelling directly into the microphone or tapping it.
- **80 to 90 dB**: Very loud voice. Shouting in surprise or laughing loudly.
- **60 to 70 dB**: Normal conversation level. Relaxed talking on Discord.
- **40 to 50 dB**: Quiet sounds/noise. Whispering, keyboard typing, mouse clicks, or background noises.
- **30 dB or lower**: Almost silent (e.g., static white noise from the microphone itself).
