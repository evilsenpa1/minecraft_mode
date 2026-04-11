package com.example.mymod;

import com.example.mymod.block.ModBlocks;
import com.example.mymod.config.ModConfig;
import com.example.mymod.item.ModItems;
import com.example.mymod.network.ModNetwork;
import com.example.mymod.tab.ModCreativeTabs;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MyMod.MOD_ID)
public class MyMod {

    public static final String MOD_ID = "mymod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MyMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрация блоков, предметов и вкладок
        ModCreativeTabs.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);

        // Регистрация клиентского конфига (файл mymod-client.toml в папке config/)
        ModLoadingContext.get().registerConfig(Type.CLIENT, ModConfig.SPEC, "mymod-client.toml");

        // Регистрация сетевых пакетов (клиент ↔ сервер)
        ModNetwork.register();

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("[{}] Mod loaded successfully!", MOD_ID);
    }
}
