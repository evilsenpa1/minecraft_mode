package com.example.mymod.network;

import com.example.mymod.MyMod;
import com.example.mymod.event.ModEvents;
import com.example.mymod.skill.IPlayerSkills;
import com.example.mymod.skill.PlayerSkillsCapability;
import com.example.mymod.skill.SkillNode;
import com.example.mymod.skill.SkillTree;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Пакет изучения ноды: клиент → сервер.
 *
 * Отправляется из SkillTreeScreen когда игрок нажимает "Изучить".
 * Сервер проверяет все условия и при успехе:
 *   1. Разблокирует ноду
 *   2. Списывает очки навыков
 *   3. Применяет специальные эффекты (ачивка, сообщение)
 *   4. Отправляет SkillSyncPacket обратно клиенту
 */
public class LearnSkillPacket {

    private final String nodeId;

    public LearnSkillPacket(String nodeId) {
        this.nodeId = nodeId;
    }

    // ─── Кодирование / декодирование ──────────────────────────────────────────

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nodeId);
    }

    public static LearnSkillPacket decode(FriendlyByteBuf buf) {
        return new LearnSkillPacket(buf.readUtf());
    }

    // ─── Обработка на сервере ─────────────────────────────────────────────────

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            SkillNode node = SkillTree.getById(nodeId);
            if (node == null) {
                MyMod.LOGGER.warn("[{}] Получен LearnSkillPacket с неизвестной нодой: {}",
                        MyMod.MOD_ID, nodeId);
                return;
            }

            player.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(skills ->
                    processLearn(player, node, skills)
            );
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Валидация и применение изучения ноды на сервере.
     * Порядок проверок: уже открыта → очки навыков → требования → выполнить.
     */
    private void processLearn(ServerPlayer player, SkillNode node, IPlayerSkills skills) {
        // Уже открыта?
        if (skills.isUnlocked(node.getId())) return;

        // Достаточно очков?
        if (skills.getSkillPoints() < node.getCost()) {
            player.sendSystemMessage(Component.literal(
                    "§c✗ Недостаточно очков навыков. Нужно: §e" + node.getCost()));
            return;
        }

        // Все требования выполнены?
        for (String req : node.getPrerequisites()) {
            if (!skills.isUnlocked(req)) {
                SkillNode reqNode = SkillTree.getById(req);
                String reqName = (reqNode != null) ? reqNode.getName() : req;
                player.sendSystemMessage(Component.literal(
                        "§c✗ Сначала изучите: §e" + reqName));
                return;
            }
        }

        // Всё ок — разблокируем ноду
        skills.unlockNode(node.getId());
        skills.setSkillPoints(skills.getSkillPoints() - node.getCost());

        // Сразу сохраняем резервную копию навыков в persistentData.
        // Это критично: резервная копия используется onPlayerClone при смерти
        // (LazyOptional capability инвалидируется раньше Clone-события).
        ModEvents.saveSkillsBackup(player);

        // Специальные эффекты конкретных нод
        onNodeUnlocked(player, node);

        // Синхронизируем обновлённые данные с клиентом (включая слоты колеса)
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
     * Побочные эффекты при разблокировке конкретных нод.
     * Расширяй по мере добавления новых нод с уникальными эффектами.
     */
    private void onNodeUnlocked(ServerPlayer player, SkillNode node) {
        switch (node.getId()) {
            case "mana_core" -> {
                // Выдать достижение "Озарение слепца"
                awardAdvancement(player, "ozarenie_slepca");

                // Красивое системное сообщение
                player.sendSystemMessage(Component.literal(
                        "§6✦ §eСистема маны пробуждена! §7Вы ощущаете поток магической энергии."));
            }
            case "crimson_gateway" ->
                    player.sendSystemMessage(Component.literal(
                            "§c✦ §4Врата Разрушения открыты. Огонь подчиняется вашей воле."));
            case "holy_gateway" ->
                    player.sendSystemMessage(Component.literal(
                            "§e✦ §6Врата Святости открыты. Свет нисходит на вас."));
            case "cultist_gateway" ->
                    player.sendSystemMessage(Component.literal(
                            "§5✦ §dВрата Тьмы открыты. Тёмная сила принимает вас."));
            case "puppet_gateway" ->
                    player.sendSystemMessage(Component.literal(
                            "§a✦ §2Врата Марионетки открыты. Нити судьбы в ваших руках."));
        }
    }

    /**
     * Выдать достижение игроку по имени (без ModID).
     * Тихо игнорирует если достижение не найдено или уже получено.
     */
    private void awardAdvancement(ServerPlayer player, String advancementName) {
        ResourceLocation id = new ResourceLocation(MyMod.MOD_ID, advancementName);
        var advancement = player.server.getAdvancements().getAdvancement(id);
        if (advancement == null) return;

        var progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) return;

        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }
}
