package com.example.mymod.event;

import com.example.mymod.MyMod;
import com.example.mymod.capability.IMana;
import com.example.mymod.capability.ManaCapability;
import com.example.mymod.capability.ManaProvider;
import com.example.mymod.network.ManaDataPacket;
import com.example.mymod.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Игровые события на FORGE bus.
 * Здесь живёт вся логика маны: привязка к игроку, регенерация, синхронизация.
 */
@Mod.EventBusSubscriber(modid = MyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEvents {

    /** ResourceLocation ключ для нашего capability — должен быть уникальным */
    private static final ResourceLocation MANA_CAP_KEY =
            new ResourceLocation(MyMod.MOD_ID, "mana_data");

    // ─── Привязка capability к игроку ────────────────────────────────────────

    /**
     * Вызывается каждый раз, когда к сущности присоединяются capability.
     * Мы добавляем ManaProvider только к игрокам.
     */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player)) return;

        ManaProvider provider = new ManaProvider();
        event.addCapability(MANA_CAP_KEY, provider);
        // При уничтожении сущности инвалидируем LazyOptional — освобождаем память
        event.addListener(provider::invalidate);
    }

    // ─── Регенерация и синхронизация маны ───────────────────────────────────

    /**
     * Тик игрока — здесь восстанавливаем ману и отправляем её клиенту.
     *
     * Регенерация: 5 единиц в секунду = 1 единица каждые 4 тика (при 20 тиках/сек).
     * Синхронизация: отправляем пакет каждые 4 тика только если мана изменилась.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Обрабатываем только в конце тика и только на сервере
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;

        Player player = event.player;

        player.getCapability(ManaCapability.MANA).ifPresent(mana -> {
            // Восстанавливаем 1 единицу маны каждые 4 тика = 5/сек
            if (player.tickCount % 4 == 0 && mana.getMana() < mana.getMaxMana()) {
                mana.addMana(1);
                // Синхронизируем изменение с клиентом
                syncManaToClient(player, mana);
            }
        });
    }

    // ─── Копирование маны при перерождении / смене измерения ────────────────

    /**
     * Вызывается при смерти игрока и возрождении (или смене измерения).
     * Копируем ману от старого игрока к новому, иначе она обнулится.
     *
     * keepInventory = true → мана сохраняется полностью.
     * keepInventory = false → сбрасываем ману до нуля (как потеря прогресса).
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player cloned   = event.getEntity();

        // Разрешаем доступ к capability мёртвого игрока
        original.reviveCaps();

        original.getCapability(ManaCapability.MANA).ifPresent(oldMana -> {
            cloned.getCapability(ManaCapability.MANA).ifPresent(newMana -> {
                if (event.isWasDeath()) {
                    // Смерть: можно либо сбросить ману, либо сохранить
                    // Пока сохраняем — потом можно изменить по балансу
                    newMana.setMana(oldMana.getMana());
                } else {
                    // Смена измерения — всегда копируем
                    newMana.setMana(oldMana.getMana());
                }
            });
        });

        original.invalidateCaps();
    }

    /**
     * Вызывается когда игрок заходит на сервер.
     * Отправляем начальный пакет синхронизации, чтобы клиент знал свою ману.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        serverPlayer.getCapability(ManaCapability.MANA).ifPresent(mana ->
                syncManaToClient(serverPlayer, mana)
        );
    }

    /**
     * Вызывается после возрождения игрока.
     * Снова синхронизируем, чтобы HUD показал актуальные данные.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        serverPlayer.getCapability(ManaCapability.MANA).ifPresent(mana ->
                syncManaToClient(serverPlayer, mana)
        );
    }

    // ─── Вспомогательный метод синхронизации ─────────────────────────────────

    /**
     * Отправить пакет с текущей маной конкретному игроку-клиенту.
     * Вызывай этот метод из любого серверного кода при изменении маны.
     *
     * @param player игрок (должен быть ServerPlayer, иначе пакет не отправится)
     * @param mana   данные маны для отправки
     */
    public static void syncManaToClient(Player player, IMana mana) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> serverPlayer),
                new ManaDataPacket(mana.getMana(), mana.getMaxMana())
        );
    }

    // ─── Старый обработчик (оставлен для примера) ────────────────────────────

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) {
            MyMod.LOGGER.debug("Player {} right-clicked a block at {}",
                    player.getName().getString(), event.getPos());
        }
    }
}
