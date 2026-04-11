package com.example.mymod.spell;

import net.minecraft.server.level.ServerPlayer;

/**
 * Абстрактный базовый класс заклинания.
 *
 * Каждое заклинание имеет: уникальный ID, школу, стоимость маны, кулдаун.
 * Конкретные реализации кладутся в пакет impl/ и регистрируются в SpellRegistry.
 *
 * Метод cast() выполняется только на сервере — нельзя вызывать на клиенте напрямую.
 */
public abstract class Spell {

    private final String id;
    private final MagicSchool school;
    private final int manaCost;
    /** Кулдаун в тиках. 20 тиков = 1 секунда. */
    private final int cooldownTicks;

    protected Spell(String id, MagicSchool school, int manaCost, int cooldownTicks) {
        this.id           = id;
        this.school       = school;
        this.manaCost     = manaCost;
        this.cooldownTicks = cooldownTicks;
    }

    // ─── Абстрактные методы (реализуют конкретные заклинания) ────────────────

    /**
     * Применить эффект заклинания.
     * Вызывается на сервере после успешной проверки маны и кулдауна.
     *
     * @param player игрок, кастующий заклинание
     */
    public abstract void cast(ServerPlayer player);

    /** Отображаемое название для UI */
    public abstract String getDisplayName();

    /** Описание механики для UI */
    public abstract String getDescription();

    // ─── Геттеры ─────────────────────────────────────────────────────────────

    public String getId()          { return id; }
    public MagicSchool getSchool() { return school; }
    public int getManaCost()       { return manaCost; }
    public int getCooldownTicks()  { return cooldownTicks; }

    /** Кулдаун в секундах (для отображения в UI) */
    public int getCooldownSeconds() { return cooldownTicks / 20; }
}
