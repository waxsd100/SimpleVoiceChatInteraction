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
@Mod.EventBusSubscriber(modid = Simplevoicechatinteraction.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ── インタラクションフィルター ──────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue GROUP_INTERACTION = BUILDER
            .comment("trueの場合、ボイスチャットグループでの発話がスカルク振動とショックウェーブを誘発する。",
                    "falseの場合、グループ通話ではいかなる効果も発生しない。",
                    "デフォルト: false")
            .define("group_interaction", false);

    private static final ForgeConfigSpec.DoubleValue WHISPER_VOLUME_MULTIPLIER = BUILDER
            .comment("囁き声（Whisper）モード中の声の音量倍率（係数）。",
                    "0.5にすると、声の大きさが半減（約-6dB）した扱いになります。",
                    "0.0にすると囁き声中は完全に無音扱いになります。",
                    "範囲: 0.0～1.0。デフォルト: 0.5")
            .defineInRange("whisper_volume_multiplier", 0.5, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue SNEAK_VOLUME_MULTIPLIER = BUILDER
            .comment("スニーク（しゃがみ）中の声の音量倍率（係数）。",
                    "0.5にすると、スニーク中の声の大きさが半減（約-6dB）した扱いになります。",
                    "0.0にするとスニーク中は完全に無音扱いになります。",
                    "範囲: 0.0～1.0。デフォルト: 0.5")
            .defineInRange("sneak_volume_multiplier", 0.5, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue SPRINT_VOLUME_MULTIPLIER = BUILDER
            .comment("ダッシュ（スプリント）中の声の音量倍率（係数）。",
                    "1.5にすると、ダッシュ中の声の大きさが1.5倍（約+3.5dB）に増幅されます。",
                    "1.0にするとダッシュによる変化は発生しません。",
                    "範囲: 0.0～10.0。デフォルト: 1.5")
            .defineInRange("sprint_volume_multiplier", 1.5, 0.0, 10.0);

    // ── スカルク振動設定 ─────────────────────────────────────────────────

    private static final ForgeConfigSpec.IntValue VOICE_SCULK_FREQUENCY = BUILDER
            .comment("声の振動に対するスカルクセンサーの周波数（レッドストーン信号強度）。",
                    "スカルクセンサーが出力するレッドストーン信号の強さを決定する。",
                    "範囲: 1-15。デフォルト: 7（entity_damageと同じ周波数）")
            .defineInRange("voice_sculk_frequency", 7, 1, 15);

    private static final ForgeConfigSpec.IntValue MINIMUM_ACTIVATION_THRESHOLD = BUILDER
            .comment("スカルク振動を作動させるために必要な最小オーディオレベル（dB）。",
                    "この値より小さい音声は無視される。",
                    "値が低いほど感度が高く、値が高いほど感度が低い。",
                    "範囲: -80～0。デフォルト: -30")
            .defineInRange("minimum_activation_threshold", -30, -80, 0);

    // ── ソニックショックウェーブ設定 ─────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue SHOCKWAVE_ENABLED = BUILDER
            .comment("ソニックショックウェーブ機能を有効にする。",
                    "プレイヤーの声がショックウェーブ閾値を超えた場合、",
                    "周囲のエンティティにダメージを与え、近くのプレイヤーに暗闇を付与し、",
                    "地面が震えるような演出を再生する。",
                    "デフォルト: true")
            .define("shockwave_enabled", true);

    private static final ForgeConfigSpec.IntValue SHOCKWAVE_THRESHOLD = BUILDER
            .comment("ショックウェーブ効果を発動するためのオーディオレベル閾値（dB）。",
                    "通常はminimum_activation_thresholdより高い値（大きい音量）に設定すべき。",
                    "範囲: -80～0。デフォルト: -10")
            .defineInRange("shockwave_threshold", -10, -80, 0);

    private static final ForgeConfigSpec.DoubleValue SHOCKWAVE_RADIUS = BUILDER
            .comment("ショックウェーブ効果の基本半径（ブロック単位）。",
                    "この範囲内にいるエンティティがダメージと暗闇を受ける。",
                    "範囲: 1.0～100.0。デフォルト: 10.0")
            .defineInRange("shockwave_radius", 10.0, 1.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue SHOCKWAVE_MAX_RADIUS_MULTIPLIER = BUILDER
            .comment("最大音量（0dB）の時のショックウェーブ範囲の倍率。",
                    "デフォルト: 2.0（閾値ギリギリの時の2倍の範囲になる）")
            .defineInRange("shockwave_max_radius_multiplier", 2.0, 1.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue SHOCKWAVE_DAMAGE = BUILDER
            .comment("ショックウェーブが周囲のエンティティに与える基本ダメージ量。",
                    "1.0 = ハート半分。sonic_boomダメージタイプを使用（防具を貫通）。",
                    "範囲: 0.0～100.0。デフォルト: 4.0（ハート2個分）")
            .defineInRange("shockwave_damage", 4.0, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue SHOCKWAVE_MAX_DAMAGE_MULTIPLIER = BUILDER
            .comment("最大音量（0dB）の時のショックウェーブダメージの倍率。",
                    "デフォルト: 2.0（閾値ギリギリの時の2倍のダメージになる）")
            .defineInRange("shockwave_max_damage_multiplier", 2.0, 1.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue SHOCKWAVE_PLAYER_DAMAGE_MULTIPLIER = BUILDER
            .comment("プレイヤーに対するショックウェーブのダメージ倍率。",
                    "デフォルト: 0.5（ダメージを半減）")
            .defineInRange("shockwave_player_damage_multiplier", 0.5, 0.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue SHOCKWAVE_MONSTER_DAMAGE_MULTIPLIER = BUILDER
            .comment("モンスター（敵対モブ）に対するショックウェーブのダメージ倍率。",
                    "デフォルト: 5.0（ダメージを5倍に増加して大ダメージ）")
            .defineInRange("shockwave_monster_damage_multiplier", 5.0, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue SHOCKWAVE_WARDEN_DAMAGE_MULTIPLIER = BUILDER
            .comment("ウォーデンに対するショックウェーブのダメージ倍率。",
                    "デフォルト: 10.0（ウォーデンに対して超特大ダメージ）")
            .defineInRange("shockwave_warden_damage_multiplier", 10.0, 0.0, 1000.0);

    private static final ForgeConfigSpec.IntValue SHOCKWAVE_DARKNESS_DURATION = BUILDER
            .comment("周囲のプレイヤーに付与される暗闇エフェクトの持続時間（tick単位）。",
                    "20 tick = 1秒。",
                    "範囲: 0～6000。デフォルト: 100（5秒）")
            .defineInRange("shockwave_darkness_duration", 100, 0, 6000);

    private static final ForgeConfigSpec.IntValue SHOCKWAVE_COOLDOWN = BUILDER
            .comment("プレイヤーごとのショックウェーブ発動クールダウン（ミリ秒単位）。",
                    "範囲: 1000～60000。デフォルト: 5000（5秒）")
            .defineInRange("shockwave_cooldown", 5000, 1000, 60000);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ── キャッシュ済み設定値（スレッド間アクセスのためvolatile） ──────────

    // インタラクションフィルター
    public static volatile boolean groupInteraction;
    public static volatile double whisperVolumeMultiplier;
    public static volatile double sneakVolumeMultiplier;
    public static volatile double sprintVolumeMultiplier;

    // スカルク振動
    public static volatile int voiceSculkFrequency;
    public static volatile int minimumActivationThreshold;

    // ショックウェーブ
    public static volatile boolean shockwaveEnabled;
    public static volatile int shockwaveThreshold;
    public static volatile double shockwaveRadius;
    public static volatile double shockwaveMaxRadiusMultiplier;
    public static volatile double shockwaveDamage;
    public static volatile double shockwaveMaxDamageMultiplier;
    public static volatile double shockwavePlayerDamageMultiplier;
    public static volatile double shockwaveMonsterDamageMultiplier;
    public static volatile double shockwaveWardenDamageMultiplier;
    public static volatile int shockwaveDarknessDuration;
    public static volatile int shockwaveCooldown;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        groupInteraction = GROUP_INTERACTION.get();
        whisperVolumeMultiplier = WHISPER_VOLUME_MULTIPLIER.get();
        sneakVolumeMultiplier = SNEAK_VOLUME_MULTIPLIER.get();
        sprintVolumeMultiplier = SPRINT_VOLUME_MULTIPLIER.get();

        voiceSculkFrequency = VOICE_SCULK_FREQUENCY.get();
        minimumActivationThreshold = MINIMUM_ACTIVATION_THRESHOLD.get();

        shockwaveEnabled = SHOCKWAVE_ENABLED.get();
        shockwaveThreshold = SHOCKWAVE_THRESHOLD.get();
        shockwaveRadius = SHOCKWAVE_RADIUS.get();
        shockwaveMaxRadiusMultiplier = SHOCKWAVE_MAX_RADIUS_MULTIPLIER.get();
        shockwaveDamage = SHOCKWAVE_DAMAGE.get();
        shockwaveMaxDamageMultiplier = SHOCKWAVE_MAX_DAMAGE_MULTIPLIER.get();
        shockwavePlayerDamageMultiplier = SHOCKWAVE_PLAYER_DAMAGE_MULTIPLIER.get();
        shockwaveMonsterDamageMultiplier = SHOCKWAVE_MONSTER_DAMAGE_MULTIPLIER.get();
        shockwaveWardenDamageMultiplier = SHOCKWAVE_WARDEN_DAMAGE_MULTIPLIER.get();
        shockwaveDarknessDuration = SHOCKWAVE_DARKNESS_DURATION.get();
        shockwaveCooldown = SHOCKWAVE_COOLDOWN.get();
    }
}
