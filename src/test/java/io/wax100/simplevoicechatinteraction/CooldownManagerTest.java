package io.wax100.simplevoicechatinteraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CooldownManager} のユニットテスト。
 */
@DisplayName("CooldownManager テスト")
class CooldownManagerTest {

    @Nested
    @DisplayName("クールダウン判定 (isInCooldown)")
    class CooldownTest {

        private final CooldownManager cooldownManager = new CooldownManager();
        private final UUID playerUUID = UUID.randomUUID();

        @Test
        @DisplayName("初回使用時はクールダウン中ではない")
        void 初回使用時はクールダウン中ではない() {
            long now = System.currentTimeMillis();
            assertFalse(cooldownManager.isSculkInCooldown(playerUUID, now),
                    "エントリがない場合はクールダウン中ではないはず");
        }

        @Test
        @DisplayName("使用直後はクールダウン中になる")
        void 使用直後はクールダウン中になる() {
            long now = System.currentTimeMillis();
            cooldownManager.recordSculkActivation(playerUUID, now - 1000L);

            assertTrue(cooldownManager.isSculkInCooldown(playerUUID, now),
                    "クールダウン期間内はクールダウン中であるべき");
        }

        @Test
        @DisplayName("クールダウン期間経過後は使用可能")
        void クールダウン期間経過後は使用可能() {
            long now = System.currentTimeMillis();
            cooldownManager.recordSculkActivation(playerUUID, now - 5000L);

            assertFalse(cooldownManager.isSculkInCooldown(playerUUID, now),
                    "クールダウン期間経過後はクールダウン中ではないはず");
        }

        @Test
        @DisplayName("ちょうどクールダウン時間経過時の場合、クールダウン中ではない（境界値）")
        void ちょうどクールダウン時間経過時の場合クールダウン中ではない() {
            long now = System.currentTimeMillis();
            cooldownManager.recordSculkActivation(playerUUID, now - 3000L); // 3000L is SCULK_COOLDOWN_MS

            assertFalse(cooldownManager.isSculkInCooldown(playerUUID, now),
                    "ちょうどクールダウン時間が経過した場合はクールダウン中ではないはず");
        }

        @Test
        @DisplayName("異なるプレイヤーのクールダウンは互いに独立している")
        void 異なるプレイヤーのクールダウンは独立している() {
            long now = System.currentTimeMillis();
            UUID otherPlayer = UUID.randomUUID();

            cooldownManager.recordSculkActivation(otherPlayer, now);

            assertFalse(cooldownManager.isSculkInCooldown(playerUUID, now),
                    "他のプレイヤーのクールダウンが存在しても影響しないはず");
        }
    }
}
