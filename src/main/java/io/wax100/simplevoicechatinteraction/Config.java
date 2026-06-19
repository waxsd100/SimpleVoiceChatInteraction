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

    private static final ForgeConfigSpec.BooleanValue WHISPER_INTERACTION = BUILDER
            .comment("trueの場合、ささやき声がスカルク振動とショックウェーブを誘発する。",
                    "falseの場合、ささやき声ではいかなる効果も発生しない。",
                    "デフォルト: false")
            .define("whisper_interaction", false);

    private static final ForgeConfigSpec.BooleanValue SNEAK_INTERACTION = BUILDER
            .comment("trueの場合、スニーク中の発話がスカルク振動とショックウェーブを誘発する。",
                    "falseの場合、スニーク中のプレイヤーの声ではいかなる効果も発生しない。",
                    "デフォルト: false")
            .define("sneak_interaction", false);

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
                    "範囲: -80～0。デフォルト: -50")
            .defineInRange("minimum_activation_threshold", -50, -80, 0);

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
                    "範囲: -80～0。デフォルト: -30")
            .defineInRange("shockwave_threshold", -30, -80, 0);

    private static final ForgeConfigSpec.DoubleValue SHOCKWAVE_RADIUS = BUILDER
            .comment("ショックウェーブ効果の半径（ブロック単位）。",
                    "この半径内のエンティティが影響を受ける。",
                    "範囲: 1.0～50.0。デフォルト: 10.0")
            .defineInRange("shockwave_radius", 10.0, 1.0, 50.0);

    private static final ForgeConfigSpec.DoubleValue SHOCKWAVE_DAMAGE = BUILDER
            .comment("ショックウェーブが周囲のエンティティに与えるダメージ量。",
                    "1.0 = ハート半分。sonic_boomダメージタイプを使用（防具を貫通）。",
                    "範囲: 0.0～100.0。デフォルト: 4.0（ハート2個分）")
            .defineInRange("shockwave_damage", 4.0, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue SHOCKWAVE_PLAYER_DAMAGE_MULTIPLIER = BUILDER
            .comment("プレイヤーに対するショックウェーブのダメージ倍率。",
                    "デフォルト: 0.5（ダメージを半減）")
            .defineInRange("shockwave_player_damage_multiplier", 0.5, 0.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue SHOCKWAVE_MONSTER_DAMAGE_MULTIPLIER = BUILDER
            .comment("モンスター（敵対モブ）に対するショックウェーブのダメージ倍率。",
                    "デフォルト: 5.0（ダメージを5倍に増加して大ダメージ）")
            .defineInRange("shockwave_monster_damage_multiplier", 5.0, 0.0, 100.0);

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
    public static volatile boolean whisperInteraction;
    public static volatile boolean sneakInteraction;

    // スカルク振動
    public static volatile int voiceSculkFrequency;
    public static volatile int minimumActivationThreshold;

    // ショックウェーブ
    public static volatile boolean shockwaveEnabled;
    public static volatile int shockwaveThreshold;
    public static volatile double shockwaveRadius;
    public static volatile double shockwaveDamage;
    public static volatile double shockwavePlayerDamageMultiplier;
    public static volatile double shockwaveMonsterDamageMultiplier;
    public static volatile int shockwaveDarknessDuration;
    public static volatile int shockwaveCooldown;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        groupInteraction = GROUP_INTERACTION.get();
        whisperInteraction = WHISPER_INTERACTION.get();
        sneakInteraction = SNEAK_INTERACTION.get();

        voiceSculkFrequency = VOICE_SCULK_FREQUENCY.get();
        minimumActivationThreshold = MINIMUM_ACTIVATION_THRESHOLD.get();

        shockwaveEnabled = SHOCKWAVE_ENABLED.get();
        shockwaveThreshold = SHOCKWAVE_THRESHOLD.get();
        shockwaveRadius = SHOCKWAVE_RADIUS.get();
        shockwaveDamage = SHOCKWAVE_DAMAGE.get();
        shockwavePlayerDamageMultiplier = SHOCKWAVE_PLAYER_DAMAGE_MULTIPLIER.get();
        shockwaveMonsterDamageMultiplier = SHOCKWAVE_MONSTER_DAMAGE_MULTIPLIER.get();
        shockwaveDarknessDuration = SHOCKWAVE_DARKNESS_DURATION.get();
        shockwaveCooldown = SHOCKWAVE_COOLDOWN.get();
    }
}
