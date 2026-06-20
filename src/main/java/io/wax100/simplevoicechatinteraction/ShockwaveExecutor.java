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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ショックウェーブ実行クラス。
 * <p>
 * 2段階の攻撃を行う:
 * <ol>
 *   <li>周囲全方位への衝撃波（従来の円形AoE）</li>
 *   <li>プレイヤーの視線方向へのソニックビーム（ウォーデン風の指向性攻撃）</li>
 * </ol>
 */
public class ShockwaveExecutor {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** ソニックビームの当たり判定の幅（ブロック単位） */
    private static final double BEAM_WIDTH = 2.0;
    /** 周囲AoEの縮小倍率（配置半径に対する比率） */
    private static final double RADIAL_RADIUS_SCALE = 0.5;
    /** ビーム射程の延長倍率（配置半径に対する比率） */
    private static final double BEAM_LENGTH_SCALE = 3.0;
    /** ビームダメージの倍率（周囲AoEダメージに対する比率） */
    private static final float BEAM_DAMAGE_MULTIPLIER = 1.5F;
    /** ビームによるノックバックの水平方向の強度（閾値ギリギリでバニラウォーデン相当） */
    private static final double BEAM_KNOCKBACK_HORIZONTAL = 2.3;
    /** ビームによるノックバックの垂直方向の強度 */
    private static final double BEAM_KNOCKBACK_VERTICAL = 0.4;

    /**
     * 発動元プレイヤーを中心にソニックショックウェーブを発生させる。
     * サーバーメインスレッドで呼ぶこと。
     *
     * @param sourcePlayer 発動元プレイヤー
     * @param dB           音声レベル
     */
    public void execute(ServerPlayer sourcePlayer, double dB) {
        if (sourcePlayer.isRemoved() || sourcePlayer.hasDisconnected()) return;

        ServerLevel level = sourcePlayer.serverLevel();
        
        double threshold = Config.shockwaveThreshold;
        // dBが閾値から100.0（最大）までの間で0.0〜1.0の割合（progress）を計算
        double progress = 0.0;
        if (threshold < 100.0) {
            progress = (dB - threshold) / (100.0 - threshold);
            progress = Mth.clamp(progress, 0.0, 1.0);
        }

        double radiusMultiplier = 1.0 + (Config.shockwaveMaxRadiusMultiplier - 1.0) * progress;
        double damageMultiplier = 1.0 + (Config.shockwaveMaxDamageMultiplier - 1.0) * progress;

        double radius = Config.shockwaveRadius * radiusMultiplier;
        float damage = (float) (Config.shockwaveDamage * damageMultiplier);
        int darknessDuration = Config.shockwaveDarknessDuration;

        Vec3 center = sourcePlayer.position();
        BlockPos centerBlock = sourcePlayer.blockPosition();

        // ── フェーズ1: 周囲全方位への衝撃波（小規模の円形AoE） ──
        Set<Integer> hitEntityIds = new HashSet<>();

        double radialRadius = radius * RADIAL_RADIUS_SCALE;
        double radialRadiusSq = radialRadius * radialRadius;
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(
                LivingEntity.class,
                sourcePlayer.getBoundingBox().inflate(radialRadius),
                e -> e != sourcePlayer && sourcePlayer.distanceToSqr(e) <= radialRadiusSq
        );

        for (LivingEntity entity : nearbyEntities) {
            applyDamageAndEffects(level, sourcePlayer, entity, damage, darknessDuration);
            hitEntityIds.add(entity.getId());
        }

        spawnRadialEffects(level, center, centerBlock, radialRadius);

        // ── フェーズ2: 前方へのソニックビーム（ウォーデン風・長射程） ──
        double beamLength = radius * BEAM_LENGTH_SCALE;
        Vec3 lookDir = sourcePlayer.getLookAngle();
        Vec3 eyePos = sourcePlayer.getEyePosition();

        // ビームの到達範囲をAABBで大まかにフィルタ
        Vec3 beamEnd = eyePos.add(lookDir.scale(beamLength));
        AABB beamBounds = new AABB(eyePos, beamEnd).inflate(BEAM_WIDTH);

        List<LivingEntity> beamCandidates = level.getEntitiesOfClass(
                LivingEntity.class,
                beamBounds,
                e -> e != sourcePlayer && !hitEntityIds.contains(e.getId())
        );

        int beamHitCount = 0;
        for (LivingEntity entity : beamCandidates) {
            // エンティティがビーム（円柱）の中にいるか判定
            if (isInBeamCylinder(eyePos, lookDir, beamLength, entity.position().add(0, entity.getBbHeight() / 2.0, 0))) {
                float beamDamage = damage * BEAM_DAMAGE_MULTIPLIER;
                applyDamageAndEffects(level, sourcePlayer, entity, beamDamage, darknessDuration);
                // ノックバックの強さをダメージに比例させる（基本ダメージで正規化）
                double knockbackScale = beamDamage / Math.max(1.0, (float) Config.shockwaveDamage);
                Vec3 knockback = lookDir.scale(BEAM_KNOCKBACK_HORIZONTAL * knockbackScale);
                entity.setDeltaMovement(entity.getDeltaMovement().add(knockback.x, BEAM_KNOCKBACK_VERTICAL * knockbackScale, knockback.z));
                entity.hurtMarked = true;
                beamHitCount++;
            }
        }

        spawnBeamEffects(level, eyePos, lookDir, beamLength);

        LOGGER.debug("[SimpleVoiceChatInteraction] ショックウェーブ発動: {} 位置={} AoE半径={} ビーム長={} ヒット数={}",
                sourcePlayer.getName().getString(), centerBlock, radialRadius, beamLength,
                hitEntityIds.size() + beamHitCount);
    }

