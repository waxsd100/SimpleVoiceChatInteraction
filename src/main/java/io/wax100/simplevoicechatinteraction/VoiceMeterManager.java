package io.wax100.simplevoicechatinteraction;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Simplevoicechatinteraction.MODID)
public class VoiceMeterManager {

    /** BossBar進捗計算用の最大dB値 */
    private static final double MAX_DB = 200.0;

    private static final Map<UUID, MeterData> playerMeters = new ConcurrentHashMap<>();

    public static class MeterData {
        public ServerBossEvent bossEvent;
        public boolean manuallyEnabled;
        public boolean inAncientCity;
        public volatile double currentDb = 0.0;
        public volatile int lastDisplayedDb = -999;
        public volatile int lastDisplayedCooldownDeci = -999;
        public volatile int shockwaveVisualTimer = 0;
        public volatile boolean needsUpdate = false;
        public volatile String monitorTargetName = null;
    }

    public static void updateMeter(ServerPlayer player, double dB) {
        MeterData data = playerMeters.computeIfAbsent(player.getUUID(), uuid -> createAndRegisterMeter(player));
        // もし他プレイヤーをモニター中の場合は、自分自身の音声でメーターを上書きしない
        if (data.monitorTargetName != null) return;
        
        data.currentDb = Math.max(data.currentDb, dB); // ピークホールド
        data.needsUpdate = true;
    }

    public static void updateMonitorMeter(ServerPlayer admin, String targetName, double dB) {
        MeterData data = playerMeters.computeIfAbsent(admin.getUUID(), uuid -> createAndRegisterMeter(admin));
        data.currentDb = Math.max(data.currentDb, dB);
        data.monitorTargetName = targetName;
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

    public static void notifyShockwaveFired(ServerPlayer player) {
        MeterData data = playerMeters.get(player.getUUID());
        if (data != null) {
            data.shockwaveVisualTimer = 20; // 1秒間紫色をキープ (20 ticks)
            data.needsUpdate = true;
        }
    }

    public static void setMonitorMode(ServerPlayer admin, String targetName) {
        MeterData data = playerMeters.computeIfAbsent(admin.getUUID(), uuid -> createAndRegisterMeter(admin));
        data.monitorTargetName = targetName;
        data.manuallyEnabled = true; // モニター時は強制的に表示
        data.needsUpdate = true;
        updateBossBar(admin, data);
    }

    public static void clearMonitorMode(ServerPlayer admin) {
        MeterData data = playerMeters.get(admin.getUUID());
        if (data != null) {
            data.monitorTargetName = null;
            data.manuallyEnabled = false; // モニター解除時にメーターも非表示にする
            data.lastDisplayedDb = -999; // 次回必ず更新させる
            data.lastDisplayedCooldownDeci = -999;
            data.needsUpdate = true;
            updateBossBar(admin, data);
        }
    }

    private static MeterData createAndRegisterMeter(ServerPlayer player) {
        MeterData data = new MeterData();
        data.bossEvent = new ServerBossEvent(
                Component.literal("Voice Volume"),
                BossEvent.BossBarColor.BLUE,
                BossEvent.BossBarOverlay.NOTCHED_20
        );
        return data;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer serverPlayer)) return;

        MeterData data = playerMeters.get(serverPlayer.getUUID());

