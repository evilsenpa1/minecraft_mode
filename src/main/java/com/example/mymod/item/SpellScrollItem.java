package com.example.mymod.item;

import com.example.mymod.capability.ManaCapability;
import com.example.mymod.event.ModEvents;
import com.example.mymod.skill.IPlayerSkills;
import com.example.mymod.skill.PlayerSkillsCapability;
import com.example.mymod.skill.SkillNode;
import com.example.mymod.skill.SkillTree;
import com.example.mymod.spell.Spell;
import com.example.mymod.spell.SpellRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Свиток заклинания — предмет, позволяющий применить конкретное заклинание.
 *
 * При правом клике:
 *   1. Проверяет, открыта ли нода заклинания в дереве прокачки.
 *   2. Проверяет наличие маны и кулдаун.
 *   3. Выполняет заклинание и списывает ману + устанавливает кулдаун.
 *
 * Логика выполняется только на сервере (ServerPlayer).
 * Кулдаун предмета синхронизируется ванильной механикой ItemCooldowns.
 */
public class SpellScrollItem extends Item {

    /** ID заклинания в SpellRegistry */
    private final String spellId;

    public SpellScrollItem(String spellId, Item.Properties properties) {
        super(properties);
        this.spellId = spellId;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Логика кастования только на сервере
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            tryCastSpell(serverPlayer, stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Попытаться применить заклинание. Проверяет все условия:
     * открытие ноды, наличие маны, кулдаун.
     */
    private void tryCastSpell(ServerPlayer player, ItemStack stack) {
        Spell spell = SpellRegistry.get(spellId);
        if (spell == null) return;

        // Проверяем, что нода заклинания открыта в дереве прокачки
        boolean isUnlocked = player.getCapability(PlayerSkillsCapability.SKILLS)
                .map(skills -> isSpellNodeUnlocked(skills, spellId))
                .orElse(false);

        if (!isUnlocked) {
            player.sendSystemMessage(Component.literal(
                    "§c✗ Это заклинание не изучено. Откройте нужную ноду в Древе Познания."));
            return;
        }

        // Проверяем кулдаун предмета (синхронизируется автоматически Minecraft)
        if (player.getCooldowns().isOnCooldown(this)) {
            return; // Тихо — кулдаун виден на предмете в хотбаре
        }

        // Проверяем ману и списываем
        boolean[] manaOk = {false};
        player.getCapability(ManaCapability.MANA).ifPresent(mana -> {
            if (mana.useMana(spell.getManaCost())) {
                manaOk[0] = true;
                // Синхронизируем ману с клиентом
                ModEvents.syncManaToClient(player, mana);
            }
        });

        if (!manaOk[0]) {
            player.sendSystemMessage(Component.literal(
                    "§9✗ Недостаточно маны. Требуется: §b" + spell.getManaCost()));
            return;
        }

        // Применяем заклинание
        spell.cast(player);

        // Устанавливаем кулдаун предмета (синхронизируется на клиент автоматически)
        player.getCooldowns().addCooldown(this, spell.getCooldownTicks());
    }

    /**
     * Проверить, что хотя бы одна нода с данным spellId открыта у игрока.
     */
    private static boolean isSpellNodeUnlocked(IPlayerSkills skills, String targetSpellId) {
        for (SkillNode node : SkillTree.getAllNodes()) {
            if (targetSpellId.equals(node.getSpellId()) && skills.isUnlocked(node.getId())) {
                return true;
            }
        }
        return false;
    }

    // ─── Подсказка (tooltip) в инвентаре ─────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        Spell spell = SpellRegistry.get(spellId);
        if (spell == null) return;

        // Название заклинания
        tooltip.add(Component.literal("§e" + spell.getDisplayName())
                .withStyle(ChatFormatting.ITALIC));

        // Описание (каждая строка отдельно)
        for (String line : spell.getDescription().split("\n")) {
            tooltip.add(Component.literal("§7" + line));
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§9◈ Мана: §b" + spell.getManaCost()));
        tooltip.add(Component.literal("§8⏱ Откат: §7" + spell.getCooldownSeconds() + " сек"));
        tooltip.add(Component.literal("§8Школа: §r" + spell.getSchool().displayName));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Предметы со свечением (как зачарованные) — визуальный эффект
        return true;
    }
}
