package com.example.mymod.network;

import com.example.mymod.MyMod;
import com.example.mymod.skill.IPlayerSkills;
import com.example.mymod.skill.PlayerSkillsCapability;
import com.example.mymod.spell.SpellRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Пакет назначения заклинания в слот колеса: клиент → сервер.
 *
 * Отправляется из SkillTreeScreen, когда игрок нажимает кнопку
 * с номером слота напротив ноды-заклинания.
 *
 * Сервер проверяет: заклинание существует, нода открыта у игрока,
 * слот в допустимом диапазоне — и применяет изменение.
 * После чего шлёт SkillSyncPacket обратно.
 */
public class SpellSlotPacket {

    /** Индекс слота [0..SPELL_SLOTS-1]. -1 = очистить слот (spellId игнорируется) */
    private final int slotIndex;
    /** ID заклинания из SpellRegistry. Пустая строка = очистить */
    private final String spellId;

    /**
     * @param slotIndex индекс слота [0..9]
     * @param spellId   ID заклинания или null/пустая строка для очистки
     */
    public SpellSlotPacket(int slotIndex, String spellId) {
        this.slotIndex = slotIndex;
        this.spellId   = spellId != null ? spellId : "";
    }

    // ─── Кодирование / декодирование ──────────────────────────────────────────

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slotIndex);
        buf.writeUtf(spellId);
    }

    public static SpellSlotPacket decode(FriendlyByteBuf buf) {
        int slot   = buf.readVarInt();
        String sid = buf.readUtf();
        return new SpellSlotPacket(slot, sid);
    }

    // ─── Обработка на сервере ─────────────────────────────────────────────────

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Валидация индекса слота
            if (slotIndex < 0 || slotIndex >= IPlayerSkills.SPELL_SLOTS) {
                MyMod.LOGGER.warn("[{}] SpellSlotPacket: неверный индекс слота {} от {}",
                        MyMod.MOD_ID, slotIndex, player.getName().getString());
                return;
            }

            player.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(skills -> {
                // Специальный маркер "::active" — изменить только активный слот
                if ("::active".equals(spellId)) {
                    skills.setActiveSlot(slotIndex);
                    syncToClient(player, skills);
                    return;
                }

                String targetSpell = spellId.isEmpty() ? null : spellId;

                // Если назначаем заклинание — проверяем что оно существует и открыто
                if (targetSpell != null) {
                    if (SpellRegistry.get(targetSpell) == null) {
                        MyMod.LOGGER.warn("[{}] SpellSlotPacket: заклинание '{}' не найдено",
                                MyMod.MOD_ID, targetSpell);
                        return;
                    }
                    // Проверяем что у игрока открыта нода этого заклинания
                    boolean isUnlocked = isSpellNodeUnlocked(skills, targetSpell);
                    if (!isUnlocked) {
                        MyMod.LOGGER.warn("[{}] SpellSlotPacket: заклинание '{}' не открыто у {}",
                                MyMod.MOD_ID, targetSpell, player.getName().getString());
                        return;
                    }
                }

                // Уникальность: если это заклинание уже стоит в другом слоте — убираем оттуда.
                // Одно заклинание не может занимать два слота одновременно.
                // targetSpell может быть null при очистке слота — проверяем перед вызовом equals.
                if (targetSpell != null) {
                    for (int i = 0; i < IPlayerSkills.SPELL_SLOTS; i++) {
                        if (i != slotIndex && targetSpell.equals(skills.getSpellInSlot(i))) {
                            skills.setSpellInSlot(i, null);
                            break;
                        }
                    }
                }

                // Применяем изменение слота
                skills.setSpellInSlot(slotIndex, targetSpell);

                // Синхронизируем изменение обратно на клиент
                syncToClient(player, skills);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Проверить что у игрока открыта хотя бы одна нода с данным spellId.
     */
    private static boolean isSpellNodeUnlocked(IPlayerSkills skills, String targetSpellId) {
        for (var node : com.example.mymod.skill.SkillTree.getAllNodes()) {
            if (targetSpellId.equals(node.getSpellId()) && skills.isUnlocked(node.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Собрать и отправить SkillSyncPacket игроку.
     */
    private static void syncToClient(ServerPlayer player, IPlayerSkills skills) {
        String[] slots = new String[IPlayerSkills.SPELL_SLOTS];
        for (int i = 0; i < IPlayerSkills.SPELL_SLOTS; i++) {
            slots[i] = skills.getSpellInSlot(i);
        }
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SkillSyncPacket(skills.getSkillPoints(), skills.getUnlockedNodes(),
                        slots, skills.getActiveSlot())
        );
    }
}
