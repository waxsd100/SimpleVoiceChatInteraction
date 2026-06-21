package io.wax100.simplevoicechatinteraction;

import com.mojang.logging.LogUtils;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Voice Chat API プラグイン。
 * プレイヤーの発話を検知し、スカルク振動やソニックショックウェーブを発生させる。
 * <p>
 * オーケストレーターとして機能し、実際の処理は各コンポーネントに委譲する。
 */
@ForgeVoicechatPlugin
public class VoiceChatSculkPlugin implements VoicechatPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * ベースラインEMAの定常状態の学習率（下限値）。
     * 初期は適応的に高いalphaを使用し、安定後はこの値で継続的に追従する。
     * alpha=0.02 → 約50パケット（1秒）で大きな変化に追従。
     */
    private static final double BASELINE_ALPHA = 0.02;

    /**
     * 適応的学習率の分子。effectiveAlpha = max(BASELINE_ALPHA, NUMERATOR / (n + 1))
     */
    private static final double ADAPTIVE_ALPHA_NUMERATOR = 2.0;

    /**
     * 正規化が有効になるまでの最小サンプル数。
     * 約100パケット（約2秒の発話）でキャリブレーション完了。
     */
    private static final int BASELINE_MIN_SAMPLES = 100;

    /**
     * EMAの平滑化係数。1パケットの突発的スパイクを除去しつつ、
     * 数パケットかけて滑らかに音量変化を反映する。
     */
    private static final double EMA_ALPHA = 0.35;

    /**
     * ウール防音チェックの更新間隔（tick）。20tick = 1秒。
     */
    private static final int WOOL_CHECK_INTERVAL = 20;

    /**
     * デバッグコマンド等からアクセスするためのシングルトンインスタンス
     */
    public static volatile VoiceChatSculkPlugin instance;

    /**
     * 音声正規化のベースラインデータ。イミュータブルでスレッド安全。
     *
     * @param ema         ベースラインEMA値
     * @param sampleCount 累積サンプル数
     */
    record BaselineData(double ema, double sampleCount) {}

    private final CooldownManager cooldownManager = new CooldownManager();
    private final SculkVibrationEmitter sculkEmitter = new SculkVibrationEmitter();
    private final ShockwaveExecutor shockwaveExecutor = new ShockwaveExecutor();
    private final ConcurrentHashMap<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Double> emaDbMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> activeMonitors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BaselineData> voiceBaselineMap = new ConcurrentHashMap<>();
    /**
     * ウール防音のdBオフセットキャッシュ。
     * メインスレッドのtickで更新、ネットワークスレッドのprocessAudioInteractionで読み取り。
     * 値は0.0（減衰なし）から負の値（dB減衰）。
     */
    private final ConcurrentHashMap<UUID, Double> woolDampeningMap = new ConcurrentHashMap<>();

    private volatile VoicechatApi voicechatApi;

    /**
     * プレイヤーがディープダークバイオームまたは otherside ディメンションにいるかを判定する。
     * サーバーメインスレッドで呼ぶこと。
     *
     * @param player 判定対象のプレイヤー
     * @return ディープダークまたは otherside にいる場合 true
     */
    public static boolean isInDeepDark(ServerPlayer player) {
        return player.serverLevel().getBiome(player.blockPosition()).is(Biomes.DEEP_DARK)
                || "deeperdarker:otherside".equals(player.serverLevel().dimension().location().toString());
    }

    @Override
    public String getPluginId() {
        return SimpleVoiceChatInteraction.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.voicechatApi = api;
        instance = this;
        LOGGER.info("[SimpleVoiceChatInteraction] VoiceChatプラグイン初期化完了");
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * CooldownManagerへのアクセサ。
     */
    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    /**
     * モニターマップへのアクセサ。
     *
     * @return activeMonitors マップ
     */
    public ConcurrentHashMap<UUID, UUID> getActiveMonitors() {
        return activeMonitors;
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        OpusDecoder decoder = decoders.remove(uuid);
        if (decoder != null && !decoder.isClosed()) {
            decoder.close();
        }
        emaDbMap.remove(uuid);
        voiceBaselineMap.remove(uuid);
        woolDampeningMap.remove(uuid);
        activeMonitors.remove(uuid);
        // このプレイヤーを監視しているモニターも解除
        activeMonitors.values().removeIf(targetUUID -> targetUUID.equals(uuid));
    }

    /**
     * 1秒ごとにプレイヤー周囲のウールブロックを検査し、防音オフセットをキャッシュする。
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer serverPlayer)) return;
        if (!Config.woolDampening) return;

        // 1秒ごとに更新（負荷軽減）
        if (serverPlayer.tickCount % WOOL_CHECK_INTERVAL != 0) return;

        double dampening = calculateWoolDampening(serverPlayer);
        if (dampening < 0.0) {
            woolDampeningMap.put(serverPlayer.getUUID(), dampening);
        } else {
            woolDampeningMap.remove(serverPlayer.getUUID());
        }
    }

    /**
     * プレイヤー周囲6方向（床・天井・4壁）のウールブロック数を数え、
     * 比率に応じたdB減衰量を返す。
     *
     * @param player 対象プレイヤー
     * @return dBオフセット（0.0=減衰なし、負の値=減衰あり）
     */
    private static double calculateWoolDampening(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos feetPos = player.blockPosition();
        BlockPos headPos = feetPos.above();

        int woolCount = 0;

        // 床
        if (level.getBlockState(feetPos.below()).is(BlockTags.WOOL)) woolCount++;
        // 天井
        if (level.getBlockState(headPos.above()).is(BlockTags.WOOL)) woolCount++;
        // 4方向の壁（足元または頭の高さにウールがあればその方向をカウント）
        BlockPos[] feetNeighbors = {feetPos.north(), feetPos.south(), feetPos.east(), feetPos.west()};
        BlockPos[] headNeighbors = {headPos.north(), headPos.south(), headPos.east(), headPos.west()};
        for (int i = 0; i < 4; i++) {
            if (level.getBlockState(feetNeighbors[i]).is(BlockTags.WOOL)
                    || level.getBlockState(headNeighbors[i]).is(BlockTags.WOOL)) {
                woolCount++;
            }
        }

        // 6方向中のウール比率 × 最大減衰量
        return (woolCount / 6.0) * Config.woolDampeningMaxDb;
    }

    /**
     * 音量修飾子（倍率）をdBに変換して適用する。
     * multiplierが0以下の場合は負の値を返し、呼び出し側で無音として処理する。
     *
     * @param currentDb 現在のdB値
     * @param multiplier 音量倍率（0以下で無音）
     * @return 修飾後のdB値。multiplierが0以下の場合は -1.0
     */
    private static double applyVolumeModifier(double currentDb, double multiplier) {
        if (multiplier <= 0.0) return -1.0;
        return currentDb + 20.0 * Math.log10(multiplier);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        VoicechatConnection senderConnection = event.getSenderConnection();
        if (senderConnection == null) return;

        Object playerObj = senderConnection.getPlayer().getPlayer();
        if (!(playerObj instanceof ServerPlayer serverPlayer)) return;

        if (!Config.groupInteraction && senderConnection.getGroup() != null) return;

        double dB = calculateAudioLevel(serverPlayer.getUUID(), event.getPacket().getOpusEncodedData());
        if (dB <= 0.0) return;

        processAudioInteraction(serverPlayer, dB, event.getPacket().isWhispering());
    }

    /**
     * オーディオレベルに基づいたインタラクション処理を実行する。
     * デバッグコマンドからも呼び出せるように公開。
     *
     * @param serverPlayer 発動元プレイヤー
     * @param dB           音声レベル
     * @param isWhispering 囁き声かどうか
     */
    public void processAudioInteraction(ServerPlayer serverPlayer, double dB, boolean isWhispering) {
        if (serverPlayer.isRemoved() || serverPlayer.hasDisconnected()) return;

        // ── 音声正規化: プレイヤー間のマイク感度差を自動補正 ──
        // ゲームプレイ修飾子（囁き・スニーク・ダッシュ）より前に適用
        double normalizedDb = dB;
        if (Config.voiceNormalization) {
            BaselineData baseline = voiceBaselineMap.get(serverPlayer.getUUID());
            if (baseline != null && baseline.sampleCount() >= BASELINE_MIN_SAMPLES) {
                double offset = Config.voiceNormalizationTarget - baseline.ema();
                // 極端な補正を防止
                offset = Math.max(-Config.voiceNormalizationMaxOffset, Math.min(Config.voiceNormalizationMaxOffset, offset));
                normalizedDb = Math.max(0.0, dB + offset);
            }
        }

        double actualDb = normalizedDb;

        // ── ウール防音: 周囲のウールブロックによる減衰 ──
        if (Config.woolDampening) {
            double woolOffset = woolDampeningMap.getOrDefault(serverPlayer.getUUID(), 0.0);
            if (woolOffset < 0.0) {
                actualDb = Math.max(0.0, actualDb + woolOffset);
            }
        }

        // ── 音量修飾子: 囁き・スニーク・ダッシュ ──
        if (isWhispering) {
            actualDb = applyVolumeModifier(actualDb, Config.whisperVolumeMultiplier);
            if (actualDb < 0.0) return; // 無音
        }
        if (serverPlayer.isCrouching()) {
            actualDb = applyVolumeModifier(actualDb, Config.sneakVolumeMultiplier);
            if (actualDb < 0.0) return;
        } else if (serverPlayer.isSprinting()) {
            actualDb = applyVolumeModifier(actualDb, Config.sprintVolumeMultiplier);
            if (actualDb < 0.0) return;
        }

        // ボイスメーターの更新
        VoiceMeterManager.updateMeter(serverPlayer, actualDb);

        UUID playerUUID = serverPlayer.getUUID();
        MinecraftServer server = serverPlayer.getServer();
        if (server != null) {
            for (Map.Entry<UUID, UUID> entry : activeMonitors.entrySet()) {
                if (entry.getValue().equals(playerUUID)) {
                    ServerPlayer admin = server.getPlayerList().getPlayer(entry.getKey());
                    if (admin != null) {
                        VoiceMeterManager.updateMonitorMeter(admin, serverPlayer.getScoreboardName(), actualDb);
                    }
                }
            }
        }

        long now = System.currentTimeMillis();

        cooldownManager.cleanupIfNeeded(now, Config.shockwaveCooldown);

        boolean sculkReady = !cooldownManager.isSculkInCooldown(playerUUID, now);
        boolean shockwaveReady = Config.shockwaveEnabled
                && !cooldownManager.isShockwaveInCooldown(playerUUID, now, Config.shockwaveCooldown);

        if (!sculkReady && !shockwaveReady) return;

        if (server == null) return;

        if (sculkReady && actualDb >= Config.minimumActivationThreshold) {
            cooldownManager.recordSculkActivation(playerUUID, now);
            int frequency = Config.voiceSculkFrequency;
            server.execute(() -> sculkEmitter.emit(serverPlayer, frequency));
        }

        if (shockwaveReady && actualDb >= Config.shockwaveThreshold) {
            // ネットワークスレッドで即座にCDを予約し、連射を防止する
            cooldownManager.recordShockwaveActivation(playerUUID, now);
            final double finalDb = actualDb;
            server.execute(() -> {
                if (serverPlayer.isRemoved() || serverPlayer.hasDisconnected()) {
                    // 発動できなかった場合はCDを取り消す
                    cooldownManager.clearShockwaveActivation(playerUUID);
                    return;
                }
                boolean inDeepDark = isInDeepDark(serverPlayer);
                if (!Config.shockwaveRequireDeepDark || inDeepDark) {
                    VoiceMeterManager.notifyShockwaveFired(serverPlayer);
                    shockwaveExecutor.execute(serverPlayer, finalDb);
                } else {
                    // ディープダーク外で発動条件を満たさなかった場合はCDを取り消す
                    cooldownManager.clearShockwaveActivation(playerUUID);
                }
            });
        }
    }

    private double calculateAudioLevel(UUID playerUUID, byte[] opusData) {
        if (opusData == null || opusData.length == 0 || voicechatApi == null) {
            return 0.0;
        }

        OpusDecoder decoder = decoders.computeIfAbsent(playerUUID, k -> voicechatApi.createDecoder());
        try {
            short[] pcmData = decoder.decode(opusData);
            if (pcmData == null || pcmData.length == 0) {
                return 0.0;
            }

            double rawDb = AudioUtils.calculateDbFromPcm(
                    pcmData, Config.microphoneBaseValue, Config.microphoneMultiplier,
                    Config.advancedNoiseFiltering);

            if (Double.isNaN(rawDb)) {
                rawDb = 0.0;
            }

            // 環境音などの低音量ノイズゲート（Configで指定した閾値未満は無音扱い）
            if (rawDb < Config.noiseGateThreshold) {
                rawDb = 0.0;
            }

            // 音声正規化用: 発話時の生データからベースラインを追跡
            // ノイズゲートを通過した発話時のみdBを学習する（無音時は学習しない）
            if (rawDb > 0.0 && Config.voiceNormalization) {
                final double baselineRawDb = rawDb;
                voiceBaselineMap.compute(playerUUID, (k, prev) -> {
                    if (prev == null || Double.isNaN(prev.ema())) return new BaselineData(baselineRawDb, 1.0);
                    // 適応的学習率: 初期は高速で外れ値の影響を素早く希釈し、安定後は中速で追従
                    double effectiveAlpha = Math.max(BASELINE_ALPHA,
                            ADAPTIVE_ALPHA_NUMERATOR / (prev.sampleCount() + 1.0));
                    double newEma = prev.ema() + effectiveAlpha * (baselineRawDb - prev.ema());
                    return new BaselineData(Double.isNaN(newEma) ? baselineRawDb : newEma, prev.sampleCount() + 1.0);
                });
            }

            final double finalRawDb = rawDb;

            // 指数移動平均 (EMA) を用いて、突発的なポップノイズや外れ値を平滑化する
            double emaDb = emaDbMap.compute(playerUUID, (k, prev) -> {
                if (prev == null || Double.isNaN(prev)) return finalRawDb;
                double next = (EMA_ALPHA * finalRawDb) + ((1.0 - EMA_ALPHA) * prev);
                return Double.isNaN(next) ? 0.0 : next;
            });

            return emaDb;
        } catch (Exception e) {
            LOGGER.warn("[SimpleVoiceChatInteraction] 音声レベルの計算に失敗しました", e);
            // デコーダーが壊れた可能性があるので再生成のために削除する
            decoders.remove(playerUUID);
            emaDbMap.remove(playerUUID);
            if (!decoder.isClosed()) {
                decoder.close();
            }
            return 0.0;
        }
    }
}
