package com.example.mymod.capability;

import net.minecraft.nbt.CompoundTag;

/**
 * Интерфейс системы маны игрока.
 * Хранит текущий и максимальный запас маны, умеет сохраняться в NBT.
 */
public interface IMana {

    /** Вернуть текущую ману */
    int getMana();

    /** Установить ману (автоматически обрезается в диапазон [0, max]) */
    void setMana(int mana);

    /** Вернуть максимальную ману */
    int getMaxMana();

    /**
     * Добавить ману — не превысит максимум.
     *
     * @param amount сколько добавить
     */
    void addMana(int amount);

    /**
     * Попытаться потратить ману.
     *
     * @param amount сколько тратить
     * @return true если маны хватило и она была списана, false если не хватает
     */
    boolean useMana(int amount);

    /** Сериализовать в NBT (для сохранения в мире) */
    CompoundTag serializeNBT();

    /** Десериализовать из NBT (при загрузке мира) */
    void deserializeNBT(CompoundTag nbt);
}
