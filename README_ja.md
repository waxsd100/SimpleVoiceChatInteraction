*他の言語で読む: [English](README.md)*

# Simple Voice Chat Interaction

このサーバーサイドForge用Modは、Simple Voice Chatの音声をMinecraftの世界と相互作用させることができます。

## 機能

- ボイスチャットで喋るとスカルクセンサーが反応する
- ボイスチャットで喋るとウォーデンに検知される
- ボイスチャットで大声を出すと、周囲のエンティティにダメージを与えるソニックショックウェーブが発生する
- 声の大きさ（dB）に応じてショックウェーブの範囲とダメージが動的に変化する
- モンスターおよびウォーデンに対するボーナスダメージ倍率
- 囁き声（Whisper）およびグループチャットでの振動発生のオプション対応
- スニーク中の会話で振動を発生させない機能（オンオフ可能）
- 音声テスト用のデバッグコマンド（`/voice_debug <dB>`）

## 設定値

*config/simplevoicechatinteraction-common.toml*

| 名前                                  | デフォルト値  | 説明                                                     |
|---------------------------------------|---------------|----------------------------------------------------------|
| `group_interaction`                   | `false`       | グループチャットでの会話で振動を発生させるか             |
| `whisper_interaction`                 | `false`       | 囁き声で振動を発生させるか                               |
| `sneak_interaction`                   | `false`       | スニーク中の会話で振動を発生させるか                     |
| `voice_sculk_frequency`               | `9`           | 声によって発生するスカルク振動の周波数                   |
| `minimum_activation_threshold`        | `-30`         | スカルクセンサーが反応する最小音量(dB)                   |
| `shockwave_enabled`                   | `true`        | ショックウェーブ機能を有効にするか                       |
| `shockwave_threshold`                 | `-10`         | ショックウェーブが発動する最小音量(dB)                   |
| `shockwave_radius`                    | `10.0`        | ショックウェーブの基本範囲（ブロック単位）               |
| `shockwave_max_radius_multiplier`     | `2.0`         | 最大音量(0dB)時の範囲倍率                                |
| `shockwave_damage`                    | `4.0`         | ショックウェーブの基本ダメージ                           |
| `shockwave_max_damage_multiplier`     | `2.0`         | 最大音量(0dB)時のダメージ倍率                            |
| `shockwave_player_damage_multiplier`  | `0.5`         | プレイヤーに対するダメージ倍率                           |
| `shockwave_monster_damage_multiplier` | `5.0`         | モンスターに対するダメージ倍率                           |
| `shockwave_warden_damage_multiplier`  | `10.0`        | ウォーデンに対するダメージ倍率                           |
| `shockwave_cooldown`                  | `20`          | ショックウェーブのクールダウン(tick単位)                 |
| `shockwave_darkness_duration`         | `60`          | プレイヤーに付与される暗闇エフェクトの持続時間(tick単位) |

### デジタル音量（dBFS）の目安

- **0 dB**：マイクの限界値（音が割れる）。マイクに口を近づけて大声で叫んだり、マイクを叩いたりした時の音。
- **-10 〜 -20 dB**：かなり大きな声。ゲームで驚いて「うわっ！」と叫んだり、大爆笑している時の音量。
- **-30 〜 -40 dB**：普通の会話レベル。Discordなどでリラックスして喋っている時の音量。
- **-50 〜 -60 dB**：小さな音・雑音。ささやき声や、マイクが拾ってしまう「キーボードのタイピング音」「マウスのクリック音」「遠くの車の音」など。
- **-70 dB 以下**：ほぼ無音（マイク自体のサーッというホワイトノイズなど）。
