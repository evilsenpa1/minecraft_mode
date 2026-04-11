package com.example.mymod.network;

import com.example.mymod.MyMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Регистрация сетевого канала мода.
 * Все пакеты клиент ↔ сервер проходят через этот канал.
 * Вызвать ModNetwork.register() один раз при загрузке мода.
 *
 * Таблица пакетов:
 *   ID 0 — ManaDataPacket       (сервер → клиент): синхронизация маны
 *   ID 1 — SkillSyncPacket      (сервер → клиент): навыки + слоты колеса
 *   ID 2 — LearnSkillPacket     (клиент → сервер): изучить ноду
 *   ID 3 — SpellSlotPacket      (клиент → сервер): назначить заклинание в слот
 *   ID 4 — CastSpellPacket      (клиент → сервер): применить активное заклинание
 */
public class ModNetwork {

    private static final String PROTOCOL_VERSION = "2";
    private static int packetId = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MyMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /**
     * Зарегистрировать все пакеты мода.
     * Порядок и ID должны совпадать на клиенте и сервере — не менять порядок.
     */
    public static void register() {
        // ID 0: Синхронизация маны сервер → клиент
        CHANNEL.registerMessage(
                packetId++,
                ManaDataPacket.class,
                ManaDataPacket::encode,
                ManaDataPacket::decode,
                ManaDataPacket::handle
        );

        // ID 1: Синхронизация навыков + слотов колеса сервер → клиент
        CHANNEL.registerMessage(
                packetId++,
                SkillSyncPacket.class,
                SkillSyncPacket::encode,
                SkillSyncPacket::decode,
                SkillSyncPacket::handle
        );

        // ID 2: Запрос изучения ноды клиент → сервер
        CHANNEL.registerMessage(
                packetId++,
                LearnSkillPacket.class,
                LearnSkillPacket::encode,
                LearnSkillPacket::decode,
                LearnSkillPacket::handle
        );

        // ID 3: Назначение заклинания в слот колеса клиент → сервер
        CHANNEL.registerMessage(
                packetId++,
                SpellSlotPacket.class,
                SpellSlotPacket::encode,
                SpellSlotPacket::decode,
                SpellSlotPacket::handle
        );

        // ID 4: Применение активного заклинания клиент → сервер
        CHANNEL.registerMessage(
                packetId++,
                CastSpellPacket.class,
                CastSpellPacket::encode,
                CastSpellPacket::decode,
                CastSpellPacket::handle
        );
    }
}
