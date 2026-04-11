package com.example.mymod.network;

import com.example.mymod.capability.ManaCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Пакет синхронизации маны: сервер → клиент.
 *
 * Сервер отправляет текущую и максимальную ману.
 * Клиент применяет данные к своей локальной копии capability игрока.
 * Это нужно потому, что capability хранится только на сервере;
 * клиент видит данные исключительно через такие пакеты.
 */
public class ManaDataPacket {

    private final int mana;
    private final int maxMana;

    public ManaDataPacket(int mana, int maxMana) {
        this.mana = mana;
        this.maxMana = maxMana;
    }

    // ─── Сериализация ────────────────────────────────────────────────────────

    /** Записать данные пакета в байт-буфер (сторона отправителя) */
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mana);
        buf.writeVarInt(maxMana);
    }

    /** Прочитать данные пакета из байт-буфера (сторона получателя) */
    public static ManaDataPacket decode(FriendlyByteBuf buf) {
        return new ManaDataPacket(buf.readVarInt(), buf.readVarInt());
    }

    // ─── Обработка на клиенте ────────────────────────────────────────────────

    /**
     * Применить пришедшие данные.
     * enqueueWork гарантирует выполнение в главном потоке, а не в сетевом.
     */
    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            mc.player.getCapability(ManaCapability.MANA).ifPresent(manaData -> {
                manaData.setMana(mana);
                // maxMana пока константа, но на будущее — можно расширить IMana
            });
        });
        ctx.setPacketHandled(true);
    }
}
