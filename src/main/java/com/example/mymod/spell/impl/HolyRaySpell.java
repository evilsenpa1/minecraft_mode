package com.example.mymod.spell.impl;

import com.example.mymod.spell.MagicSchool;
import com.example.mymod.spell.Spell;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Луч Святости — первое заклинание Пути Тимофея.
 *
 * Хитскан-луч дальностью 15 блоков. Ударяет первую встреченную живую сущность.
 * Монстры получают 10 урона, все остальные — 4 урона.
 *
 * Визуально: яркий золотисто-жёлтый луч с электрическими искрами —
 * сверкающая молния божественного света.
 *
 * Стоимость: 15 маны. Откат: 10 секунд (200 тиков).
 */
public class HolyRaySpell extends Spell {

    private static final double RAY_LENGTH = 15.0;

    /** Урон монстрам (нечисть, враги). */
    private static final float DAMAGE_MONSTER = 10.0f;

    /** Урон остальным сущностям. */
    private static final float DAMAGE_OTHER = 4.0f;

    // Цветовые профили частиц луча
    /** Основной золотой цвет луча. */
    private static final DustParticleOptions GOLD_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.0f), 1.4f);

    /** Белое золото — яркие блики. */
    private static final DustParticleOptions WHITE_GOLD_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.97f, 0.6f), 0.9f);

    /** Горячий внутренний core луча — почти белый. */
    private static final DustParticleOptions CORE_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 1.0f, 0.85f), 0.6f);

    public HolyRaySpell() {
        super("holy_ray", MagicSchool.HOLY, 15, 200);
    }

    @Override
    public void cast(ServerPlayer player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 look   = player.getLookAngle();
        Vec3 end    = eyePos.add(look.scale(RAY_LENGTH));

        // Рейкаст по блокам: найти ближайший непрозрачный блок на пути луча
        BlockHitResult blockHit = player.level().clip(new ClipContext(
                eyePos, end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.SOURCE_ONLY,
                player
        ));

        // Если луч упёрся в блок — укорачиваем дальность
        Vec3 effectiveEnd = (blockHit.getType() == HitResult.Type.BLOCK)
                ? blockHit.getLocation()
                : end;

        // Найти ближайшую живую сущность на пути луча (до стены)
        Entity target = findEntityOnRay(player, eyePos, effectiveEnd);

        ServerLevel serverLevel = (ServerLevel) player.level();

        // ── Наносим урон ────────────────────────────────────────────────────
        if (target instanceof LivingEntity living) {
            float damage = (living instanceof Monster) ? DAMAGE_MONSTER : DAMAGE_OTHER;
            living.hurt(player.damageSources().magic(), damage);

            // Взрыв святого света на цели
            spawnImpactParticles(serverLevel, living);
        }

        // ── Рисуем луч-молнию ───────────────────────────────────────────────
        Vec3 dir = look.normalize();
        double beamLength = target != null
                ? target.position().distanceTo(eyePos)
                : effectiveEnd.distanceTo(eyePos);

        spawnBeamParticles(serverLevel, eyePos, dir, beamLength);

        // ── Звук ────────────────────────────────────────────────────────────
        // Высокий тон маяка + небольшой треск молнии
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS,
                0.5f, 2.0f);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS,
                0.08f, 2.0f);
    }

    /**
     * Спавнит частицы луча — сверкающая золотая молния.
     *
     * Три слоя:
     * - Основной золотой трейл (плотный)
     * - Белые искры по краям (электрический эффект)
     * - Редкие зачарованные звёздочки для блеска
     */
    private void spawnBeamParticles(ServerLevel level, Vec3 origin, Vec3 dir, double length) {
        for (double dist = 0.5; dist < length; dist += 0.55) {
            Vec3 pos = origin.add(dir.scale(dist));

            // Слой 1: золотой core луча
            level.sendParticles(GOLD_DUST,
                    pos.x, pos.y, pos.z,
                    1, 0.05, 0.05, 0.05, 0.0);

            // Слой 2: белое золото — внутренний блик (реже, для мерцания)
            level.sendParticles(CORE_DUST,
                    pos.x, pos.y, pos.z,
                    1, 0.02, 0.02, 0.02, 0.0);

            // Слой 3: электрические искры — появляются случайно вдоль луча
            if ((dist % 1.1) < 0.55) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        pos.x, pos.y, pos.z,
                        1, 0.12, 0.08, 0.12, 0.15);
            }

            // Слой 4: блики зачарования — редкие звёздочки
            if ((dist % 1.65) < 0.55) {
                level.sendParticles(WHITE_GOLD_DUST,
                        pos.x, pos.y, pos.z,
                        1, 0.08, 0.06, 0.08, 0.0);
                level.sendParticles(ParticleTypes.ENCHANT,
                        pos.x, pos.y, pos.z,
                        1, 0.1, 0.08, 0.1, 0.08);
            }
        }
    }

    /**
     * Спавнит частицы взрыва святого света при попадании в цель.
     * Золотая вспышка + электрические разряды + ослепляющий блик.
     */
    private void spawnImpactParticles(ServerLevel level, LivingEntity target) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() / 2.0;
        double z = target.getZ();

        // Широкий золотой взрыв
        level.sendParticles(GOLD_DUST,
                x, y, z, 40, 0.45, 0.55, 0.45, 0.18);

        // Белые искры — разряд молнии
        level.sendParticles(WHITE_GOLD_DUST,
                x, y, z, 20, 0.3, 0.4, 0.3, 0.22);

        // Электрические разряды вокруг цели
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                x, y, z, 18, 0.5, 0.6, 0.5, 0.2);

        // Ослепляющий центральный блик
        level.sendParticles(ParticleTypes.FLASH,
                x, y, z, 3, 0.1, 0.1, 0.1, 0.0);

        // Блики зачарования — разлетаются от цели
        level.sendParticles(ParticleTypes.ENCHANT,
                x, y, z, 25, 0.4, 0.5, 0.4, 0.3);
    }

    /**
     * Найти ближайшую к лучу живую сущность (не считая кастующего).
     * Проверяем пересечение расширенных hitbox с лучом.
     */
    private Entity findEntityOnRay(ServerPlayer caster, Vec3 start, Vec3 end) {
        AABB searchBox = new AABB(start, end).inflate(0.6);
        List<Entity> candidates = caster.level().getEntities(
                caster,
                searchBox,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
        );

        Entity closest     = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : candidates) {
            var optHit = entity.getBoundingBox().inflate(0.3).clip(start, end);
            if (optHit.isPresent()) {
                double dist = optHit.get().distanceToSqr(start);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest     = entity;
                }
            }
        }
        return closest;
    }

    @Override
    public String getDisplayName() { return "Луч Святости"; }

    @Override
    public String getDescription() {
        return "Молния божественного света испепеляет нечисть.\n10 урона монстрам · 4 урона остальным.";
    }
}
