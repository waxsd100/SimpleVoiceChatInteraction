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
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Simple Voice Chat API プラグイン。
 * プレイヤーの発話を検知し、スカルク振動やソニックショックウェーブを発生させる。
 * 
 * オーケストレーターとして機能し、実際の処理は各コンポーネントに委譲する。
 */
@ForgeVoicechatPlugin
public class VoiceChatSculkPlugin implements VoicechatPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** デバッグコマンド等からアクセスするためのシングルトンインスタンス */
    public static VoiceChatSculkPlugin instance;

    private final CooldownManager cooldownManager = new CooldownManager();
    private final SculkVibrationEmitter sculkEmitter = new SculkVibrationEmitter();
    private final ShockwaveExecutor shockwaveExecutor = new ShockwaveExecutor();

    private VoicechatApi voicechatApi;

    @Override
    public String getPluginId() {
        return Simplevoicechatinteraction.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.voicechatApi = api;
        instance = this;
        LOGGER.info("[SimpleVoiceChatInteraction] VoiceChatプラグイン初期化完了");
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

        double dB = calculateAudioLevel(event.getPacket().getOpusEncodedData());
        if (Double.isInfinite(dB) || Double.isNaN(dB)) return;

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
        
        double actualDb = dB;
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

        UUID playerUUID = serverPlayer.getUUID();
        long now = System.currentTimeMillis();

        cooldownManager.cleanupIfNeeded(now, Config.shockwaveCooldown);

        boolean sculkReady = !cooldownManager.isSculkInCooldown(playerUUID, now);
        boolean shockwaveReady = Config.shockwaveEnabled
                && !cooldownManager.isShockwaveInCooldown(playerUUID, now, Config.shockwaveCooldown);

        if (!sculkReady && !shockwaveReady) return;

        MinecraftServer server = serverPlayer.getServer();
        if (server == null) return;

        if (sculkReady && actualDb >= Config.minimumActivationThreshold) {
            cooldownManager.recordSculkActivation(playerUUID, now);
            int frequency = Config.voiceSculkFrequency;
            server.execute(() -> sculkEmitter.emit(serverPlayer, frequency));
        }

        if (shockwaveReady && actualDb >= Config.shockwaveThreshold) {
            cooldownManager.recordShockwaveActivation(playerUUID, now);
            final double finalDb = actualDb;
            server.execute(() -> shockwaveExecutor.execute(serverPlayer, finalDb));
        }
    }

    private double calculateAudioLevel(byte[] opusData) {
        if (opusData == null || opusData.length == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (voicechatApi == null) {
            return Double.NEGATIVE_INFINITY;
        }

        OpusDecoder decoder = null;
        try {
            decoder = voicechatApi.createDecoder();
            short[] pcmData = decoder.decode(opusData);
            if (pcmData == null || pcmData.length == 0) {
                return Double.NEGATIVE_INFINITY;
            }

            return AudioUtils.calculateDbFromPcm(pcmData);
        } catch (Exception e) {
            LOGGER.warn("[SimpleVoiceChatInteraction] 音声レベルの計算に失敗しました", e);
            return Double.NEGATIVE_INFINITY;
        } finally {
            if (decoder != null && !decoder.isClosed()) {
                decoder.close();
            }
        }
    }
}
