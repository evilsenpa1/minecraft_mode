package com.example.mymod.client;

import com.example.mymod.MyMod;
import com.example.mymod.client.keybind.ModKeyBindings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиентские события на MOD bus.
 * Dist.CLIENT — никогда не загружается на выделенном сервере.
 *
 * Сюда идут: регистрация оверлеев, клавиш, рендереров — всё что
 * регистрируется один раз во время инициализации мода.
 */
@Mod.EventBusSubscriber(modid = MyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientEvents {

    /** Зарегистрировать HUD-оверлей маны */
    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        ManaOverlay.register(event);
    }

    /** Зарегистрировать клавиши мода в системе управления Minecraft */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ModKeyBindings.register(event);
    }
}
