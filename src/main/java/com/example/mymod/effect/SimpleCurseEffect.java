package com.example.mymod.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Простое проклятье — наносит 1 урона в секунду.
 *
 * Используется заклинанием «Проклятье ученика» (ApprenticesCurseSpell).
 * Категория HARMFUL — иконка красная, показывается как негативный эффект.
 *
 * Формула урона: 0.5f единицы за тик = 1 HP в секунду (20 тиков).
 * Стандартный урон Minecraft: 2 единицы = 1 HP. Мы используем 1.0f = 0.5 HP,
 * но applyEffectTick() вызывается каждые 20 тиков, значит 1.0f в секунду.
 */
public class SimpleCurseEffect extends MobEffect {

    public SimpleCurseEffect() {
        // HARMFUL — вредный эффект. 0x6B0080 — тёмно-фиолетовый цвет частиц.
        super(MobEffectCategory.HARMFUL, 0x6B0080);
    }

    /**
     * Наносит урон при каждом "тике эффекта".
     * isDurationEffectTick() контролирует, когда именно это вызывается.
     *
     * 1.0f урона = 0.5 HP (в Minecraft 2 единицы = 1 HP).
     * Вызывается раз в 20 тиков, итого 0.5 HP/сек.
     * Чтобы получить ровно 1 HP/сек — используем 2.0f.
     */
    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        entity.hurt(entity.damageSources().magic(), 2.0f); // 2.0f = 1 HP
    }

    /**
     * Определяет, нужно ли вызвать applyEffectTick на этом тике.
     * Возвращаем true раз в 20 тиков = каждую секунду.
     *
     * @param duration  оставшаяся длительность эффекта в тиках
     * @param amplifier уровень усиления эффекта (0 = уровень 1)
     */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }
}
