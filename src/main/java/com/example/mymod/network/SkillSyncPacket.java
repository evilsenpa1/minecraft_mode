package com.example.mymod.network;

import com.example.mymod.skill.IPlayerSkills;
import com.example.mymod.skill.PlayerSkillsCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Пакет синхронизации навыков: сервер → клиент.
 *
 * Отправляется после:
 *   - Входа игрока на сервер
 *   - Изучения ноды в дереве прокачки
 *   - Назначения заклинания в слот колеса
 *   - Смерти/возрождения
 *
 * Обновляет PlayerSkillsCapability на клиентской стороне,
 * включая слоты колеса заклинаний и активный слот.
 */
public class SkillSyncPacket {

    private final int skillPoints;
    private final Set<String> unlockedNodes;
    private final String[] spellSlots;
    private final int activeSlot;

    public SkillSyncPacket(int skillPoints, Set<String> unlockedNodes,
                           String[] spellSlots, int activeSlot) {
        this.skillPoints   = skillPoints;
        this.unlockedNodes = Set.copyOf(unlockedNodes);
        // Защитная копия массива. null = пустой слот, сохраняем как null.
        // encode() при записи в буфер заменит null на "" — это нормально для FriendlyByteBuf.
        // decode() уже преобразует "" → null при чтении.
        this.spellSlots = new String[IPlayerSkills.SPELL_SLOTS];
        for (int i = 0; i < IPlayerSkills.SPELL_SLOTS; i++) {
            this.spellSlots[i] = (spellSlots != null && i < spellSlots.length)
                    ? spellSlots[i] : null;
        }
        this.activeSlot = activeSlot;
    }

    // ─── Кодирование / декодирование ──────────────────────────────────────────

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(skillPoints);

        // Разблокированные ноды
        buf.writeVarInt(unlockedNodes.size());
        for (String nodeId : unlockedNodes) {
            buf.writeUtf(nodeId);
        }

        // Слоты колеса (всегда ровно SPELL_SLOTS записей, пустые = "")
        for (int i = 0; i < IPlayerSkills.SPELL_SLOTS; i++) {
            buf.writeUtf(spellSlots[i] != null ? spellSlots[i] : "");
        }
        buf.writeVarInt(activeSlot);
    }

    public static SkillSyncPacket decode(FriendlyByteBuf buf) {
        int skillPoints = buf.readVarInt();

        int count = buf.readVarInt();
        Set<String> nodes = new HashSet<>();
        for (int i = 0; i < count; i++) {
            nodes.add(buf.readUtf());
        }

        String[] slots = new String[IPlayerSkills.SPELL_SLOTS];
        for (int i = 0; i < IPlayerSkills.SPELL_SLOTS; i++) {
            String val = buf.readUtf();
            slots[i] = val.isEmpty() ? null : val;
        }
        int activeSlot = buf.readVarInt();

        return new SkillSyncPacket(skillPoints, nodes, slots, activeSlot);
    }

    // ─── Обработка на клиенте ─────────────────────────────────────────────────

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            mc.player.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(skills ->
                    skills.syncFromServer(skillPoints, unlockedNodes, spellSlots, activeSlot)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
