package com.example.mymod.spell.impl;

import com.example.mymod.effect.ModEffects;
import com.example.mymod.spell.MagicSchool;
import com.example.mymod.spell.Spell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Проклятье Ученика — первое заклинание Пути Культиста.
 *
 * Накладывает DoT-эффект «Простое проклятье» на первую сущность в прицеле.
 * Эффект: 1 HP урона в секунду · 10 секунд (итого до 10 HP).
 *
 * Визуально: фиолетовые witch-частицы + тёмная вспышка на цели.
 * Стоимость: 30 маны. Откат: 20 секунд (400 тиков).
 */
public class ApprenticesCurseSpell extends Spell {

    /** Длительность проклятья: 10 секунд = 200 тиков */
    private static final int CURSE_DURATION_TICKS = 200;

    private static final double RAY_LENGTH = 15.0;

    public ApprenticesCurseSpell() {
        super("apprentice_curse", MagicSchool.CULTIST, 30, 400);
    }

    @Override
    public void cast(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end   = start.add(player.getLookAngle().scale(RAY_LENGTH));

        // Найти цель по лучу взгляда
        LivingEntity target = findTargetOnRay(player, start, end);

        if (target != null) {
            // Наложить эффект проклятья (amplifier=0 → уровень 1)
            target.addEffect(new MobEffectInstance(
                    ModEffects.SIMPLE_CURSE.get(),
                    CURSE_DURATION_TICKS,
                    0,     // amplifier
                    false, // не ambient (частицы нормального размера)
                    true   // показывать частицы
            ));

            // Тёмные частицы на цели — witch + large smoke
            double cx = target.getX();
            double cy = target.getY() + target.getBbHeight() / 2.0;
            double cz = target.getZ();

            // sendParticles доступен только на ServerLevel
            ServerLevel serverLevel = (ServerLevel) player.level();
            serverLevel.sendParticles(ParticleTypes.WITCH,
                    cx, cy, cz, 30, 0.4, 0.6, 0.4, 0.06);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    cx, cy + 0.5, cz, 8, 0.3, 0.4, 0.3, 0.02);

            // Звук: заглушённый свист = "наложение проклятья"
            player.level().playSound(null, target.blockPosition(),
                    SoundEvents.WITHER_SHOOT,
                    SoundSource.PLAYERS,
                    0.4f, 2.0f);
        }
    }

    /**
     * Найти живую сущность по лучу взгляда.
     * Возвращает ближайшую подходящую цель или null.
     */
    private LivingEntity findTargetOnRay(ServerPlayer caster, Vec3 start, Vec3 end) {
        AABB searchBox = new AABB(start, end).inflate(0.6);
        List<Entity> candidates = caster.level().getEntities(
                caster,
                searchBox,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
        );

        LivingEntity closest = null;
        double closestDist   = Double.MAX_VALUE;

        for (Entity entity : candidates) {
            var optHit = entity.getBoundingBox().inflate(0.3).clip(start, end);
            if (optHit.isPresent()) {
                double dist = optHit.get().distanceToSqr(start);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest     = (LivingEntity) entity;
                }
            }
        }
        return closest;
    }

    @Override public String getDisplayName() { return "Проклятье Ученика"; }

    @Override
    public String getDescription() {
        return "Простейшее из проклятий. Изъедает цель изнутри.\n1 урон/сек · 10 секунд.";
    }
}
