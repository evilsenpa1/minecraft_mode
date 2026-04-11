package com.example.mymod.effect;

import com.example.mymod.MyMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Реестр кастомных эффектов мода.
 * Регистрируется в MyMod через ModEffects.register(modEventBus).
 */
public class ModEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MyMod.MOD_ID);

    // ─── Эффекты ──────────────────────────────────────────────────────────────

    /**
     * Простое проклятье — наносит 1 HP урона в секунду.
     * Применяется заклинанием «Проклятье ученика».
     */
    public static final RegistryObject<MobEffect> SIMPLE_CURSE =
            MOB_EFFECTS.register("simple_curse", SimpleCurseEffect::new);

    // ─── Регистрация ──────────────────────────────────────────────────────────

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}
