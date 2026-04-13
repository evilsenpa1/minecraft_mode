package com.example.mymod.spell.impl;

import com.example.mymod.entity.ModEntities;
import com.example.mymod.entity.SparkProjectile;
import com.example.mymod.spell.MagicSchool;
import com.example.mymod.spell.Spell;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Искра — первое заклинание Багрового пути.
 *
 * Запускает огненный снаряд, летящий по дуге (с гравитацией) на 11 блоков.
 * При ударе — взрывной выброс огня, поджигает 6 блоков (2 ряда по 3).
 *
 * Из-за дугового полёта игроку нужно целиться чуть выше цели — как при броске.
 *
 * Дальность: 11 блоков. Стоимость: 10 маны. Откат: 5 секунд (100 тиков).
 */
public class SparkSpell extends Spell {

    /**
     * Скорость полёта снаряда (блоков/тик).
     * Увеличена с 0.8 до 1.0 — компенсирует гравитацию на длинной дистанции.
     */
    private static final float PROJECTILE_SPEED = 1.0f;

    public SparkSpell() {
        super("spark", MagicSchool.CRIMSON, 10, 100);
    }

    @Override
    public void cast(ServerPlayer player) {
        SparkProjectile projectile = new SparkProjectile(
                ModEntities.SPARK_PROJECTILE.get(),
                player,
                player.level()
        );

        // Размещаем снаряд чуть впереди от глаз игрока, чтобы не застрять в себе
        Vec3 eye  = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        projectile.setPos(
                eye.x + look.x * 0.7,
                eye.y + look.y * 0.7,
                eye.z + look.z * 0.7
        );

        // Задаём направление по взгляду игрока — снаряд летит дугой благодаря гравитации
        projectile.shootFromRotation(
                player,
                player.getXRot(), player.getYRot(),
                0.0f,             // без дополнительного подъёма — игрок сам прицеливается
                PROJECTILE_SPEED, // скорость
                0.0f              // точность (0 = идеально прямо)
        );

        player.level().addFreshEntity(projectile);

        // Звук пуска — шипение/хлопок огненного шара
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS,
                0.7f, 1.5f);
    }

    @Override
    public String getDisplayName() { return "Искра"; }

    @Override
    public String getDescription() {
        return "Огненный шар, летящий по дуге и поджигающий всё вокруг.\n«Дыма без огня не бывает»";
    }
}
