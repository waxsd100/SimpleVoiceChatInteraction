*Read this in other languages: [日本語](README_ja.md)*

# Simple Voice Chat Interaction

This server side Forge mod allows Simple Voice Chat to interact with your Minecraft world.

## Features

- Talking in voice chat activates sculk sensors
- Talking in voice chat is detected by the warden
- Yelling in voice chat unleashes a sonic shockwave that damages nearby entities
- Dynamic shockwave radius and damage based on voice volume
- Bonus damage multipliers against monsters and wardens
- Optional support for whisper and group chat vibrations
- Talking while sneaking doesn't trigger vibrations (Configurable)
- Voice testing command (`/voice_debug <dB>`)

## Config Values

*config/simplevoicechatinteraction-common.toml*

| Name                                  | Default Value | Description                                                  |
|---------------------------------------|---------------|--------------------------------------------------------------|
| `group_interaction`                   | `false`       | If talking in groups should trigger vibrations               |
| `whisper_interaction`                 | `false`       | If whispering should trigger vibrations                      |
| `sneak_interaction`                   | `false`       | If talking while sneaking should trigger vibrations          |
| `voice_sculk_frequency`               | `9`           | The frequency of the voice vibration                         |
| `minimum_activation_threshold`        | `-30`         | The audio level threshold to activate the sculk sensor in dB |
| `shockwave_enabled`                   | `true`        | If the sonic shockwave feature is enabled                    |
| `shockwave_threshold`                 | `-10`         | The audio level threshold to unleash a shockwave in dB       |
| `shockwave_radius`                    | `10.0`        | Base radius of the shockwave effect                          |
| `shockwave_max_radius_multiplier`     | `2.0`         | Max radius multiplier at maximum volume (0dB)                |
| `shockwave_damage`                    | `4.0`         | Base damage of the shockwave effect                          |
| `shockwave_max_damage_multiplier`     | `2.0`         | Max damage multiplier at maximum volume (0dB)                |
| `shockwave_player_damage_multiplier`  | `0.5`         | Damage multiplier against players                            |
| `shockwave_monster_damage_multiplier` | `5.0`         | Damage multiplier against monsters                           |
| `shockwave_warden_damage_multiplier`  | `10.0`        | Damage multiplier against wardens                            |
| `shockwave_cooldown`                  | `20`          | Cooldown of the shockwave effect in ticks                    |
| `shockwave_darkness_duration`         | `60`          | Duration of the darkness effect applied to players in ticks  |
