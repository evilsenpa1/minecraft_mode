package com.example.mymod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Снаряд заклинания «Искра» — пылающий огненный шар Багрового пути.
 *
 * Летит по баллистической дуге (с гравитацией) на расстояние до {@link #MAX_RANGE} блоков.
 * При ударе о блок или сущность — взрыв огня; поджигает 6 блоков в паттерне 2 ряда по 3.
 * Сущности-цели получают урон от огня и загораются.
 *
 * Визуально: хвост из пламени при полёте; огненная вспышка + дым при попадании.
 * Рендер: {@link com.example.mymod.client.render.SparkProjectileRenderer}.
 */
public class SparkProjectile extends ThrowableItemProjectile {

    /** Максимальная дальность полёта (в блоках, 3D-расстояние от точки спавна). */
    public static final double MAX_RANGE = 11.0;

    /** Гравитация снаряда — создаёт дугу полёта. Стандарт у снежка — 0.03f. */
    private static final float GRAVITY = 0.04f;

    /** Урон от прямого попадания по сущности. */
    private static final float ENTITY_DAMAGE = 3.0f;

    /** Длительность горения сущности (секунды). */
    private static final int ENTITY_FIRE_SECONDS = 5;

    /**
     * Паттерн поджига: 2 ряда по 3 блока (6 позиций).
     * Координаты относительно точки попадания: {dx, dy, dz}.
     * Первый ряд — по бокам от центра, второй — на шаг дальше.
     */
    private static final int[][] FIRE_PATTERN = {
        // Ряд 1 — три блока в одну сторону (X)
        {-1, 0, 0}, {0, 0, 0}, {1, 0, 0},
        // Ряд 2 — три блока ещё дальше (Z)
        {-1, 0, 1}, {0, 0, 1}, {1, 0, 1}
    };

    // Позиция спавна для контроля пройденного расстояния
    private double originX, originY, originZ;
    private boolean originRecorded = false;

    // ─── Конструкторы ────────────────────────────────────────────────────────

    public SparkProjectile(EntityType<? extends SparkProjectile> type, Level level) {
        super(type, level);
    }

    public SparkProjectile(EntityType<? extends SparkProjectile> type, LivingEntity owner, Level level) {
        super(type, owner, level);
    }

    // ─── Внешний вид ─────────────────────────────────────────────────────────

    /**
     * Базовый предмет для рендера через ThrownItemRenderer (запасной вариант).
     * Основной рендер — {@link com.example.mymod.client.render.SparkProjectileRenderer}.
     */
    @Override
    protected Item getDefaultItem() {
        return Items.FIRE_CHARGE;
    }

    /**
     * Гравитация создаёт красивую дугу полёта.
     * Игрок должен целиться чуть выше цели — как при броске мяча.
     */
    @Override
    protected float getGravity() {
        return GRAVITY;
    }

    // ─── Тик ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        // Запоминаем точку старта один раз (только на сервере)
        if (!level().isClientSide() && !originRecorded) {
            originX = getX();
            originY = getY();
            originZ = getZ();
            originRecorded = true;
        }

        super.tick(); // Движение + коллизии (вызывает onHitBlock/onHitEntity)

        if (!isAlive()) return; // Снаряд уже попал — не спавним хвост

        // Огненный хвост при полёте (сервер рассылает пакеты всем клиентам)
        // Количество партиклов сокращено на 30% от оригинала
        if (!level().isClientSide() && level() instanceof ServerLevel serverLevel) {
            // Основное пламя: было 4 → стало 3
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    getX(), getY(), getZ(), 3, 0.1, 0.1, 0.1, 0.04);
            // Маленькие огоньки: было 3 → стало 2
            serverLevel.sendParticles(ParticleTypes.SMALL_FLAME,
                    getX(), getY(), getZ(), 2, 0.06, 0.06, 0.06, 0.02);
            // Лавовые брызги: шанс снижен с 50% до 35%
            if (random.nextFloat() < 0.35f) {
                serverLevel.sendParticles(ParticleTypes.LAVA,
                        getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // Уничтожить снаряд, если он улетел дальше максимальной дальности
        if (!level().isClientSide() && originRecorded) {
            double distSq = distanceToSqr(originX, originY, originZ);
            if (distSq >= MAX_RANGE * MAX_RANGE) {
                // Небольшой эффект угасания при исчезновении
                if (level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SMOKE,
                            getX(), getY(), getZ(), 4, 0.15, 0.15, 0.15, 0.04);
                }
                discard();
            }
        }
    }

    // ─── Обработка попаданий ─────────────────────────────────────────────────

    /** Попадание в блок — взрыв огня вокруг точки контакта. */
    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide()) {
            playImpactEffects(result.getBlockPos());
            spreadFirePattern(result.getBlockPos());
            discard();
        }
    }

    /** Попадание в сущность — урон + поджог + огонь вокруг. */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide()) {
            var entity = result.getEntity();

            // Поджигаем сущность
            entity.setSecondsOnFire(ENTITY_FIRE_SECONDS);

            // Прямой урон от огня
            if (entity instanceof LivingEntity living) {
                living.hurt(damageSources().onFire(), ENTITY_DAMAGE);
            }

            // Огонь вокруг места попадания
            playImpactEffects(entity.blockPosition());
            spreadFirePattern(entity.blockPosition());
            discard();
        }
    }

    // ─── Внутренняя логика ────────────────────────────────────────────────────

    /**
     * Поджигает блоки по паттерну 2 ряда × 3 блока (итого до 6 позиций).
     *
     * Паттерн ориентируется по горизонтальному направлению полёта снаряда:
     * «ширина» (3 блока) перпендикулярна траектории, «глубина» (2 блока) — вперёд.
     * Это убирает артефакт всегда-осевой ориентации — огонь всегда «за» снарядом.
     *
     * Для каждой позиции ищет свободное место по Y (уровень ±0..+2 от центра).
     *
     * @param center блок, в который попал снаряд
     */
    private void spreadFirePattern(BlockPos center) {
        // Берём текущий вектор скорости — он указывает направление полёта в момент удара
        Vec3 vel = getDeltaMovement();

        // Горизонтальный угол (yaw) по осям X и Z: atan2(x, z) даёт угол от +Z по часовой
        double yawRad = Math.atan2(vel.x, vel.z);
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);

        for (int[] offset : FIRE_PATTERN) {
            // offset[0] = «ширина» (перпендикуляр к направлению)
            // offset[2] = «глубина» (вдоль направления полёта)
            int dx = offset[0];
            int dy = offset[1];
            int dz = offset[2];

            // Поворачиваем смещение вокруг вертикальной оси Y на угол yaw
            int rotDx = (int) Math.round(dx * cos - dz * sin);
            int rotDz = (int) Math.round(dx * sin + dz * cos);

            // Пробуем три уровня по Y — подстраиваемся под рельеф местности
            for (int yTry = 0; yTry <= 2; yTry++) {
                BlockPos firePos = center.offset(rotDx, dy + yTry, rotDz);
                if (BaseFireBlock.canBePlacedAt(level(), firePos, Direction.UP)) {
                    level().setBlockAndUpdate(firePos, BaseFireBlock.getState(level(), firePos));
                    break; // Нашли — переходим к следующей точке паттерна
                }
            }
        }
    }

    /**
     * Воспроизводит визуальные и звуковые эффекты взрыва в точке попадания.
     * Количество партиклов сокращено на 30% от оригинала.
     *
     * @param center блок, в который попал снаряд
     */
    private void playImpactEffects(BlockPos center) {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;

        // Взрывной выброс лавы: было 20 → стало 14
        serverLevel.sendParticles(ParticleTypes.LAVA,
                cx, cy, cz, 14, 0.5, 0.3, 0.5, 0.2);
        // Вспышка пламени: было 35 → стало 25
        serverLevel.sendParticles(ParticleTypes.FLAME,
                cx, cy, cz, 25, 0.6, 0.4, 0.6, 0.14);
        // Чёрный дым шлейфом вверх: было 12 → стало 8
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                cx, cy + 0.5, cz, 8, 0.4, 0.3, 0.4, 0.06);
        // Летящие огоньки: было 15 → стало 10
        serverLevel.sendParticles(ParticleTypes.SMALL_FLAME,
                cx, cy, cz, 10, 0.4, 0.4, 0.4, 0.08);

        // Звук удара огненного шара
        level().playSound(null, center,
                SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS,
                1.2f, 0.85f);
    }
}
