package io.wax100.simplevoicechatinteraction;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;

/**
 * スカルク振動発生クラス。
 */
public class SculkVibrationEmitter {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * スカルクセンサーの周波数（1〜15）をバニラの {@link GameEvent} にマッピングする配列。
     * 各 GameEvent はスカルクセンサーで検知された際に対応する周波数を出力する。
     * Minecraft 1.20.1 の振動周波数テーブルに基づく。
     */
    private static final GameEvent[] FREQUENCY_EVENTS = {
            GameEvent.STEP,              // 周波数 1:  step, swim, flap
            GameEvent.HIT_GROUND,        // 周波数 2:  projectile_land, hit_ground, splash
            GameEvent.PROJECTILE_SHOOT,  // 周波数 3:  item_interact_finish, projectile_shoot
            GameEvent.ENTITY_ROAR,       // 周波数 4:  entity_roar, entity_shake
            GameEvent.EQUIP,             // 周波数 5:  entity_dismount, equip
            GameEvent.ENTITY_INTERACT,   // 周波数 6:  entity_mount, entity_interact, shear
            GameEvent.ENTITY_DAMAGE,     // 周波数 7:  entity_damage
            GameEvent.DRINK,             // 周波数 8:  drink, eat
            GameEvent.CONTAINER_CLOSE,   // 周波数 9:  container_close, block_close
            GameEvent.CONTAINER_OPEN,    // 周波数 10: container_open, block_open
            GameEvent.BLOCK_PLACE,       // 周波数 11: block_place, fluid_place
            GameEvent.BLOCK_DESTROY,     // 周波数 12: block_destroy, fluid_pickup
            GameEvent.ENTITY_DIE,        // 周波数 13: entity_die, lightning_strike
            GameEvent.TELEPORT,          // 周波数 14: teleport, explode
            GameEvent.SHRIEK,            // 周波数 15: shriek, resonate_*
    };

    /**
     * プレイヤー位置に GameEvent を発生させてスカルクセンサーを反応させる。
     * サーバーメインスレッドで呼ぶこと。
     *
     * @param player    発動元プレイヤー
     * @param frequency スカルクセンサーの周波数
     */
    public void emit(ServerPlayer player, int frequency) {
        if (player.isRemoved() || player.hasDisconnected()) return;

        ServerLevel level = player.serverLevel();
        GameEvent gameEvent = getGameEventForFrequency(frequency);
        level.gameEvent(player, gameEvent, player.blockPosition());

        LOGGER.debug("[SimpleVoiceChatInteraction] スカルク振動 (周波数={}) {} の位置 {}",
                frequency, player.getName().getString(), player.blockPosition());
    }

    /**
     * 指定されたスカルクセンサー周波数に対応するバニラの {@link GameEvent} を返す。
     *
     * @param frequency 周波数（1〜15）
     * @return 対応する GameEvent。範囲外の場合は {@link GameEvent#STEP} を返す
     */
    public static GameEvent getGameEventForFrequency(int frequency) {
        if (frequency < 1 || frequency > 15) {
            return GameEvent.STEP;
        }
        return FREQUENCY_EVENTS[frequency - 1];
    }
}
