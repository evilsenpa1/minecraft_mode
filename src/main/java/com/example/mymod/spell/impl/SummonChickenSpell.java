package com.example.mymod.spell.impl;

import com.example.mymod.spell.MagicSchool;
import com.example.mymod.spell.Spell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.phys.Vec3;

/**
 * Призыв Курицы — первое заклинание Пути Марионетки.
 *
 * Призывает обычную ванильную курицу (EntityType.CHICKEN) перед игроком.
 * Курица появляется с эффектом телепортации (portal + end_rod частицы).
 *
 * Стоимость: 15 маны. Откат: 30 секунд (600 тиков).
 */
public class SummonChickenSpell extends Spell {

    public SummonChickenSpell() {
        super("summon_chicken", MagicSchool.PUPPET, 15, 600);
    }

    @Override
    public void cast(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        // Позиция появления — немного перед игроком
        Vec3 look = player.getLookAngle();
        // Убираем вертикальную составляющую и нормализуем
        Vec3 horizontal = new Vec3(look.x, 0, look.z).normalize();
        Vec3 spawnPos   = player.position().add(horizontal.scale(2.0));

        // Поднимаем чуть выше, чтобы не застрять в земле
        spawnPos = new Vec3(spawnPos.x, spawnPos.y + 0.1, spawnPos.z);

        // Создаём и настраиваем курицу
        Chicken chicken = new Chicken(EntityType.CHICKEN, level);
        chicken.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        chicken.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(player.blockPosition()),
                MobSpawnType.MOB_SUMMONED,
                null,
                null
        );

        level.addFreshEntity(chicken);

        // Частицы призыва: портал + белые искры
        level.sendParticles(ParticleTypes.PORTAL,
                spawnPos.x, spawnPos.y + 0.5, spawnPos.z,
                40, 0.3, 0.6, 0.3, 0.15);
        level.sendParticles(ParticleTypes.END_ROD,
                spawnPos.x, spawnPos.y + 0.3, spawnPos.z,
                12, 0.2, 0.3, 0.2, 0.06);

        // Звук телепортации
        level.playSound(null, player.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS,
                0.5f, 1.5f);
    }

    @Override public String getDisplayName() { return "Призыв Курицы"; }

    @Override
    public String getDescription() {
        return "Марионеточник начинает с малого.\nПризывает обычную курицу.";
    }
}
