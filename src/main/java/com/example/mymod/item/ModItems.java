package com.example.mymod.item;

import com.example.mymod.MyMod;
import com.example.mymod.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Реестр всех предметов мода.
 *
 * Свитки заклинаний: правый клик → применить заклинание (если открыто в дереве).
 */
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MyMod.MOD_ID);

    // ─── Примеры (оставлены для совместимости) ────────────────────────────────

    public static final RegistryObject<Item> EXAMPLE_ITEM = ITEMS.register("example_item",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block",
            () -> new BlockItem(ModBlocks.EXAMPLE_BLOCK.get(), new Item.Properties()));

    // ─── Свитки заклинаний ────────────────────────────────────────────────────

    /** Свиток «Искра» (Багровый путь) — поджигает блок */
    public static final RegistryObject<Item> SCROLL_SPARK = ITEMS.register("scroll_spark",
            () -> new SpellScrollItem("spark",
                    new Item.Properties().stacksTo(1)));

    /** Свиток «Луч Святости» (Путь Тимофея) */
    public static final RegistryObject<Item> SCROLL_HOLY_RAY = ITEMS.register("scroll_holy_ray",
            () -> new SpellScrollItem("holy_ray",
                    new Item.Properties().stacksTo(1)));

    /** Свиток «Проклятье Ученика» (Путь Культиста) */
    public static final RegistryObject<Item> SCROLL_APPRENTICE_CURSE = ITEMS.register(
            "scroll_apprentice_curse",
            () -> new SpellScrollItem("apprentice_curse",
                    new Item.Properties().stacksTo(1)));

    /** Свиток «Призыв Курицы» (Путь Марионетки) */
    public static final RegistryObject<Item> SCROLL_SUMMON_CHICKEN = ITEMS.register(
            "scroll_summon_chicken",
            () -> new SpellScrollItem("summon_chicken",
                    new Item.Properties().stacksTo(1)));

    // ─── Регистрация ──────────────────────────────────────────────────────────

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
