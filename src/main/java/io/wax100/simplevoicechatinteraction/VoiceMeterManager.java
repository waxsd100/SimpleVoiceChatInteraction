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

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Simplevoicechatinteraction.MODID)
public class VoiceMeterManager {

    private static final Map<UUID, MeterData> playerMeters = new ConcurrentHashMap<>();

    public static class MeterData {
        public ServerBossEvent bossEvent;
        public boolean manuallyEnabled;
        public boolean inAncientCity;
        public double currentDb = 0.0;
        public int lastDisplayedDb = -999;
        public boolean needsUpdate = false;
    }

    public static void updateMeter(ServerPlayer player, double dB) {
        MeterData data = playerMeters.computeIfAbsent(player.getUUID(), uuid -> createAndRegisterMeter(player));
        data.currentDb = Math.max(data.currentDb, dB); // ピークホールド
        data.needsUpdate = true;
    }

    public static void toggleManual(ServerPlayer player) {
        MeterData data = playerMeters.computeIfAbsent(player.getUUID(), uuid -> createAndRegisterMeter(player));
        data.manuallyEnabled = !data.manuallyEnabled;
        data.needsUpdate = true;
        
        player.sendSystemMessage(Component.literal("§a[SVC] 音量メーターを " + (data.manuallyEnabled ? "§eオン" : "§cオフ") + " §aにしました。"));
        updateBossBar(player, data);
        data.needsUpdate = false;
    }

    private static MeterData createAndRegisterMeter(ServerPlayer player) {
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

        // 負荷軽減：40tick(2秒)に1回だけ、古代都市（ディープダークバイオーム）判定を行う
        if (serverPlayer.tickCount % 40 == 0) {
            boolean inDeepDark = serverPlayer.serverLevel().getBiome(serverPlayer.blockPosition()).is(Biomes.DEEP_DARK);
            if (inDeepDark) {
                if (data == null) {
                    data = createAndRegisterMeter(serverPlayer);
                    playerMeters.put(serverPlayer.getUUID(), data);
                }
                if (!data.inAncientCity) {
                    data.inAncientCity = true;
                    data.needsUpdate = true;
                }
            } else if (data != null && data.inAncientCity) {
                data.inAncientCity = false;
                data.needsUpdate = true;
            }
        }

        // メーターデータが存在しない（手動表示でもなく、古代都市でもなく、発声もしていない）場合は処理スキップ
        if (data == null) return;

        // 負荷軽減：毎tickではなく、2tickごとに減衰処理を行う
        if (serverPlayer.tickCount % 2 == 0 && data.currentDb > 0.0) {
            data.currentDb -= 2.0; // 減衰速度
            if (data.currentDb < 0.0) {
                data.currentDb = 0.0;
            }
            data.needsUpdate = true;
        }

        // 状態が変化した時だけパケットを送信してBossBarを更新する
        if (data.needsUpdate) {
            updateBossBar(serverPlayer, data);
            data.needsUpdate = false;
        }

        // メモリリーク防止：完全に不要になったデータのクリーンアップ（10秒に1回チェック）
        if (serverPlayer.tickCount % 200 == 0) {
            if (!data.manuallyEnabled && !data.inAncientCity && data.currentDb <= 0.0) {
                data.bossEvent.removePlayer(serverPlayer);
                playerMeters.remove(serverPlayer.getUUID());
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
            
            // 進捗の計算（0dB 〜 100dB を 0.0 〜 1.0 にマッピング）
            double minDb = 0.0;
            double maxDb = 100.0;
            double progress = (data.currentDb - minDb) / (maxDb - minDb);
            progress = Math.max(0.0, Math.min(1.0, progress));
            
            data.bossEvent.setProgress((float) progress);
            
            // 文字列生成とパケット送信の負荷を抑えるため、整数値(int)が変わった時だけテキストを更新
            int currentDbInt = (int) Math.round(data.currentDb);
            if (currentDbInt != data.lastDisplayedDb) {
                data.lastDisplayedDb = currentDbInt;
                
                if (data.currentDb >= Config.shockwaveThreshold) {
                    data.bossEvent.setColor(BossEvent.BossBarColor.RED);
                    data.bossEvent.setName(Component.literal("§cVoice Volume: " + currentDbInt + " dB"));
                } else if (data.currentDb >= Config.minimumActivationThreshold) {
                    data.bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
                    data.bossEvent.setName(Component.literal("§eVoice Volume: " + currentDbInt + " dB"));
                } else {
                    data.bossEvent.setColor(BossEvent.BossBarColor.BLUE);
                    data.bossEvent.setName(Component.literal("§bVoice Volume: " + currentDbInt + " dB"));
                }
            }
        } else {
            data.bossEvent.removePlayer(player);
        }
    }
}
