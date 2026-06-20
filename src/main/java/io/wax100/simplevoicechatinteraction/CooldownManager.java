package io.wax100.simplevoicechatinteraction;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * クールダウン管理クラス。
 * <p>
 * プレイヤーごとのクールダウン状態を管理し、定期的なクリーンアップを行う。
 */
public class CooldownManager {

    private static final long SCULK_COOLDOWN_MS = 3000L;
    private static final long COOLDOWN_CLEANUP_INTERVAL_MS = 60_000L;
    private final ConcurrentHashMap<UUID, Long> sculkCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> shockwaveCooldowns = new ConcurrentHashMap<>();
    private volatile long lastCleanupTime = 0L;

    /**
     * スカルク振動のクールダウン中かどうかを判定する。
     *
     * @param playerUUID プレイヤーのUUID
     * @param now        現在時刻（ミリ秒）
     * @return クールダウン中なら true
     */
    public boolean isSculkInCooldown(UUID playerUUID, long now) {
        return isInCooldown(sculkCooldowns, playerUUID, now, SCULK_COOLDOWN_MS);
    }

    /**
     * ショックウェーブのクールダウン中かどうかを判定する。
     *
     * @param playerUUID プレイヤーのUUID
     * @param now        現在時刻（ミリ秒）
     * @param cooldownMs 設定されたクールダウン時間（ミリ秒）
     * @return クールダウン中なら true
     */
    public boolean isShockwaveInCooldown(UUID playerUUID, long now, long cooldownMs) {
        return isInCooldown(shockwaveCooldowns, playerUUID, now, cooldownMs);
    }

    /**
     * スカルク振動の発動を記録する。
     *
     * @param playerUUID プレイヤーのUUID
     * @param now        現在時刻（ミリ秒）
     */
    public void recordSculkActivation(UUID playerUUID, long now) {
        sculkCooldowns.put(playerUUID, now);
    }

    /**
     * ショックウェーブの発動を記録する。
     *
     * @param playerUUID プレイヤーのUUID
     * @param now        現在時刻（ミリ秒）
     */
    public void recordShockwaveActivation(UUID playerUUID, long now) {
        shockwaveCooldowns.put(playerUUID, now);
    }

    /**
     * ショックウェーブのクールダウン予約を取り消す。
     * ネットワークスレッドでCDを予約した後、メインスレッドで発動条件を満たさなかった場合に呼ぶ。
     *
     * @param playerUUID プレイヤーのUUID
     */
    public void clearShockwaveActivation(UUID playerUUID) {
        shockwaveCooldowns.remove(playerUUID);
    }

    /**
     * ショックウェーブの残りクールダウン時間を取得する。
     *
     * @param playerUUID プレイヤーのUUID
     * @param now        現在時刻（ミリ秒）
     * @param cooldownMs 設定されたクールダウン時間（ミリ秒）
     * @return 残り時間（ミリ秒）。クールダウン中でなければ0
     */
    public long getShockwaveCooldownRemaining(UUID playerUUID, long now, long cooldownMs) {
        Long lastTime = shockwaveCooldowns.get(playerUUID);
        if (lastTime == null) return 0;
        long elapsed = now - lastTime;
        if (elapsed >= cooldownMs) return 0;
        return cooldownMs - elapsed;
    }

    /**
     * 古いクールダウンエントリを定期的に削除してメモリリークを防ぐ。
     * ログアウト済みプレイヤーのエントリが無限に蓄積するのを防止する。
     *
     * @param now               現在時刻（ミリ秒）
     * @param shockwaveCooldown 設定されたショックウェーブのクールダウン時間
     */
    public void cleanupIfNeeded(long now, long shockwaveCooldown) {
        // NOTE: この判定と代入はアトミックではないが、クリーンアップは冪等なので
        // 複数スレッドが同時に実行しても実害はない（重複クリーンアップが走るだけ）
        if ((now - lastCleanupTime) < COOLDOWN_CLEANUP_INTERVAL_MS) return;
        lastCleanupTime = now;

        long maxCooldown = Math.max(SCULK_COOLDOWN_MS, shockwaveCooldown);
        sculkCooldowns.entrySet().removeIf(entry -> (now - entry.getValue()) > maxCooldown);
        shockwaveCooldowns.entrySet().removeIf(entry -> (now - entry.getValue()) > maxCooldown);
    }

    /**
     * 汎用クールダウン判定。
     */
    private boolean isInCooldown(ConcurrentHashMap<UUID, Long> cooldownMap, UUID playerUUID,
                                 long now, long cooldownMs) {
        Long lastTime = cooldownMap.get(playerUUID);
        return lastTime != null && (now - lastTime) < cooldownMs;
    }
}
