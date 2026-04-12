package com.example.mymod.skill;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Интерфейс capability навыков игрока.
 *
 * Хранит: очки навыков, разблокированные ноды, кулдауны заклинаний,
 * слоты колеса заклинаний и индекс активного слота.
 * Реализуется PlayerSkillsCapability, выдаётся через PlayerSkillsProvider.
 */
public interface IPlayerSkills {

    /** Количество слотов в колесе заклинаний */
    int SPELL_SLOTS = 10;

    // ─── Очки навыков ─────────────────────────────────────────────────────────

    int getSkillPoints();
    void setSkillPoints(int points);
    void addSkillPoints(int amount);

    // ─── Разблокированные ноды ────────────────────────────────────────────────

    Set<String> getUnlockedNodes();
    boolean isUnlocked(String nodeId);

    /** Разблокировать ноду. Не проверяет стоимость — только записывает факт. */
    void unlockNode(String nodeId);

    // ─── Бонусы ───────────────────────────────────────────────────────────────

    /** Суммарный бонус к регенерации маны от всех пассивных нод (в единицах/сек) */
    int getBonusManaRegen();

    // ─── Кулдауны заклинаний (серверная сторона) ──────────────────────────────

    /** Оставшийся кулдаун заклинания в тиках. 0 = готово. */
    int getSpellCooldown(String spellId);

    /** Установить кулдаун заклинания. ticks <= 0 снимает кулдаун. */
    void setSpellCooldown(String spellId, int ticks);

    /** Уменьшить все кулдауны на 1 тик. Вызывается в PlayerTickEvent. */
    void tickCooldowns();

    // ─── Колесо заклинаний ────────────────────────────────────────────────────

    /**
     * ID заклинания в слоте [0..SPELL_SLOTS-1].
     * @return spellId или null, если слот пуст
     */
    @Nullable String getSpellInSlot(int index);

    /**
     * Назначить заклинание в слот.
     * @param spellId ID заклинания из SpellRegistry, или null — очистить слот
     */
    void setSpellInSlot(int index, @Nullable String spellId);

    /** Индекс выбранного (активного) слота [0..SPELL_SLOTS-1] */
    int getActiveSlot();

    /** Выбрать активный слот */
    void setActiveSlot(int index);

    // ─── Синхронизация ────────────────────────────────────────────────────────

    /**
     * Синхронизировать данные с сервера на клиент.
     * Вызывается из SkillSyncPacket на клиентской стороне.
     *
     * @param spellSlots  массив из SPELL_SLOTS элементов (null = пустой слот)
     * @param activeSlot  индекс активного слота
     */
    void syncFromServer(int skillPoints, Set<String> unlockedNodes,
                        String[] spellSlots, int activeSlot);

    // ─── Отслеживание уровня (для начисления очков за повышение уровня) ───────

    /**
     * Последний известный уровень опыта игрока.
     * -1 = ещё не инициализировано (новый игрок или первый тик после установки мода).
     * Используется чтобы обнаружить повышение уровня и выдать очки навыков.
     */
    int getLastKnownLevel();

    /** Обновить сохранённый уровень (после начисления очков или при уменьшении уровня) */
    void setLastKnownLevel(int level);

    // ─── NBT сериализация ─────────────────────────────────────────────────────

    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag tag);
}
