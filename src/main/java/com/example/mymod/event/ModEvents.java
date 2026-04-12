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
import net.minecraft.network.chat.Component;
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
     *   1. Тик кулдаунов заклинаний
     *   2. Регенерация маны (базовая + бонус от открытых пассивных нод)
     *   3. (раз в 20 тиков) Проверка повышения уровня → начисление очков навыков
     *   4. (раз в 200 тиков) Резервное сохранение навыков в persistentData
     *
     * Базовая регенерация: 5 единиц/сек = 1 каждые 4 тика.
     * Бонусная: +2/сек за каждую открытую пассивную ноду.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;

        Player player = event.player;

        // Тик кулдаунов заклинаний (каждый тик)
        player.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(IPlayerSkills::tickCooldowns);

        // Проверка повышения уровня (раз в секунду)
        if (player.tickCount % 20 == 0 && player instanceof ServerPlayer serverPlayer) {
            checkLevelUp(serverPlayer);
        }

        // Резервное сохранение навыков в persistentData (раз в 10 секунд)
        // Страховка на случай смерти до автосохранения мира.
        if (player.tickCount % 200 == 0) {
            saveSkillsBackup(player);
        }

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

    /**
     * Проверяет, повысился ли уровень опыта игрока с момента последней проверки.
     * При повышении начисляет +1 очко навыков за каждый полученный уровень.
     *
     * Использует lastKnownLevel из capability:
     *   -1  → первый запуск (просто запоминаем уровень, очки не выдаём)
     *   >= 0 → сравниваем, при росте начисляем
     */
    private static void checkLevelUp(ServerPlayer player) {
        player.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(skills -> {
            int currentLevel = player.experienceLevel;
            int lastLevel = skills.getLastKnownLevel();

            if (lastLevel < 0) {
                // Первый тик после установки мода — запоминаем уровень без начисления
                skills.setLastKnownLevel(currentLevel);
                return;
            }

            if (currentLevel > lastLevel) {
                // Игрок получил один или несколько уровней
                int gained = currentLevel - lastLevel;
                skills.addSkillPoints(gained);
                skills.setLastKnownLevel(currentLevel);

                // Синхронизируем дерево с клиентом
                syncSkillsToClient(player, skills);

                // Уведомление в actionbar — не засоряет чат
                player.displayClientMessage(
                        Component.literal("§6✦ §eОчки Познания: §a+" + gained
                                + " §7(уровень " + currentLevel + ")  "
                                + "§eВсего: §a" + skills.getSkillPoints()),
                        true
                );
            } else if (currentLevel < lastLevel) {
                // Уровень уменьшился (смерть с keepInventory=false или команда).
                // Очки не отбираем — просто сбрасываем счётчик.
                skills.setLastKnownLevel(currentLevel);
            }
        });
    }

    // ─── Копирование capability при смерти/смене измерения ───────────────────

    /**
     * Копирует capability маны и навыков со старой сущности игрока на новую.
     * Вызывается при смерти (wasDeath=true) и смене измерения (wasDeath=false).
     *
     * ПРОБЛЕМА: Forge регистрирует addListener(provider::invalidate) на AttachCapabilitiesEvent.
     * При удалении сущности (смерти) этот listener срабатывает ДО PlayerEvent.Clone,
     * инвалидируя LazyOptional. После этого reviveCaps() не восстанавливает LazyOptional —
     * он уже мёртв. Поэтому getCapability() возвращает empty и навыки не копируются.
     *
     * РЕШЕНИЕ: двухслойный механизм:
     *   1. Основной — через capability (работает при смене измерения и иногда при смерти).
     *   2. Резервный — через persistentData (сохраняется в onPlayerTick и LearnSkillPacket).
     *      persistentData копируется вручную здесь, т.к. Forge не делает это автоматически.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player cloned   = event.getEntity();

        original.reviveCaps();

        // ── Мана ──────────────────────────────────────────────────────────────
        original.getCapability(ManaCapability.MANA).ifPresent(oldMana ->
                cloned.getCapability(ManaCapability.MANA).ifPresent(newMana ->
                        newMana.setMana(oldMana.getMana())
                )
        );

        // ── Навыки: основной путь через capability ─────────────────────────
        boolean[] copiedViaCapability = { false };

        original.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(oldSkills -> {
            CompoundTag skillsNbt = oldSkills.serializeNBT();
            cloned.getCapability(PlayerSkillsCapability.SKILLS)
                  .ifPresent(newSkills -> {
                      newSkills.deserializeNBT(skillsNbt);
                      copiedViaCapability[0] = true;
                  });
        });

        // ── Навыки: резервный путь через persistentData ────────────────────
        // Используется когда LazyOptional original уже инвалидирован до Clone-события.
        if (!copiedViaCapability[0]) {
            CompoundTag backup = original.getPersistentData()
                    .getCompound(PlayerSkillsCapability.SKILLS_BACKUP_KEY);
            if (!backup.isEmpty()) {
                cloned.getCapability(PlayerSkillsCapability.SKILLS)
                      .ifPresent(skills -> skills.deserializeNBT(backup));
                MyMod.LOGGER.info("[{}] Навыки восстановлены из резервной копии persistentData.",
                        MyMod.MOD_ID);
            } else {
                MyMod.LOGGER.warn("[{}] onPlayerClone: capability и резервная копия недоступны — " +
                        "навыки сброшены. Убедитесь что игрок хотя бы раз тикнул на сервере.",
                        MyMod.MOD_ID);
            }
        }

        // ── Копируем persistentData (включая резервную копию навыков) ─────
        // Forge не переносит persistentData автоматически на новую сущность.
        CompoundTag originalPersistent = original.getPersistentData();
        if (!originalPersistent.isEmpty()) {
            cloned.getPersistentData().merge(originalPersistent);
        }

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
     * Синхронизировать навыки игрока с клиентом.
     * Вызывается после начисления очков навыков и из LearnSkillPacket.
     */
    public static void syncSkillsToClient(ServerPlayer player, IPlayerSkills skills) {
        String[] slots = new String[IPlayerSkills.SPELL_SLOTS];
        for (int i = 0; i < IPlayerSkills.SPELL_SLOTS; i++) {
            slots[i] = skills.getSpellInSlot(i);
        }
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SkillSyncPacket(skills.getSkillPoints(), skills.getUnlockedNodes(),
                        slots, skills.getActiveSlot())
        );
    }

    /**
     * Сохранить резервную копию навыков в persistentData игрока.
     *
     * persistentData хранится отдельно от capability и не зависит от LazyOptional.
     * Именно отсюда onPlayerClone берёт данные если capability инвалидирован до события.
     *
     * Вызывается:
     *   - каждые 200 тиков (onPlayerTick)
     *   - сразу после разблокировки ноды (LearnSkillPacket)
     *   - при выходе из игры (onPlayerLogout)
     */
    public static void saveSkillsBackup(Player player) {
        player.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(skills ->
                player.getPersistentData().put(
                        PlayerSkillsCapability.SKILLS_BACKUP_KEY,
                        skills.serializeNBT()
                )
        );
    }

    /**
     * Синхронизировать все данные игрока с клиентом (мана + навыки).
     */
    private static void syncAllToClient(ServerPlayer player) {
        player.getCapability(ManaCapability.MANA).ifPresent(mana ->
                syncManaToClient(player, mana)
        );
        player.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(skills ->
                syncSkillsToClient(player, skills)
        );
    }

    // ─── Сохранение резервной копии при выходе ───────────────────────────────

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Сохраняем актуальную копию навыков в persistentData перед отключением.
        // Это гарантирует что резервная копия свежая даже если автосохранение
        // мира не успело сработать.
        saveSkillsBackup(event.getEntity());
    }
}
