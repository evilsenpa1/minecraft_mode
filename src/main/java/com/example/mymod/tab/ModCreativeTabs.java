package com.example.mymod.tab;

import com.example.mymod.MyMod;
import com.example.mymod.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MyMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MYMOD_TAB = CREATIVE_MODE_TABS.register("mymod_tab",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> new ItemStack(ModItems.EXAMPLE_ITEM.get()))
                    .title(Component.translatable("itemGroup.mymod.mymod_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.EXAMPLE_ITEM.get());
                        output.accept(ModItems.EXAMPLE_BLOCK_ITEM.get());
                        // Свитки заклинаний
                        output.accept(ModItems.SCROLL_SPARK.get());
                        output.accept(ModItems.SCROLL_HOLY_RAY.get());
                        output.accept(ModItems.SCROLL_APPRENTICE_CURSE.get());
                        output.accept(ModItems.SCROLL_SUMMON_CHICKEN.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
