package com.example.mymod.network;

import com.example.mymod.capability.ManaCapability;
import com.example.mymod.event.ModEvents;
import com.example.mymod.skill.IPlayerSkills;
import com.example.mymod.skill.PlayerSkillsCapability;
import com.example.mymod.spell.Spell;
import com.example.mymod.spell.SpellRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Пакет применения заклинания из активного слота: клиент → сервер.
 *
 * Отправляется когда игрок нажимает клавишу каста (F по умолчанию).
 * Сервер: берёт spellId из активного слота, проверяет ману и кулдаун,
 * применяет заклинание, синхронизирует ману.
 *
 * Пакет не несёт данных — вся логика на сервере по capability игрока.
 */
public class CastSpellPacket {

    // Пакет без данных
    public CastSpellPacket() {}

    // ─── Кодирование / декодирование ──────────────────────────────────────────

    public void encode(FriendlyByteBuf buf) {
        // Данных нет
    }

    public static CastSpellPacket decode(FriendlyByteBuf buf) {
        return new CastSpellPacket();
    }

    // ─── Обработка на сервере ─────────────────────────────────────────────────

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            player.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(skills ->
                    tryCastActiveSpell(player, skills)
            );
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Попытка применить заклинание из активного слота.
     *
     * Порядок проверок:
     *   1. Нода mana_core открыта (система маны пробуждена)
     *   2. В активном слоте есть заклинание
     *   3. Нода заклинания открыта у игрока
     *   4. Нет активного кулдауна
     *   5. Достаточно маны
     *   → применить заклинание, списать ману, поставить кулдаун
     */
    private static void tryCastActiveSpell(ServerPlayer player, IPlayerSkills skills) {
        // 1. Система маны должна быть пробуждена
        if (!skills.isUnlocked("mana_core")) {
            player.sendSystemMessage(Component.literal(
                    "§8✗ Откройте ноду §7«Пробуждение»§8 в Древе Познания."));
            return;
        }

        // 2. Получаем заклинание из активного слота
        String spellId = skills.getSpellInSlot(skills.getActiveSlot());
        if (spellId == null) {
            player.sendSystemMessage(Component.literal(
                    "§8✗ Активный слот пуст. §7Откройте колесо заклинаний (R)."));
            return;
        }

        Spell spell = SpellRegistry.get(spellId);
        if (spell == null) return; // заклинание удалено из регистра

        // 3. Нода заклинания должна быть открыта (дополнительная серверная проверка)
        boolean nodeUnlocked = isSpellNodeUnlocked(skills, spellId);
        if (!nodeUnlocked) {
            player.sendSystemMessage(Component.literal(
                    "§c✗ Заклинание §e" + spell.getDisplayName() + " §cне изучено."));
            return;
        }

        // 4. Серверный кулдаун (по spellId — не привязан к предмету)
        int cd = skills.getSpellCooldown(spellId);
        if (cd > 0) {
            // Тихое отклонение — кулдаун виден в HUD
            return;
        }

        // 5. Проверяем и списываем ману
        boolean[] manaOk = {false};
        player.getCapability(ManaCapability.MANA).ifPresent(mana -> {
            if (mana.useMana(spell.getManaCost())) {
                manaOk[0] = true;
                ModEvents.syncManaToClient(player, mana);
            }
        });

        if (!manaOk[0]) {
            player.sendSystemMessage(Component.literal(
                    "§9✗ Недостаточно маны. §bТребуется: " + spell.getManaCost()));
            return;
        }

        // Применяем заклинание
        spell.cast(player);

        // Серверный кулдаун по spellId (решает абуз с несколькими свитками)
        skills.setSpellCooldown(spellId, spell.getCooldownTicks());

        // Синхронизируем кулдауны на клиент (для HUD)
        syncCooldownToClient(player, skills, spellId, spell.getCooldownTicks());
    }

    /**
     * Синхронизировать кулдаун конкретного заклинания на клиент.
     * Использует полный SkillSyncPacket (несёт очки, ноды, слоты).
     */
    private static void syncCooldownToClient(ServerPlayer player, IPlayerSkills skills,
                                              String spellId, int cooldownTicks) {
        // Полный sync — клиент получит актуальные слоты и activeSlot
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

    /**
     * Проверить что у игрока открыта нода с данным spellId.
     */
    private static boolean isSpellNodeUnlocked(IPlayerSkills skills, String targetSpellId) {
        for (var node : com.example.mymod.skill.SkillTree.getAllNodes()) {
            if (targetSpellId.equals(node.getSpellId()) && skills.isUnlocked(node.getId())) {
                return true;
            }
        }
        return false;
    }
}
