package com.example.mymod.event;

import com.example.mymod.MyMod;
import com.example.mymod.capability.IMana;
import com.example.mymod.skill.IPlayerSkills;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * События инициализации мода на MOD bus.
 * Регистрация capability — обязательна, иначе getCapability() вернёт empty().
 */
@Mod.EventBusSubscriber(modid = MyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModSetupEvents {

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // Capability маны
        event.register(IMana.class);
        // Capability навыков (дерево прокачки)
        event.register(IPlayerSkills.class);

        MyMod.LOGGER.info("[{}] Capabilities registered: IMana, IPlayerSkills.", MyMod.MOD_ID);
    }
}
