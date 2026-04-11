package com.example.mymod.skill;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Провайдер capability навыков — прикрепляется к игроку через AttachCapabilitiesEvent.
 *
 * Паттерн аналогичен ManaProvider: хранит LazyOptional и делегирует
 * сериализацию в PlayerSkillsCapability.
 */
public class PlayerSkillsProvider implements ICapabilitySerializable<CompoundTag> {

    private final PlayerSkillsCapability instance = new PlayerSkillsCapability();
    private final LazyOptional<IPlayerSkills> optional = LazyOptional.of(() -> instance);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return PlayerSkillsCapability.SKILLS.orEmpty(cap, optional);
    }

    @Override
    public CompoundTag serializeNBT() {
        return instance.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        instance.deserializeNBT(tag);
    }

    /** Инвалидировать LazyOptional при удалении игрока — освобождает память */
    public void invalidate() {
        optional.invalidate();
    }
}