    /**
     * エンティティにダメージと暗闇エフェクトを適用する。
     */
    private void applyDamageAndEffects(ServerLevel level, ServerPlayer sourcePlayer,
                                        LivingEntity entity, float baseDamage, int darknessDuration) {
        float actualDamage = baseDamage;
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

    /**
     * 指定した点がビームの円柱（シリンダー）の中にあるかを判定する。
     *
     * @param origin    ビームの始点（プレイヤーの目の位置）
     * @param direction ビームの方向（正規化済み）
     * @param length    ビームの長さ
     * @param point     判定対象の点
     * @return 円柱内にある場合 true
     */
    private boolean isInBeamCylinder(Vec3 origin, Vec3 direction, double length, Vec3 point) {
        Vec3 toPoint = point.subtract(origin);
        // ビーム軸方向への射影距離
        double projectedDistance = toPoint.dot(direction);
        if (projectedDistance < 0.0 || projectedDistance > length) {
            return false; // ビームの前後範囲外
        }
        // ビーム軸からの垂直距離の二乗
        Vec3 closestOnBeam = origin.add(direction.scale(projectedDistance));
        double perpendicularDistSq = point.distanceToSqr(closestOnBeam);
        return perpendicularDistSq <= BEAM_WIDTH * BEAM_WIDTH;
    }

    /**
     * 周囲全方位ショックウェーブのパーティクル・サウンドエフェクト。
     */
    private void spawnRadialEffects(ServerLevel level, Vec3 center, BlockPos centerBlock, double radius) {
        level.playSound(null, centerBlock, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 0.4F);

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
                            2, 0.05, 0.3, 0.05, 0.05
                    );
                }
            }
        }

        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                center.x, center.y + 0.1, center.z,
                10, radius * 0.5, 0.05, radius * 0.5, 0.01);
    }

    /**
     * ウォーデン風ソニックビームのパーティクル・サウンドエフェクト。
     * プレイヤーの視線方向にソニックブームパーティクルを連続発射する。
     */
    private void spawnBeamEffects(ServerLevel level, Vec3 eyePos, Vec3 direction, double beamLength) {
        // ソニックブーム音（ウォーデン固有の音）
        level.playSound(null, BlockPos.containing(eyePos), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 0.5F);

        // ビーム到達地点の着弾音
        Vec3 impactPos = eyePos.add(direction.scale(beamLength));
        BlockPos impactBlockPos = BlockPos.containing(impactPos);
        level.playSound(null, impactBlockPos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 0.3F);
        level.playSound(null, impactBlockPos, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 0.5F);

        // ビーム沿いにソニックブームパーティクルを配置
        for (double dist = 1.0; dist <= beamLength; dist += 3.0) {
            Vec3 pos = eyePos.add(direction.scale(dist));
            level.sendParticles(ParticleTypes.SONIC_BOOM,
                    pos.x, pos.y, pos.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        // ビーム沿いに煙パーティクルを散らす（軌道の可視化）
        for (double dist = 0.5; dist <= beamLength; dist += 1.5) {
            Vec3 pos = eyePos.add(direction.scale(dist));
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.x, pos.y, pos.z,
                    2, 0.15, 0.15, 0.15, 0.01);
        }
    }
}

