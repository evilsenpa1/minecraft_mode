package com.example.mymod.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Провайдер capability маны.
 * Создаётся один раз при AttachCapabilitiesEvent и привязывается к игроку.
 * Отвечает за:
 *   - выдачу объекта маны по запросу через getCapability()
 *   - сохранение/загрузку через NBT (ICapabilitySerializable)
 *   - инвалидацию LazyOptional при удалении игрока (освобождение памяти)
 */
public class ManaProvider implements ICapabilitySerializable<CompoundTag> {

    /** Хранилище данных маны */
    private final ManaCapability manaInstance = new ManaCapability();

    /**
     * LazyOptional — обёртка Forge, которая даёт ссылку на capability
     * без жёсткого удержания в памяти.
     */
    private final LazyOptional<IMana> lazyOptional = LazyOptional.of(() -> manaInstance);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        // Возвращаем наш LazyOptional только если запрашивают именно MANA capability
        return ManaCapability.MANA.orEmpty(cap, lazyOptional);
    }

    @Override
    public CompoundTag serializeNBT() {
        return manaInstance.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        manaInstance.deserializeNBT(nbt);
    }

    /**
     * Инвалидировать LazyOptional.
     * Вызывается через event.addListener() при удалении capability,
     * чтобы Forge мог корректно освободить ресурсы.
     */
    public void invalidate() {
        lazyOptional.invalidate();
    }
}
