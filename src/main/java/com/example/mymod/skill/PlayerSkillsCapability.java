package com.example.mymod.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import java.util.*;

/**
 * Реализация capability навыков игрока.
 *
 * Хранит:
 *   - Очки навыков (skillPoints)
 *   - Множество разблокированных нод (unlockedNodes)
 *   - Серверные кулдауны заклинаний (cooldowns)
 *   - Слоты колеса заклинаний (spellSlots)
 *   - Индекс активного слота (activeSlot)
 *
 * Синхронизируется с клиентом через SkillSyncPacket.
 * Персистентно сохраняется через NBT.
 */
public class PlayerSkillsCapability implements IPlayerSkills {

    /** Токен для получения capability у игрока: player.getCapability(PlayerSkillsCapability.SKILLS) */
    public static final Capability<IPlayerSkills> SKILLS =
            CapabilityManager.get(new CapabilityToken<>() {});

    /** Начальное количество очков навыков (достаточно для нескольких нод) */
    private static final int STARTING_POINTS = 5;

    private int skillPoints = STARTING_POINTS;
    private final Set<String> unlockedNodes = new HashSet<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();

    // Колесо заклинаний: 10 слотов, null = пусто
    private final String[] spellSlots = new String[SPELL_SLOTS];
    private int activeSlot = 0;

    // ─── Очки навыков ─────────────────────────────────────────────────────────

    @Override public int getSkillPoints()          { return skillPoints; }
    @Override public void setSkillPoints(int p)    { skillPoints = Math.max(0, p); }
    @Override public void addSkillPoints(int a)    { skillPoints += a; }

    // ─── Ноды ─────────────────────────────────────────────────────────────────

    @Override
    public Set<String> getUnlockedNodes()          { return Collections.unmodifiableSet(unlockedNodes); }

    @Override
    public boolean isUnlocked(String nodeId)       { return unlockedNodes.contains(nodeId); }

    @Override
    public void unlockNode(String nodeId)          { unlockedNodes.add(nodeId); }

    // ─── Бонусы ───────────────────────────────────────────────────────────────

    @Override
    public int getBonusManaRegen() {
        // +2 за каждую открытую PASSIVE-ноду с бонусом регенерации
        int bonus = 0;
        for (SkillNode node : SkillTree.getAllNodes()) {
            if (node.getType() == SkillNode.NodeType.PASSIVE
                    && unlockedNodes.contains(node.getId())
                    && node.getBonusDescription() != null) {
                bonus += 2;
            }
        }
        return bonus;
    }

    // ─── Кулдауны ─────────────────────────────────────────────────────────────

    @Override
    public int getSpellCooldown(String spellId)    { return cooldowns.getOrDefault(spellId, 0); }

    @Override
    public void setSpellCooldown(String spellId, int ticks) {
        if (ticks <= 0) cooldowns.remove(spellId);
        else            cooldowns.put(spellId, ticks);
    }

    @Override
    public void tickCooldowns() {
        cooldowns.replaceAll((id, cd) -> cd - 1);
        cooldowns.entrySet().removeIf(e -> e.getValue() <= 0);
    }

    // ─── Колесо заклинаний ────────────────────────────────────────────────────

    @Override
    public @Nullable String getSpellInSlot(int index) {
        if (index < 0 || index >= SPELL_SLOTS) return null;
        return spellSlots[index];
    }

    @Override
    public void setSpellInSlot(int index, @Nullable String spellId) {
        if (index < 0 || index >= SPELL_SLOTS) return;
        spellSlots[index] = spellId;
    }

    @Override
    public int getActiveSlot() { return activeSlot; }

    @Override
    public void setActiveSlot(int index) {
        if (index >= 0 && index < SPELL_SLOTS) activeSlot = index;
    }

    // ─── Синхронизация клиент/сервер ──────────────────────────────────────────

    @Override
    public void syncFromServer(int skillPoints, Set<String> unlockedNodes,
                               String[] slots, int activeSlot) {
        this.skillPoints = skillPoints;
        this.unlockedNodes.clear();
        this.unlockedNodes.addAll(unlockedNodes);

        // Синхронизируем слоты колеса.
        // Пустые строки "" трактуем как null — защита от кривой сериализации пакетов.
        for (int i = 0; i < SPELL_SLOTS; i++) {
            String val = (slots != null && i < slots.length) ? slots[i] : null;
            this.spellSlots[i] = (val != null && !val.isEmpty()) ? val : null;
        }
        this.activeSlot = activeSlot;

        // Кулдауны не синхронизируем — клиенту они не нужны для логики
    }

    // ─── NBT ─────────────────────────────────────────────────────────────────

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SkillPoints", skillPoints);

        // Список разблокированных нод
        ListTag nodeList = new ListTag();
        for (String nodeId : unlockedNodes) {
            nodeList.add(StringTag.valueOf(nodeId));
        }
        tag.put("UnlockedNodes", nodeList);

        // Кулдауны заклинаний
        CompoundTag cdTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : cooldowns.entrySet()) {
            cdTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("Cooldowns", cdTag);

        // Слоты колеса заклинаний (пустые слоты сохраняем как пустую строку)
        ListTag slotList = new ListTag();
        for (String slot : spellSlots) {
            slotList.add(StringTag.valueOf(slot != null ? slot : ""));
        }
        tag.put("SpellSlots", slotList);
        tag.putInt("ActiveSlot", activeSlot);

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        skillPoints = tag.getInt("SkillPoints");

        unlockedNodes.clear();
        ListTag nodeList = tag.getList("UnlockedNodes", Tag.TAG_STRING);
        for (int i = 0; i < nodeList.size(); i++) {
            unlockedNodes.add(nodeList.getString(i));
        }

        cooldowns.clear();
        CompoundTag cdTag = tag.getCompound("Cooldowns");
        for (String key : cdTag.getAllKeys()) {
            cooldowns.put(key, cdTag.getInt(key));
        }

        // Слоты колеса
        Arrays.fill(spellSlots, null);
        if (tag.contains("SpellSlots")) {
            ListTag slotList = tag.getList("SpellSlots", Tag.TAG_STRING);
            for (int i = 0; i < Math.min(SPELL_SLOTS, slotList.size()); i++) {
                String val = slotList.getString(i);
                spellSlots[i] = val.isEmpty() ? null : val;
            }
        }
        activeSlot = tag.contains("ActiveSlot") ? tag.getInt("ActiveSlot") : 0;
    }
}
