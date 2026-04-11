package com.example.mymod.network;

import com.example.mymod.MyMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Регистрация сетевого канала мода.
 * Все пакеты клиент ↔ сервер проходят через этот канал.
 * Вызвать ModNetwork.register() один раз при загрузке мода.
 */
public class ModNetwork {

    /** Версия протокола — при несовпадении клиент/сервер не соединятся */
    private static final String PROTOCOL_VERSION = "1";

    /** Счётчик ID пакетов — каждый пакет получает уникальный номер */
    private static int packetId = 0;

    /** Основной канал мода для отправки/приёма пакетов */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MyMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,  // проверка версии на клиенте
            PROTOCOL_VERSION::equals   // проверка версии на сервере
    );

    /**
     * Зарегистрировать все пакеты.
     * Порядок регистрации важен — ID присваивается последовательно.
     */
    public static void register() {
        // Пакет синхронизации маны: сервер → клиент
        CHANNEL.registerMessage(
                packetId++,
                ManaDataPacket.class,
                ManaDataPacket::encode,
                ManaDataPacket::decode,
                ManaDataPacket::handle
        );
    }
}