        // 負荷軽減：40tick(2秒)に1回だけ、古代都市（ディープダークバイオーム）判定を行う
        if (serverPlayer.tickCount % 40 == 0) {
            boolean inDeepDark = VoiceChatSculkPlugin.isInDeepDark(serverPlayer);
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

        // クールダウン中は表示更新のため強制的にneedsUpdateをtrueにする
        if (serverPlayer.tickCount % 2 == 0) {
            if (data.shockwaveVisualTimer > 0) {
                data.shockwaveVisualTimer -= 2;
                if (data.shockwaveVisualTimer <= 0) {
                    data.shockwaveVisualTimer = 0;
                    data.needsUpdate = true;
                }
            }
            if (VoiceChatSculkPlugin.instance != null) {
                UUID targetUUID = serverPlayer.getUUID();
                if (data.monitorTargetName != null && serverPlayer.getServer() != null) {
                    ServerPlayer targetPlayer = serverPlayer.getServer().getPlayerList().getPlayerByName(data.monitorTargetName);
                    if (targetPlayer != null) {
                        targetUUID = targetPlayer.getUUID();
                    }
                }
                long remainingMs = VoiceChatSculkPlugin.instance.getCooldownManager()
                        .getShockwaveCooldownRemaining(targetUUID, System.currentTimeMillis(), Config.shockwaveCooldown);
                if (remainingMs > 0) {
                    data.needsUpdate = true;
                }
            }
        }

        // 状態が変化した時だけパケットを送信してBossBarを更新する
        if (data.needsUpdate) {
            updateBossBar(serverPlayer, data);
            data.needsUpdate = false;
        }

        // メモリリーク防止：完全に不要になったデータのクリーンアップ（10秒に1回チェック）
        if (serverPlayer.tickCount % 200 == 0) {
            if (!data.manuallyEnabled && !data.inAncientCity && data.currentDb <= 0.0 && data.monitorTargetName == null) {
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
            
            // 進捗の計算（0dB 〜 200dB を 0.0 〜 1.0 にマッピング）
            double progress = Math.max(0.0, Math.min(1.0, data.currentDb / MAX_DB));
            
            data.bossEvent.setProgress((float) progress);
            
            // 文字列生成とパケット送信の負荷を抑えるため、整数値やクールダウンが変わった時だけテキストを更新
            int currentDbInt = (int) Math.round(data.currentDb);

            long remainingMs = 0;
            if (VoiceChatSculkPlugin.instance != null) {
                UUID targetUUID = player.getUUID();
                if (data.monitorTargetName != null && player.getServer() != null) {
                    ServerPlayer targetPlayer = player.getServer().getPlayerList().getPlayerByName(data.monitorTargetName);
                    if (targetPlayer != null) {
                        targetUUID = targetPlayer.getUUID();
                    }
                }
                remainingMs = VoiceChatSculkPlugin.instance.getCooldownManager()
                        .getShockwaveCooldownRemaining(targetUUID, System.currentTimeMillis(), Config.shockwaveCooldown);
            }
            int cooldownDeci = (int) (remainingMs / 100);

            if (currentDbInt != data.lastDisplayedDb || cooldownDeci != data.lastDisplayedCooldownDeci) {
                data.lastDisplayedDb = currentDbInt;
                data.lastDisplayedCooldownDeci = cooldownDeci;
                
                String prefix = data.monitorTargetName != null ? "§a[" + data.monitorTargetName + "] " : "";
                String cooldownText = cooldownDeci > 0 ? String.format(" §8[CD: %.1fs]", cooldownDeci / 10.0) : "";
                
                if (data.shockwaveVisualTimer > 0) {
                    data.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
                    data.bossEvent.setName(Component.literal(prefix + "§5Voice Volume: " + currentDbInt + " dB (SHOCKWAVE)" + cooldownText));
                } else if (data.currentDb >= Config.shockwaveThreshold) {
                    data.bossEvent.setColor(BossEvent.BossBarColor.RED);
                    data.bossEvent.setName(Component.literal(prefix + "§cVoice Volume: " + currentDbInt + " dB" + cooldownText));
                } else if (data.currentDb >= (Config.minimumActivationThreshold + Config.shockwaveThreshold) / 2.0) {
                    data.bossEvent.setColor(BossEvent.BossBarColor.RED);
                    data.bossEvent.setName(Component.literal(prefix + "§cVoice Volume: " + currentDbInt + " dB" + cooldownText));
                } else if (data.currentDb >= Config.minimumActivationThreshold) {
                    data.bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
                    data.bossEvent.setName(Component.literal(prefix + "§eVoice Volume: " + currentDbInt + " dB" + cooldownText));
                } else {
                    data.bossEvent.setColor(BossEvent.BossBarColor.BLUE);
                    data.bossEvent.setName(Component.literal(prefix + "§bVoice Volume: " + currentDbInt + " dB" + cooldownText));
                }
            }
        } else {
            data.bossEvent.removePlayer(player);
        }
    }
}
