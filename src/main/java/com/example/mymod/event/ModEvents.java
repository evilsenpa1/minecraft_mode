package com.example.mymod.event;

import com.example.mymod.MyMod;
import com.example.mymod.capability.IMana;
import com.example.mymod.capability.ManaCapability;
import com.example.mymod.capability.ManaProvider;
import com.example.mymod.network.ManaDataPacket;
import com.example.mymod.network.ModNetwork;
import com.example.mymod.network.SkillSyncPacket;
import com.example.mymod.skill.IPlayerSkills;
import com.example.mymod.skill.PlayerSkillsCapability;
import com.example.mymod.skill.PlayerSkillsProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Игровые события на FORGE bus.
 *
 * Отвечает за:
 *   - Привязку capability маны и навыков к игрокам
 *   - Регенерацию маны (базовая + бонусная от пассивных нод)
 *   - Тик кулдаунов заклинаний
 *   - Синхронизацию данных с клиентом
 *   - Копирование capability при смерти/смене измерения
 */
@Mod.EventBusSubscriber(modid = MyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEvents {

    private static final ResourceLocation MANA_CAP_KEY   =
            new ResourceLocation(MyMod.MOD_ID, "mana_data");
    private static final ResourceLocation SKILLS_CAP_KEY =
            new ResourceLocation(MyMod.MOD_ID, "skills_data");

    // ─── Привязка capability к игроку ────────────────────────────────────────

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player)) return;

        // Мана
        ManaProvider manaProvider = new ManaProvider();
        event.addCapability(MANA_CAP_KEY, manaProvider);
        event.addListener(manaProvider::invalidate);

        // Навыки (дерево прокачки, кулдауны заклинаний)
        PlayerSkillsProvider skillsProvider = new PlayerSkillsProvider();
        event.addCapability(SKILLS_CAP_KEY, skillsProvider);
        event.addListener(skillsProvider::invalidate);
    }

    // ─── Серверный тик игрока ────────────────────────────────────────────────

    /**
     * Каждый тик игрока на сервере:
     *   1. Регенерация маны (базовая + бонус от открытых пассивных нод)
     *   2. Тик кулдаунов заклинаний
     *   3. Синхронизация маны с клиентом
     *
     * Базовая регенерация: 5 единиц/сек = 1 каждые 4 тика.
     * Бонусная: +2/сек за каждую открытую пассивную ноду.
     * Итоговая (4 ноды открыты): 5 + 8 = 13/сек.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;

        Player player = event.player;

        // Тик кулдаунов заклинаний
        player.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(IPlayerSkills::tickCooldowns);

        player.getCapability(ManaCapability.MANA).ifPresent(mana -> {
            if (mana.getMana() >= mana.getMaxMana()) return;

            // Регенерация только при открытой центральной ноде (мана активирована)
            boolean manaActive = player.getCapability(PlayerSkillsCapability.SKILLS)
                    .map(s -> s.isUnlocked("mana_core"))
                    .orElse(false);
            if (!manaActive) return;

            // Базовая регенерация: 1 ед. каждые 4 тика
            boolean baseTick = player.tickCount % 4 == 0;

            // Бонусная регенерация: 1 ед. за каждые 10 тиков per бонус-очко
            // +2/сек = +1 каждые 10 тиков; но для простоты — добавляем bonus/сек сразу
            int bonus = player.getCapability(PlayerSkillsCapability.SKILLS)
                    .map(IPlayerSkills::getBonusManaRegen)
                    .orElse(0);

            boolean bonusTick = bonus > 0 && player.tickCount % 20 == 0;

            if (baseTick) {
                mana.addMana(1);
                syncManaToClient(player, mana);
            } else if (bonusTick) {
                mana.addMana(bonus);
                syncManaToClient(player, mana);
            }
        });
    }

    // ─── Копирование capability при смерти/смене измерения ───────────────────

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player cloned   = event.getEntity();

        original.reviveCaps();

        // Копируем ману
        original.getCapability(ManaCapability.MANA).ifPresent(oldMana ->
                cloned.getCapability(ManaCapability.MANA).ifPresent(newMana ->
                        newMana.setMana(oldMana.getMana())
                )
        );

        // Копируем навыки + слоты колеса через NBT — единственный надёжный способ.
        // syncFromServer() предназначен для клиентской синхронизации; здесь оба игрока
        // на сервере, и LazyOptional у original после reviveCaps() может вести себя
        // непредсказуемо при прямом переносе объектов.
        original.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(oldSkills -> {
            CompoundTag skillsNbt = oldSkills.serializeNBT();
            cloned.getCapability(PlayerSkillsCapability.SKILLS)
                  .ifPresent(newSkills -> newSkills.deserializeNBT(skillsNbt));
        });

        original.invalidateCaps();
    }

    // ─── Синхронизация при входе в мир ───────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        syncAllToClient(serverPlayer);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        syncAllToClient(serverPlayer);
    }

    // ─── Вспомогательные методы синхронизации ────────────────────────────────

    /**
     * Синхронизировать ману с клиентом конкретного игрока.
     * Вызывается из SpellScrollItem и других мест при изменении маны.
     */
    public static void syncManaToClient(Player player, IMana mana) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> serverPlayer),
                new ManaDataPacket(mana.getMana(), mana.getMaxMana())
        );
    }

    /**
     * Синхронизировать все данные игрока с клиентом (мана + навыки).
     */
    private static void syncAllToClient(ServerPlayer player) {
        player.getCapability(ManaCapability.MANA).ifPresent(mana ->
                syncManaToClient(player, mana)
        );
        player.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(skills -> {
            String[] slots = new String[IPlayerSkills.SPELL_SLOTS];
            for (int i = 0; i < IPlayerSkills.SPELL_SLOTS; i++) {
                slots[i] = skills.getSpellInSlot(i);
            }
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SkillSyncPacket(skills.getSkillPoints(), skills.getUnlockedNodes(),
                            slots, skills.getActiveSlot())
            );
        });
    }
}
