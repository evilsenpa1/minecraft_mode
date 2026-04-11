package com.example.mymod.entity;

import com.example.mymod.MyMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Реестр кастомных сущностей мода.
 * Регистрируется через DeferredRegister на MOD bus в MyMod.java.
 */
public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MyMod.MOD_ID);

    // Снаряд заклинания «Искра» — огненный шар Багрового пути
    public static final RegistryObject<EntityType<SparkProjectile>> SPARK_PROJECTILE =
            ENTITY_TYPES.register("spark_projectile", () ->
                    EntityType.Builder.<SparkProjectile>of(SparkProjectile::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)          // хитбокс снаряда
                            .clientTrackingRange(4)      // дальность трекинга клиентом (в чанках)
                            .updateInterval(1)           // обновлять позицию каждый тик
                            .build("spark_projectile")
            );
}
