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
    private final java.util.concurrent.ConcurrentHashMap<UUID, OpusDecoder> decoders = new java.util.concurrent.ConcurrentHashMap<>();

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
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        OpusDecoder decoder = decoders.remove(event.getEntity().getUUID());
        if (decoder != null && !decoder.isClosed()) {
            decoder.close();
        }
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

        // ボイスメーターの更新
        VoiceMeterManager.updateMeter(serverPlayer, actualDb);

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
            boolean inDeepDark = serverPlayer.serverLevel().getBiome(serverPlayer.blockPosition()).is(net.minecraft.world.level.biome.Biomes.DEEP_DARK)
                    || "deeperdarker:otherside".equals(serverPlayer.serverLevel().dimension().location().toString());
            if (inDeepDark) {
                cooldownManager.recordShockwaveActivation(playerUUID, now);
                final double finalDb = actualDb;
                server.execute(() -> shockwaveExecutor.execute(serverPlayer, finalDb));
            }
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

            return AudioUtils.calculateDbFromPcm(pcmData);
        } catch (Exception e) {
            LOGGER.warn("[SimpleVoiceChatInteraction] 音声レベルの計算に失敗しました", e);
            // デコーダーが壊れた可能性があるので再生成のために削除する
            decoders.remove(playerUUID);
            if (!decoder.isClosed()) {
                decoder.close();
            }
            return 0.0;
        }
    }
}
