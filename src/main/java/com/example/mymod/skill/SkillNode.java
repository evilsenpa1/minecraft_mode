package com.example.mymod.skill;

import com.example.mymod.spell.MagicSchool;

import java.util.List;

/**
 * Нода (узел) дерева прокачки.
 *
 * Каждая нода имеет позицию в системе координат дерева (0,0 = центр),
 * тип, принадлежность школе, список требований (пресети) и стоимость.
 *
 * Для создания нод используй Builder — конструктор приватный.
 */
public class SkillNode {

    // ─── Типы нод ─────────────────────────────────────────────────────────────

    /**
     * Тип ноды определяет её визуальное отображение и роль в дереве.
     */
    public enum NodeType {
        /** Большая центральная нода — стартовая точка, открывает всю систему */
        CENTRAL,
        /** Нода-ворота школы — средняя, открывает ветку */
        GATEWAY,
        /** Пассивная нода — маленькая, даёт постоянный бонус */
        PASSIVE,
        /** Нода заклинания — особое оформление, открывает заклинание */
        SPELL
    }

    // ─── Поля ────────────────────────────────────────────────────────────────

    private final String id;
    private final String name;
    private final String description;
    private final NodeType type;
    private final MagicSchool school;

    /** Позиция в системе координат дерева (0,0 — центр экрана) */
    private final float x;
    private final float y;

    /** Ноды, которые должны быть открыты перед этой нодой */
    private final List<String> prerequisites;

    /** Стоимость в очках навыков */
    private final int cost;

    /**
     * ID заклинания в SpellRegistry (только для нод типа SPELL).
     * null для остальных типов.
     */
    private final String spellId;

    /**
     * Описание пассивного бонуса (только для нод типа PASSIVE).
     * null для остальных типов.
     */
    private final String bonusDescription;

    // ─── Конструктор (только через Builder) ──────────────────────────────────

    private SkillNode(Builder b) {
        this.id               = b.id;
        this.name             = b.name;
        this.description      = b.description;
        this.type             = b.type;
        this.school           = b.school;
        this.x                = b.x;
        this.y                = b.y;
        this.prerequisites    = List.copyOf(b.prerequisites);
        this.cost             = b.cost;
        this.spellId          = b.spellId;
        this.bonusDescription = b.bonusDescription;
    }

    // ─── Геттеры ──────────────────────────────────────────────────────────────

    public String getId()               { return id; }
    public String getName()             { return name; }
    public String getDescription()      { return description; }
    public NodeType getType()           { return type; }
    public MagicSchool getSchool()      { return school; }
    public float getX()                 { return x; }
    public float getY()                 { return y; }
    public List<String> getPrerequisites() { return prerequisites; }
    public int getCost()                { return cost; }
    public String getSpellId()          { return spellId; }
    public String getBonusDescription() { return bonusDescription; }

    // ─── Builder ─────────────────────────────────────────────────────────────

    public static class Builder {
        private final String id;
        private String name             = "";
        private String description      = "";
        private NodeType type           = NodeType.PASSIVE;
        private MagicSchool school      = MagicSchool.NONE;
        private float x                 = 0;
        private float y                 = 0;
        private List<String> prerequisites = List.of();
        private int cost                = 1;
        private String spellId          = null;
        private String bonusDescription = null;

        public Builder(String id) { this.id = id; }

        public Builder name(String n)               { this.name = n; return this; }
        public Builder description(String d)        { this.description = d; return this; }
        public Builder type(NodeType t)             { this.type = t; return this; }
        public Builder school(MagicSchool s)        { this.school = s; return this; }
        public Builder pos(float x, float y)        { this.x = x; this.y = y; return this; }
        public Builder requires(String... ids)      { this.prerequisites = List.of(ids); return this; }
        public Builder cost(int c)                  { this.cost = c; return this; }
        public Builder spell(String spellId)        { this.spellId = spellId; return this; }
        public Builder bonus(String desc)           { this.bonusDescription = desc; return this; }

        public SkillNode build() { return new SkillNode(this); }
    }
}
