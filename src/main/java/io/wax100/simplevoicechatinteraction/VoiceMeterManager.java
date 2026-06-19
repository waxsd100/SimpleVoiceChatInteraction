package io.wax100.simplevoicechatinteraction;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Simplevoicechatinteraction.MODID)
public class VoiceMeterManager {

    private static final Map<UUID, MeterData> playerMeters = new HashMap<>();

    public static class MeterData {
        public ServerBossEvent bossEvent;
        public boolean manuallyEnabled;
        public boolean inAncientCity;
        public double currentDb = -100.0;
        public long lastUpdatedTime;
    }

    public static void updateMeter(ServerPlayer player, double dB) {
        MeterData data = playerMeters.computeIfAbsent(player.getUUID(), uuid -> createMeter(player));
        data.currentDb = Math.max(data.currentDb, dB); // ピークホールド
        data.lastUpdatedTime = System.currentTimeMillis();
        
        updateBossBar(player, data);
    }

    public static void toggleManual(ServerPlayer player) {
        MeterData data = playerMeters.computeIfAbsent(player.getUUID(), uuid -> createMeter(player));
        data.manuallyEnabled = !data.manuallyEnabled;
        updateBossBar(player, data);
        
        player.sendSystemMessage(Component.literal("§a[SVC] 音量メーターを " + (data.manuallyEnabled ? "§eオン" : "§cオフ") + " §aにしました。"));
    }

    private static MeterData createMeter(ServerPlayer player) {
        MeterData data = new MeterData();
        data.bossEvent = new ServerBossEvent(
                Component.literal("Voice Volume"),
                BossEvent.BossBarColor.BLUE,
                BossEvent.BossBarOverlay.PROGRESS
        );
        return data;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer serverPlayer)) return;

        MeterData data = playerMeters.get(serverPlayer.getUUID());
        if (data == null) return;

        // 1秒に1回、古代都市（ディープダークバイオーム）判定を更新
        if (serverPlayer.tickCount % 20 == 0) {
            boolean inDeepDark = serverPlayer.serverLevel().getBiome(serverPlayer.blockPosition()).is(Biomes.DEEP_DARK);
            if (data.inAncientCity != inDeepDark) {
                data.inAncientCity = inDeepDark;
                updateBossBar(serverPlayer, data);
            }
        }

        // dBの減衰（なめらかに下がるアニメーション）
        long now = System.currentTimeMillis();
        if (now - data.lastUpdatedTime > 50) { // 50msごとにチェック
            if (data.currentDb > -100.0) {
                data.currentDb -= 2.0; // 減衰速度
                if (data.currentDb < -100.0) {
                    data.currentDb = -100.0;
                }
                updateBossBar(serverPlayer, data);
                data.lastUpdatedTime = now;
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MeterData data = playerMeters.remove(player.getUUID());
            if (data != null) {
                data.bossEvent.removeAllPlayers();
            }
        }
    }

    private static void updateBossBar(ServerPlayer player, MeterData data) {
        boolean shouldShow = data.manuallyEnabled || data.inAncientCity;
        
        if (shouldShow) {
            data.bossEvent.addPlayer(player);
            
            // 進捗の計算（-60dB 〜 0dB を 0.0 〜 1.0 にマッピング）
            double minDb = -60.0;
            double maxDb = 0.0;
            double progress = (data.currentDb - minDb) / (maxDb - minDb);
            progress = Math.max(0.0, Math.min(1.0, progress));
            
            data.bossEvent.setProgress((float) progress);
            
            // 色の変更（安全:青、スカルク反応:黄、ショックウェーブ:赤）
            if (data.currentDb >= Config.shockwaveThreshold) {
                data.bossEvent.setColor(BossEvent.BossBarColor.RED);
                data.bossEvent.setName(Component.literal("§cVoice Volume: " + String.format("%.1f", data.currentDb) + " dB"));
            } else if (data.currentDb >= Config.minimumActivationThreshold) {
                data.bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
                data.bossEvent.setName(Component.literal("§eVoice Volume: " + String.format("%.1f", data.currentDb) + " dB"));
            } else {
                data.bossEvent.setColor(BossEvent.BossBarColor.BLUE);
                data.bossEvent.setName(Component.literal("§bVoice Volume: " + String.format("%.1f", data.currentDb) + " dB"));
            }
        } else {
            data.bossEvent.removePlayer(player);
        }
    }
}
