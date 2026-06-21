package io.wax100.simplevoicechatinteraction;

import com.mojang.logging.LogUtils;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.common.MinecraftForge;
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
     * 正規化が有効になるまでの最小サンプル数。
     * 約100パケット（約2秒の発話）でキャリブレーション完了。
     */
    private static final int BASELINE_MIN_SAMPLES = 100;

    /**
     * デバッグコマンド等からアクセスするためのシングルトンインスタンス
     */
    public static volatile VoiceChatSculkPlugin instance;

    private final CooldownManager cooldownManager = new CooldownManager();
    private final SculkVibrationEmitter sculkEmitter = new SculkVibrationEmitter();
    private final ShockwaveExecutor shockwaveExecutor = new ShockwaveExecutor();
    private final ConcurrentHashMap<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Double> emaDbMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> activeMonitors = new ConcurrentHashMap<>();
    /**
     * 音声正規化用ベースラインデータ。
     * double[0] = ベースラインEMA値、double[1] = サンプル数
     */
    private final ConcurrentHashMap<UUID, double[]> voiceBaselineMap = new ConcurrentHashMap<>();

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
        activeMonitors.remove(uuid);
        // このプレイヤーを監視しているモニターも解除
        activeMonitors.values().removeIf(targetUUID -> targetUUID.equals(uuid));
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
            double[] baseline = voiceBaselineMap.get(serverPlayer.getUUID());
            if (baseline != null && baseline[1] >= BASELINE_MIN_SAMPLES) {
                double offset = Config.voiceNormalizationTarget - baseline[0];
                // 極端な補正を防止
                offset = Math.max(-Config.voiceNormalizationMaxOffset, Math.min(Config.voiceNormalizationMaxOffset, offset));
                normalizedDb = Math.max(0.0, dB + offset);
            }
        }

        double actualDb = normalizedDb;
        if (isWhispering) {
            double multiplier = Config.whisperVolumeMultiplier;
            if (multiplier <= 0.0) {
                return; // 無音
            }
            actualDb += 20.0 * Math.log10(multiplier);
        }

        if (serverPlayer.isCrouching()) {
            double multiplier = Config.sneakVolumeMultiplier;
            if (multiplier <= 0.0) {
                return; // 無音
            }
            actualDb += 20.0 * Math.log10(multiplier);
        } else if (serverPlayer.isSprinting()) {
            double multiplier = Config.sprintVolumeMultiplier;
            if (multiplier <= 0.0) {
                return; // 無音
            }
            actualDb += 20.0 * Math.log10(multiplier);
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

            double rawDb = AudioUtils.calculateDbFromPcm(pcmData, Config.microphoneBaseValue, Config.microphoneMultiplier);

            // 環境音などの低音量ノイズゲート（Configで指定した閾値未満は無音扱い）
            if (rawDb < Config.noiseGateThreshold) {
                rawDb = 0.0;
            }

            // 音声正規化用: 発話時の生データからベースラインを追跡
            // ノイズゲートを通過した発話時のみdBを学習する（無音時は学習しない）
            if (rawDb > 0.0 && Config.voiceNormalization) {
                final double baselineRawDb = rawDb;
                voiceBaselineMap.compute(playerUUID, (k, prev) -> {
                    if (prev == null) return new double[]{baselineRawDb, 1.0};
                    // 適応的学習率: 初期は高速で外れ値の影響を素早く希釈し、安定後は中速で追従
                    // sampleCount=1→alpha=1.0, =10→0.2, =50→0.04, =100+→0.02(下限)
                    double effectiveAlpha = Math.max(BASELINE_ALPHA, 2.0 / (prev[1] + 1.0));
                    prev[0] += effectiveAlpha * (baselineRawDb - prev[0]);
                    prev[1] += 1.0;
                    return prev;
                });
            }

            final double finalRawDb = rawDb;

            // 指数移動平均 (EMA) を用いて、突発的なポップノイズや外れ値を平滑化する
            // alpha = 0.35 (数パケットかけて滑らかに音量が変化し、1パケットだけの突発的なスパイクを除去)
            double alpha = 0.35;
            double emaDb = emaDbMap.compute(playerUUID, (k, prev) -> {
                if (prev == null) return finalRawDb;
                return (alpha * finalRawDb) + ((1.0 - alpha) * prev);
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
