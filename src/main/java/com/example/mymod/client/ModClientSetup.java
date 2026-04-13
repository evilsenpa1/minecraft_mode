package com.example.mymod.client;

import com.example.mymod.MyMod;
import com.example.mymod.client.render.SparkProjectileRenderer;
import com.example.mymod.entity.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиентские события на MOD bus.
 * Регистрация рендереров кастомных сущностей мода.
 *
 * Отдельный класс от ModClientForgeEvents: FORGE bus — игровые события,
 * MOD bus — события инициализации (рендереры, модели и т.п.).
 */
@Mod.EventBusSubscriber(modid = MyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientSetup {

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Снаряд «Искра» — кастомный рендерер с вращающимися кольцами и ядром
        event.registerEntityRenderer(
                ModEntities.SPARK_PROJECTILE.get(),
                SparkProjectileRenderer::new
        );
    }
}
