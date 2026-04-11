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

/**
 * Снаряд заклинания «Искра» — пылающий огненный шар Багрового пути.
 *
 * Летит по прямой без гравитации на расстояние до {@link #MAX_RANGE} блоков.
 * При ударе о блок или сущность — взрыв огня, поджигает до 6 ближайших позиций.
 * Сущности-цели получают урон от огня и загораются.
 *
 * Визуально: хвост из пламени и лавовых брызг при полёте;
 * огненная вспышка + дым при попадании.
 */
public class SparkProjectile extends ThrowableItemProjectile {

    /** Максимальная дальность полёта (в блоках). */
    public static final double MAX_RANGE = 8.0;

    /** Количество блоков, которые поджигаются при попадании. */
    private static final int FIRE_SPREAD_COUNT = 6;

    /** Урон от прямого попадания по сущности. */
    private static final float ENTITY_DAMAGE = 3.0f;

    /** Длительность горения сущности (секунды). */
    private static final int ENTITY_FIRE_SECONDS = 5;

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

    /** Снаряд выглядит как огненный шар (fire charge). */
    @Override
    protected Item getDefaultItem() {
        return Items.FIRE_CHARGE;
    }

    /** Летит без гравитации — магический снаряд идёт строго по прямой. */
    @Override
    protected float getGravity() {
        return 0.0f;
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

        super.tick(); // Обработка движения и коллизий (вызывает onHitBlock/onHitEntity)

        if (!isAlive()) return; // Снаряд уже попал — не спавним хвост

        // Огненный хвост при полёте (сервер рассылает пакеты всем клиентам)
        if (!level().isClientSide() && level() instanceof ServerLevel serverLevel) {
            // Основное пламя
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    getX(), getY(), getZ(), 4, 0.1, 0.1, 0.1, 0.04);
            // Маленькие огоньки для плотности
            serverLevel.sendParticles(ParticleTypes.SMALL_FLAME,
                    getX(), getY(), getZ(), 3, 0.06, 0.06, 0.06, 0.02);
            // Редкие лавовые брызги — эффект раскалённого ядра
            if (random.nextFloat() < 0.5f) {
                serverLevel.sendParticles(ParticleTypes.LAVA,
                        getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // Уничтожить снаряд, если он улетел дальше максимальной дальности
        if (!level().isClientSide() && originRecorded) {
            double distSq = distanceToSqr(originX, originY, originZ);
            if (distSq >= MAX_RANGE * MAX_RANGE) {
                // Небольшой эффект угасания
                if (level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SMOKE,
                            getX(), getY(), getZ(), 6, 0.15, 0.15, 0.15, 0.04);
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
            spreadFire(result.getBlockPos());
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
            spreadFire(entity.blockPosition());
            discard();
        }
    }

    // ─── Внутренняя логика ────────────────────────────────────────────────────

    /**
     * Поджигает до {@link #FIRE_SPREAD_COUNT} блоков вокруг центра взрыва.
     * Ищет свободные позиции где может разместиться огонь.
     *
     * @param center точка попадания снаряда
     */
    private void spreadFire(BlockPos center) {
        int placed = 0;

        // Перебираем позиции в области 5×3×5 вокруг центра
        outer:
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (placed >= FIRE_SPREAD_COUNT) break outer;

                    BlockPos firePos = center.offset(dx, dy, dz);
                    if (BaseFireBlock.canBePlacedAt(level(), firePos, Direction.UP)) {
                        level().setBlockAndUpdate(firePos, BaseFireBlock.getState(level(), firePos));
                        placed++;
                    }
                }
            }
        }
    }

    /**
     * Воспроизводит визуальные и звуковые эффекты взрыва в точке попадания.
     *
     * @param center блок, в который попал снаряд
     */
    private void playImpactEffects(BlockPos center) {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;

        // Взрывной выброс лавы и пламени
        serverLevel.sendParticles(ParticleTypes.LAVA,
                cx, cy, cz, 20, 0.5, 0.3, 0.5, 0.2);
        serverLevel.sendParticles(ParticleTypes.FLAME,
                cx, cy, cz, 35, 0.6, 0.4, 0.6, 0.14);
        // Чёрный дым шлейфом вверх
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                cx, cy + 0.5, cz, 12, 0.4, 0.3, 0.4, 0.06);
        // Летящие огоньки
        serverLevel.sendParticles(ParticleTypes.SMALL_FLAME,
                cx, cy, cz, 15, 0.4, 0.4, 0.4, 0.08);

        // Звук удара огненного шара
        level().playSound(null, center,
                SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS,
                1.2f, 0.85f);
    }
}
