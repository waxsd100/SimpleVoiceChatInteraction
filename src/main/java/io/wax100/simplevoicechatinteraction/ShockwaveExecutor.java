package io.wax100.simplevoicechatinteraction;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.List;

/**
 * ショックウェーブ実行クラス。
 */
public class ShockwaveExecutor {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * ソニックショックウェーブ効果を実行する。
     * ダメージ、暗闇エフェクト、パーティクル、サウンドを含む。
     * サーバーメインスレッドで呼ぶこと。
     *
     * @param sourcePlayer 発動元プレイヤー
     */
    public void execute(ServerPlayer sourcePlayer) {
        if (sourcePlayer.isRemoved() || sourcePlayer.hasDisconnected()) return;

        ServerLevel level = sourcePlayer.serverLevel();
        Vec3 center = sourcePlayer.position();
        BlockPos centerBlock = sourcePlayer.blockPosition();

        double radius = Config.shockwaveRadius;
        float damage = (float) Config.shockwaveDamage;
        int darknessDuration = Config.shockwaveDarknessDuration;

        double radiusSq = radius * radius;
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(
                LivingEntity.class,
                sourcePlayer.getBoundingBox().inflate(radius),
                e -> e != sourcePlayer && sourcePlayer.distanceToSqr(e) <= radiusSq
        );

        for (LivingEntity entity : nearbyEntities) {
            float actualDamage = damage;
            if (entity instanceof Player) {
                actualDamage *= (float) Config.shockwavePlayerDamageMultiplier;
            } else if (entity instanceof Warden) {
                actualDamage *= (float) Config.shockwaveWardenDamageMultiplier;
            } else if (entity instanceof Monster) {
                actualDamage *= (float) Config.shockwaveMonsterDamageMultiplier;
            }

            if (actualDamage > 0.0F) {
                entity.hurt(level.damageSources().sonicBoom(sourcePlayer), actualDamage);
            }

            if (darknessDuration > 0 && entity instanceof ServerPlayer targetPlayer) {
                targetPlayer.addEffect(new MobEffectInstance(
                        MobEffects.DARKNESS, darknessDuration, 0, false, false, true
                ));
            }
        }

        spawnShockwaveEffects(level, center, centerBlock, radius);

        LOGGER.debug("[SimpleVoiceChatInteraction] ショックウェーブ発動: {} 位置={} 半径={} ヒット数={}",
                sourcePlayer.getName().getString(), centerBlock, radius, nearbyEntities.size());
    }

    private void spawnShockwaveEffects(ServerLevel level, Vec3 center, BlockPos centerBlock, double radius) {
        level.playSound(null, centerBlock, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 0.5F);
        level.playSound(null, centerBlock, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 0.4F);

        level.sendParticles(ParticleTypes.SONIC_BOOM,
                center.x, center.y + 1.0, center.z,
                1, 0.0, 0.0, 0.0, 0.0);

        level.sendParticles(ParticleTypes.EXPLOSION,
                center.x, center.y + 1.0, center.z,
                3, 0.5, 0.3, 0.5, 0.0);

        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                center.x, center.y + 0.2, center.z,
                15, radius * 0.3, 0.1, radius * 0.3, 0.02);

        int groundY = centerBlock.getY();
        for (double ringRadius = 2.0; ringRadius <= radius; ringRadius += 2.5) {
            int pointsOnRing = Math.max(8, (int) (ringRadius * 4));
            for (int i = 0; i < pointsOnRing; i++) {
                double angle = 2.0 * Math.PI * i / pointsOnRing;
                double x = center.x + Math.cos(angle) * ringRadius;
                double z = center.z + Math.sin(angle) * ringRadius;

                BlockPos groundPos = new BlockPos(
                        Mth.floor(x), groundY - 1, Mth.floor(z)
                );
                BlockState groundBlock = level.getBlockState(groundPos);

                if (!groundBlock.isAir()) {
                    level.sendParticles(
                            new BlockParticleOption(ParticleTypes.BLOCK, groundBlock),
                            x, groundY + 0.1, z,
                            2,
                            0.05,
                            0.3,
                            0.05,
                            0.05
                    );
                }
            }
        }

        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                center.x, center.y + 0.1, center.z,
                10, radius * 0.5, 0.05, radius * 0.5, 0.01);
    }
}
