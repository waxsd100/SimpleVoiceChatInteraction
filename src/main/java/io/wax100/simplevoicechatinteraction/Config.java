package io.wax100.simplevoicechatinteraction;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * SimpleVoiceChatInteraction のMod設定クラス。
 * <p>
 * ボイスチャットによるスカルク振動の挙動とソニックショックウェーブ機能を制御する。
 * 設定値は Voice Chat のネットワークスレッドから読まれるため {@code volatile} で宣言。
 */
@Mod.EventBusSubscriber(modid = SimpleVoiceChatInteraction.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    public static final ForgeConfigSpec.BooleanValue GROUP_INTERACTION;
    public static final ForgeConfigSpec.DoubleValue WHISPER_VOLUME_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SNEAK_VOLUME_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SPRINT_VOLUME_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MICROPHONE_BASE_VALUE;
    public static final ForgeConfigSpec.DoubleValue MICROPHONE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue NOISE_GATE_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue ADVANCED_NOISE_FILTERING;
    public static final ForgeConfigSpec.IntValue VOICE_SCULK_FREQUENCY;
    public static final ForgeConfigSpec.IntValue MINIMUM_ACTIVATION_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue SHOCKWAVE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SHOCKWAVE_REQUIRE_DEEP_DARK;
    public static final ForgeConfigSpec.IntValue SHOCKWAVE_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue SHOCKWAVE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue SHOCKWAVE_100DB_RADIUS;
    public static final ForgeConfigSpec.DoubleValue SHOCKWAVE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue SHOCKWAVE_100DB_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue SHOCKWAVE_PLAYER_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SHOCKWAVE_MONSTER_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SHOCKWAVE_WARDEN_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SHOCKWAVE_KNOCKBACK_HORIZONTAL;
    public static final ForgeConfigSpec.DoubleValue SHOCKWAVE_KNOCKBACK_VERTICAL;
    public static final ForgeConfigSpec.DoubleValue SHOCKWAVE_OVERDRIVE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue SHOCKWAVE_DARKNESS_DURATION;
    public static final ForgeConfigSpec.IntValue SHOCKWAVE_COOLDOWN;
    static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    // インタラクションフィルター
    public static volatile boolean groupInteraction;

    // ── キャッシュ済み設定値（スレッド間アクセスのためvolatile） ──────────
    public static volatile double whisperVolumeMultiplier;
    public static volatile double sneakVolumeMultiplier;
    public static volatile double sprintVolumeMultiplier;
    // マイク音量スケーリング
    public static volatile double microphoneBaseValue;
    public static volatile double microphoneMultiplier;
    public static volatile double noiseGateThreshold;
    public static volatile boolean advancedNoiseFiltering;
    // スカルク振動
    public static volatile int voiceSculkFrequency;
    public static volatile int minimumActivationThreshold;
    // ショックウェーブ
    public static volatile boolean shockwaveEnabled;
    public static volatile boolean shockwaveRequireDeepDark;
    public static volatile int shockwaveThreshold;
    public static volatile double shockwaveRadius;
    public static volatile double shockwave100dbRadius;
    public static volatile double shockwaveDamage;
    public static volatile double shockwave100dbDamage;
    public static volatile double shockwavePlayerDamageMultiplier;
    public static volatile double shockwaveMonsterDamageMultiplier;
    public static volatile double shockwaveWardenDamageMultiplier;
    public static volatile double shockwaveKnockbackHorizontal;
    public static volatile double shockwaveKnockbackVertical;
    public static volatile double shockwaveOverdriveMultiplier;
    public static volatile int shockwaveDarknessDuration;
    public static volatile int shockwaveCooldown;

    static {
        BUILDER.push("interaction_filters");
        GROUP_INTERACTION = BUILDER
                .comment("---------------------------------------------------------",
                        "trueの場合、ボイスチャットグループでの発話がスカルク振動とショックウェーブを誘発する。",
                        "falseの場合、グループ通話ではいかなる効果も発生しない。",
                        "デフォルト: true")
                .define("group_interaction", true);

        WHISPER_VOLUME_MULTIPLIER = BUILDER
                .comment("---------------------------------------------------------",
                        "囁き声（Whisper）モード中の声の音量倍率（係数）。",
                        "0.5にすると、声の大きさが半減（約-6dB）した扱いになります。",
                        "0.0にすると囁き声中は完全に無音扱いになります。",
                        "範囲: 0.0～1.0。デフォルト: 0.5")
                .defineInRange("whisper_volume_multiplier", 0.5, 0.0, 1.0);

        SNEAK_VOLUME_MULTIPLIER = BUILDER
                .comment("---------------------------------------------------------",
                        "スニーク（しゃがみ）中の声の音量倍率（係数）。",
                        "0.5にすると、スニーク中の声の大きさが半減（約-6dB）した扱いになります。",
                        "0.0にするとスニーク中は完全に無音扱いになります。",
                        "範囲: 0.0～1.0。デフォルト: 0.5")
                .defineInRange("sneak_volume_multiplier", 0.5, 0.0, 1.0);

        SPRINT_VOLUME_MULTIPLIER = BUILDER
                .comment("---------------------------------------------------------",
                        "ダッシュ（スプリント）中の声の音量倍率（係数）。",
                        "2.5にすると、ダッシュ中の声の大きさが2.5倍（約+8.0dB）に増幅されます。",
                        "1.0にするとダッシュによる変化は発生しません。",
                        "範囲: 0.0～10.0。デフォルト: 2.5")
                .defineInRange("sprint_volume_multiplier", 2.5, 0.0, 10.0);
        BUILDER.pop();

        BUILDER.push("microphone_scaling");
        MICROPHONE_BASE_VALUE = BUILDER
                .comment("---------------------------------------------------------",
                        "マイクから取得した音声レベル（dBFS）をゲーム内の音量（dB SPL）に変換する際のベース値。",
                        "この値を上げると全体的な音量が底上げされます。",
                        "範囲: 0.0～200.0。デフォルト: 100.0")
                .defineInRange("microphone_base_value", 100.0, 0.0, 200.0);

        MICROPHONE_MULTIPLIER = BUILDER
                .comment("---------------------------------------------------------",
                        "マイクから取得した音声レベル（dBFS）に乗算する係数。",
                        "マイクの感度が低い場合や、叫んでも200dBに届かない場合はこの値を上げてください。",
                        "範囲: 0.1～10.0。デフォルト: 2.0")
                .defineInRange("microphone_multiplier", 2.0, 0.1, 10.0);

        NOISE_GATE_THRESHOLD = BUILDER
                .comment("---------------------------------------------------------",
                        "環境音やマイクのノイズ（ホワイトノイズ）をカットするための閾値（ノイズゲート）。",
                        "この数値（dB）以下の小さな音は「完全に無音（0dB）」として扱われます。",
                        "扇風機の音やキーボードの打鍵音などを拾ってしまう場合は、この数値を上げて調整してください。",
                        "範囲: 0.0～100.0。デフォルト: 40.0")
                .defineInRange("noise_gate_threshold", 40.0, 0.0, 100.0);

        ADVANCED_NOISE_FILTERING = BUILDER
                .comment("---------------------------------------------------------",
                        "高度なノイズ抑制（バンドパスフィルターとゼロ交差率判定）を有効にする。",
                        "オンにすると、人間の声の周波数帯（約300Hz〜3000Hz）以外の音や、",
                        "キーボードの打鍵音などの高周波ノイズを自動的に計算から除外します。",
                        "デフォルト: true")
                .define("advanced_noise_filtering", true);
        BUILDER.pop();

        BUILDER.push("sculk_vibration");
        VOICE_SCULK_FREQUENCY = BUILDER
                .comment("---------------------------------------------------------",
                        "声の振動に対するスカルクセンサーの周波数（レッドストーン信号強度）。",
                        "スカルクセンサーが出力するレッドストーン信号の強さを決定する。",
                        "範囲: 1-15。デフォルト: 7（entity_damageと同じ周波数）")
                .defineInRange("voice_sculk_frequency", 7, 1, 15);

        MINIMUM_ACTIVATION_THRESHOLD = BUILDER
                .comment("---------------------------------------------------------",
                        "スカルク振動を作動させるために必要な最小音量（dB SPL相当）。",
                        "日常の目安: 30(ささやき声), 60(普通の会話), 85(騒音)",
                        "この値より小さい音声は無視される。",
                        "範囲: 0～200。デフォルト: 60")
                .defineInRange("minimum_activation_threshold", 60, 0, 200);
        BUILDER.pop();

        BUILDER.push("sonic_shockwave");
        SHOCKWAVE_ENABLED = BUILDER
                .comment("---------------------------------------------------------",
                        "ソニックショックウェーブ機能を有効にする。",
                        "プレイヤーの声がショックウェーブ閾値を超えた場合、",
                        "周囲のエンティティにダメージを与え、近くのプレイヤーに暗闇を付与し、",
                        "地面が震えるような演出を再生する。",
                        "デフォルト: true")
                .define("shockwave_enabled", true);

        SHOCKWAVE_REQUIRE_DEEP_DARK = BUILDER
                .comment("---------------------------------------------------------",
                        "ショックウェーブの発動をディープダークバイオーム（または otherside ディメンション）に限定する。",
                        "falseに設定すると、地上やネザーなど「どこでも」発動できるようになります。",
                        "デフォルト: true")
                .define("shockwave_require_deep_dark", true);

        SHOCKWAVE_THRESHOLD = BUILDER
                .comment("---------------------------------------------------------",
                        "ショックウェーブ効果を発動するためのオーディオレベル閾値（dB SPL相当）。",
                        "通常はminimum_activation_thresholdより高い値（大きい音量）に設定すべき。",
                        "範囲: 0～200。デフォルト: 85")
                .defineInRange("shockwave_threshold", 85, 0, 200);

        SHOCKWAVE_RADIUS = BUILDER
                .comment("---------------------------------------------------------",
                        "ショックウェーブが閾値ギリギリで発動した時の基本半径（ブロック単位）。",
                        "範囲: 1.0～100.0。デフォルト: 10.0")
                .defineInRange("shockwave_radius", 10.0, 1.0, 100.0);

        SHOCKWAVE_100DB_RADIUS = BUILDER
                .comment("---------------------------------------------------------",
                        "100dB（最大の声）の時のショックウェーブ範囲（ブロック単位）。",
                        "デフォルト: 20.0（閾値の時の2倍）")
                .defineInRange("shockwave_100db_radius", 20.0, 1.0, 200.0);

        SHOCKWAVE_DAMAGE = BUILDER
                .comment("---------------------------------------------------------",
                        "ショックウェーブが閾値ギリギリで発動した時の基本ダメージ量。",
                        "1.0 = ハート半分。範囲: 0.0～100.0。デフォルト: 4.0（ハート2個分）")
                .defineInRange("shockwave_damage", 4.0, 0.0, 100.0);

        SHOCKWAVE_100DB_DAMAGE = BUILDER
                .comment("---------------------------------------------------------",
                        "100dB（最大の声）の時のショックウェーブ基本ダメージ量。",
                        "デフォルト: 8.0（閾値の時の2倍、ハート4個分）")
                .defineInRange("shockwave_100db_damage", 8.0, 0.0, 200.0);

        SHOCKWAVE_OVERDRIVE_MULTIPLIER = BUILDER
                .comment("---------------------------------------------------------",
                        "オーバードライブ係数（100dBを超えた場合のさらなる増加倍率）。",
                        "200dB（最大）の時に、100dB時の何倍の威力・範囲になるかを指定します。",
                        "デフォルト: 2.0（200dBで100dB時の2倍の威力になる）")
                .defineInRange("shockwave_overdrive_multiplier", 2.0, 1.0, 100.0);

        SHOCKWAVE_PLAYER_DAMAGE_MULTIPLIER = BUILDER
                .comment("---------------------------------------------------------",
                        "プレイヤーに対するショックウェーブのダメージ倍率。",
                        "デフォルト: 0.5（ダメージを半減）")
                .defineInRange("shockwave_player_damage_multiplier", 0.5, 0.0, 10.0);

        SHOCKWAVE_MONSTER_DAMAGE_MULTIPLIER = BUILDER
                .comment("---------------------------------------------------------",
                        "モンスター（敵対モブ）に対するショックウェーブのダメージ倍率。",
                        "デフォルト: 10.0（ダメージを10倍に増加して大ダメージ）")
                .defineInRange("shockwave_monster_damage_multiplier", 10.0, 0.0, 100.0);

        SHOCKWAVE_WARDEN_DAMAGE_MULTIPLIER = BUILDER
                .comment("---------------------------------------------------------",
                        "ウォーデンに対するショックウェーブのダメージ倍率。",
                        "デフォルト: 20.0（ウォーデンに対して超特大ダメージ）")
                .defineInRange("shockwave_warden_damage_multiplier", 20.0, 0.0, 1000.0);

        SHOCKWAVE_KNOCKBACK_HORIZONTAL = BUILDER
                .comment("---------------------------------------------------------",
                        "ショックウェーブの水平ノックバック強度（横方向への吹き飛びやすさ）。",
                        "デフォルト: 2.3")
                .defineInRange("shockwave_knockback_horizontal", 2.3, 0.0, 100.0);

        SHOCKWAVE_KNOCKBACK_VERTICAL = BUILDER
                .comment("---------------------------------------------------------",
                        "ショックウェーブの垂直ノックバック強度（上方向への浮き上がりやすさ）。",
                        "デフォルト: 0.4")
                .defineInRange("shockwave_knockback_vertical", 0.4, 0.0, 100.0);

        SHOCKWAVE_DARKNESS_DURATION = BUILDER
                .comment("---------------------------------------------------------",
                        "周囲のプレイヤーに付与される暗闇エフェクトの持続時間（tick単位）。",
                        "20 tick = 1秒。",
                        "範囲: 0～6000。デフォルト: 60（3秒）")
                .defineInRange("shockwave_darkness_duration", 60, 0, 6000);

        SHOCKWAVE_COOLDOWN = BUILDER
                .comment("---------------------------------------------------------",
                        "プレイヤーごとのショックウェーブ発動クールダウン（ミリ秒単位）。",
                        "範囲: 1000～300000。デフォルト: 30000（30秒）")
                .defineInRange("shockwave_cooldown", 30000, 1000, 300000);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        reloadCachedValues();
    }

    public static void reloadCachedValues() {
        groupInteraction = GROUP_INTERACTION.get();
        whisperVolumeMultiplier = WHISPER_VOLUME_MULTIPLIER.get();
        sneakVolumeMultiplier = SNEAK_VOLUME_MULTIPLIER.get();
        sprintVolumeMultiplier = SPRINT_VOLUME_MULTIPLIER.get();

        microphoneBaseValue = MICROPHONE_BASE_VALUE.get();
        microphoneMultiplier = MICROPHONE_MULTIPLIER.get();
        noiseGateThreshold = NOISE_GATE_THRESHOLD.get();
        advancedNoiseFiltering = ADVANCED_NOISE_FILTERING.get();

        voiceSculkFrequency = VOICE_SCULK_FREQUENCY.get();
        minimumActivationThreshold = MINIMUM_ACTIVATION_THRESHOLD.get();

        shockwaveEnabled = SHOCKWAVE_ENABLED.get();
        shockwaveRequireDeepDark = SHOCKWAVE_REQUIRE_DEEP_DARK.get();
        shockwaveThreshold = SHOCKWAVE_THRESHOLD.get();
        shockwaveRadius = SHOCKWAVE_RADIUS.get();
        shockwave100dbRadius = SHOCKWAVE_100DB_RADIUS.get();
        shockwaveDamage = SHOCKWAVE_DAMAGE.get();
        shockwave100dbDamage = SHOCKWAVE_100DB_DAMAGE.get();
        shockwavePlayerDamageMultiplier = SHOCKWAVE_PLAYER_DAMAGE_MULTIPLIER.get();
        shockwaveMonsterDamageMultiplier = SHOCKWAVE_MONSTER_DAMAGE_MULTIPLIER.get();
        shockwaveWardenDamageMultiplier = SHOCKWAVE_WARDEN_DAMAGE_MULTIPLIER.get();
        shockwaveKnockbackHorizontal = SHOCKWAVE_KNOCKBACK_HORIZONTAL.get();
        shockwaveKnockbackVertical = SHOCKWAVE_KNOCKBACK_VERTICAL.get();
        shockwaveOverdriveMultiplier = SHOCKWAVE_OVERDRIVE_MULTIPLIER.get();
        shockwaveDarknessDuration = SHOCKWAVE_DARKNESS_DURATION.get();
        shockwaveCooldown = SHOCKWAVE_COOLDOWN.get();
    }
}
