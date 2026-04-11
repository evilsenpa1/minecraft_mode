package com.example.mymod.client;

import com.example.mymod.MyMod;
import com.example.mymod.client.keybind.ModKeyBindings;
import com.example.mymod.client.screen.GrimoireScreen;
import com.example.mymod.client.screen.ModSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиентские события на FORGE bus.
 * Dist.CLIENT — никогда не загружается на выделенном сервере.
 *
 * Сюда идут игровые события клиента: тики, нажатия клавиш,
 * клиентские взаимодействия — всё что происходит во время игры.
 */
@Mod.EventBusSubscriber(modid = MyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ModClientForgeEvents {

    /**
     * Проверяем нажатие клавиши каждый клиентский тик.
     *
     * consumeClick() возвращает true ровно один раз за нажатие
     * и сбрасывает счётчик — защита от срабатывания несколько раз подряд.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // Обрабатываем только в конце тика
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();

        // Открываем меню только если игрок в мире и никакое другое меню не открыто
        if (mc.player != null && mc.screen == null) {
            if (ModKeyBindings.OPEN_SETTINGS.consumeClick()) {
                mc.setScreen(new ModSettingsScreen());
            }
            if (ModKeyBindings.OPEN_GRIMOIRE.consumeClick()) {
                mc.setScreen(new GrimoireScreen());
            }
        }
    }
}
