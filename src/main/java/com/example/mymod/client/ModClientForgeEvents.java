package com.example.mymod.client;

import com.example.mymod.MyMod;
import com.example.mymod.client.keybind.ModKeyBindings;
import com.example.mymod.client.screen.GrimoireScreen;
import com.example.mymod.client.screen.ModSettingsScreen;
import com.example.mymod.client.screen.SkillTreeScreen;
import com.example.mymod.client.screen.SpellWheelScreen;
import com.example.mymod.network.CastSpellPacket;
import com.example.mymod.network.ModNetwork;
import com.example.mymod.skill.IPlayerSkills;
import com.example.mymod.skill.PlayerSkillsCapability;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиентские события на FORGE bus.
 * Обрабатывает нажатия клавиш мода во время игры.
 *
 * G — открыть настройки мода
 * B — открыть Гримуар Знаний
 * P — открыть Древо Познания
 * R — открыть Колесо заклинаний
 * F — применить заклинание из активного слота
 */
@Mod.EventBusSubscriber(modid = MyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ModClientForgeEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        // G — настройки
        if (ModKeyBindings.OPEN_SETTINGS.consumeClick()) {
            mc.setScreen(new ModSettingsScreen());
            return;
        }

        // B — Гримуар
        if (ModKeyBindings.OPEN_GRIMOIRE.consumeClick()) {
            mc.setScreen(new GrimoireScreen());
            return;
        }

        // P — Древо Познания
        if (ModKeyBindings.OPEN_SKILL_TREE.consumeClick()) {
            mc.setScreen(new SkillTreeScreen());
            return;
        }

        // R — Колесо заклинаний (только при открытой ноде mana_core)
        if (ModKeyBindings.OPEN_SPELL_WHEEL.consumeClick()) {
            boolean manaActive = mc.player.getCapability(PlayerSkillsCapability.SKILLS)
                    .map(s -> s.isUnlocked("mana_core"))
                    .orElse(false);

            if (manaActive) {
                mc.setScreen(new SpellWheelScreen());
            } else {
                // Короткий намёк в chat без спама
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§8✗ Откройте §7«Пробуждение»§8 в Древе Познания."),
                        true // actionbar — не засоряет чат
                );
            }
            return;
        }

        // F — применить заклинание
        if (ModKeyBindings.CAST_SPELL.consumeClick()) {
            boolean manaActive = mc.player.getCapability(PlayerSkillsCapability.SKILLS)
                    .map(s -> s.isUnlocked("mana_core"))
                    .orElse(false);

            if (!manaActive) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§8✗ Система маны не пробуждена."),
                        true
                );
                return;
            }

            // Быстрая клиентская проверка: есть ли заклинание в активном слоте
            String spellId = mc.player.getCapability(PlayerSkillsCapability.SKILLS)
                    .map(s -> s.getSpellInSlot(s.getActiveSlot()))
                    .orElse(null);

            if (spellId == null) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§8✗ Активный слот пуст. §7[R] §8— колесо заклинаний."),
                        true
                );
                return;
            }

            // Всё ок — отправляем запрос на сервер
            ModNetwork.CHANNEL.sendToServer(new CastSpellPacket());
        }
    }
}
