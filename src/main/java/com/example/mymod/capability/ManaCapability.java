package com.example.mymod.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/**
 * Реализация маны игрока.
 * Также содержит статический токен capability — через него
 * любой код может получить ману у сущности:
 *   entity.getCapability(ManaCapability.MANA)
 */
public class ManaCapability implements IMana {

    /** Токен для получения capability маны у сущности */
    public static final Capability<IMana> MANA = CapabilityManager.get(new CapabilityToken<>() {});

    /** Максимальная мана — потом вынесем в конфиг или атрибут */
    private static final int MAX_MANA = 100;

    private static final String NBT_KEY_MANA = "Mana";

    /** Текущая мана (начинаем с полным запасом) */
    private int mana = MAX_MANA;

    // ─── IMana ────────────────────────────────────────────────────────────────

    @Override
    public int getMana() {
        return mana;
    }

    @Override
    public void setMana(int mana) {
        // Гарантируем, что мана всегда в допустимом диапазоне
        this.mana = Math.max(0, Math.min(MAX_MANA, mana));
    }

    @Override
    public int getMaxMana() {
        return MAX_MANA;
    }

    @Override
    public void addMana(int amount) {
        setMana(this.mana + amount);
    }

    @Override
    public boolean useMana(int amount) {
        if (this.mana >= amount) {
            this.mana -= amount;
            return true;
        }
        // Маны недостаточно — ничего не списываем
        return false;
    }

    // ─── NBT ─────────────────────────────────────────────────────────────────

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NBT_KEY_MANA, mana);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.mana = nbt.getInt(NBT_KEY_MANA);
    }
}
