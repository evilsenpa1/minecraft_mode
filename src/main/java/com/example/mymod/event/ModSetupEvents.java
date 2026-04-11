package com.example.mymod.event;

import com.example.mymod.MyMod;
import com.example.mymod.capability.IMana;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/**
 * События инициализации мода на MOD bus.
 * Сюда идут события жизненного цикла: регистрация capability,
 * настройка сети и т.п.
 */
@Mod.EventBusSubscriber(modid = MyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModSetupEvents {

    /**
     * Зарегистрировать capability маны.
     * Без этого вызова getCapability() будет всегда возвращать LazyOptional.empty().
     */
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IMana.class);
        MyMod.LOGGER.info("[{}] Mana capability registered.", MyMod.MOD_ID);
    }
}
