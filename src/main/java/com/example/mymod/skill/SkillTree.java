package com.example.mymod.skill;

import com.example.mymod.spell.MagicSchool;

import java.util.*;

/**
 * Дерево прокачки — статическое хранилище всех нод и связей.
 *
 * Макет: центральная нода + 4 ветки школ по сторонам (аналог Path of Exile).
 * Координаты нод — в "мировом" пространстве дерева; центр (0,0).
 *
 * Топология:
 *
 *              [HOLY_GATEWAY]
 *                   |
 *              [HOLY_REGEN]
 *                   |
 *              [HOLY_SPELL]
 *
 * [CRIMSON_SPELL]-[CRIMSON_REGEN]-[CRIMSON_GW]-[MANA_CORE]-[CULTIST_GW]-[CULTIST_REGEN]-[CULTIST_SPELL]
 *
 *              [PUPPET_GATEWAY]
 *                   |
 *              [PUPPET_REGEN]
 *                   |
 *              [PUPPET_SPELL]
 */
public class SkillTree {

    // Расстояния между нодами в единицах дерева
    private static final float GATEWAY_DIST = 200f; // центр → ворота школы
    private static final float PASSIVE_DIST = 310f; // центр → пассивная нода
    private static final float SPELL_DIST   = 420f; // центр → нода заклинания

    // ─── Карта и список всех нод ──────────────────────────────────────────────

    private static final Map<String, SkillNode> NODES_MAP  = new LinkedHashMap<>();
    private static final List<SkillNode>         NODES_LIST = new ArrayList<>();

    // ─── Центральная нода ────────────────────────────────────────────────────

    /** Пробуждение — открывает систему маны, даёт ачивку "Озарение слепца" */
    public static final SkillNode MANA_CORE = reg(new SkillNode.Builder("mana_core")
            .name("Пробуждение")
            .description("Ваш разум открывается потоку маны.\n"
                    + "Открывает систему маны.\n"
                    + "Ачивка: «Озарение слепца».")
            .type(SkillNode.NodeType.CENTRAL)
            .school(MagicSchool.NONE)
            .pos(0, 0)
            .cost(1)
            .build());

    // ─── Багровый путь (слева, -X) ──────────────────────────────────────────

    public static final SkillNode CRIMSON_GATEWAY = reg(new SkillNode.Builder("crimson_gateway")
            .name("Врата Разрушения")
            .description("Путь огня и молний открывается перед тобой.\n"
                    + "Открывает ветку Багрового пути.")
            .type(SkillNode.NodeType.GATEWAY)
            .school(MagicSchool.CRIMSON)
            .pos(-GATEWAY_DIST, 0)
            .requires("mana_core")
            .cost(1)
            .build());

    public static final SkillNode CRIMSON_REGEN = reg(new SkillNode.Builder("crimson_regen")
            .name("Огненное дыхание")
            .description("Жар разрушения питает разум.\n"
                    + "+2 к регенерации маны в секунду.")
            .type(SkillNode.NodeType.PASSIVE)
            .school(MagicSchool.CRIMSON)
            .pos(-PASSIVE_DIST, 0)
            .requires("crimson_gateway")
            .cost(1)
            .bonus("+2 реген. маны/сек")
            .build());

    public static final SkillNode CRIMSON_SPARK = reg(new SkillNode.Builder("crimson_spark")
            .name("Искра")
            .description("Поджигает блок перед тобой — как огниво.\n"
                    + "Мана: 10  ·  Откат: 5 сек\n"
                    + "«Дыма без огня не бывает»")
            .type(SkillNode.NodeType.SPELL)
            .school(MagicSchool.CRIMSON)
            .pos(-SPELL_DIST, 0)
            .requires("crimson_regen")
            .cost(1)
            .spell("spark")
            .build());

    // ─── Путь Тимофея (вверх, -Y) ────────────────────────────────────────────

    public static final SkillNode HOLY_GATEWAY = reg(new SkillNode.Builder("holy_gateway")
            .name("Врата Святости")
            .description("Свет благодати нисходит на избранного.\n"
                    + "Открывает ветку Пути Тимофея.")
            .type(SkillNode.NodeType.GATEWAY)
            .school(MagicSchool.HOLY)
            .pos(0, -GATEWAY_DIST)
            .requires("mana_core")
            .cost(1)
            .build());

    public static final SkillNode HOLY_REGEN = reg(new SkillNode.Builder("holy_regen")
            .name("Благодать Тимофея")
            .description("Молитва восполняет запасы силы.\n"
                    + "+2 к регенерации маны в секунду.")
            .type(SkillNode.NodeType.PASSIVE)
            .school(MagicSchool.HOLY)
            .pos(0, -PASSIVE_DIST)
            .requires("holy_gateway")
            .cost(1)
            .bonus("+2 реген. маны/сек")
            .build());

    public static final SkillNode HOLY_RAY = reg(new SkillNode.Builder("holy_ray")
            .name("Луч Святости")
            .description("Луч чистого света карает нечисть.\n"
                    + "5 урона монстрам · 2 урона прочим.\n"
                    + "Мана: 15  ·  Откат: 10 сек")
            .type(SkillNode.NodeType.SPELL)
            .school(MagicSchool.HOLY)
            .pos(0, -SPELL_DIST)
            .requires("holy_regen")
            .cost(1)
            .spell("holy_ray")
            .build());

    // ─── Путь Культиста (справа, +X) ─────────────────────────────────────────

    public static final SkillNode CULTIST_GATEWAY = reg(new SkillNode.Builder("cultist_gateway")
            .name("Врата Тьмы")
            .description("Тёмная магия принимает нового ученика.\n"
                    + "Открывает ветку Пути Культиста.")
            .type(SkillNode.NodeType.GATEWAY)
            .school(MagicSchool.CULTIST)
            .pos(GATEWAY_DIST, 0)
            .requires("mana_core")
            .cost(1)
            .build());

    public static final SkillNode CULTIST_REGEN = reg(new SkillNode.Builder("cultist_regen")
            .name("Поглощение Тьмы")
            .description("Тьма питает того, кто принял её.\n"
                    + "+2 к регенерации маны в секунду.")
            .type(SkillNode.NodeType.PASSIVE)
            .school(MagicSchool.CULTIST)
            .pos(PASSIVE_DIST, 0)
            .requires("cultist_gateway")
            .cost(1)
            .bonus("+2 реген. маны/сек")
            .build());

    public static final SkillNode CULTIST_CURSE = reg(new SkillNode.Builder("cultist_curse")
            .name("Проклятье Ученика")
            .description("Простейшее из проклятий. Изъедает цель.\n"
                    + "1 урон/сек · 10 секунд.\n"
                    + "Мана: 30  ·  Откат: 20 сек")
            .type(SkillNode.NodeType.SPELL)
            .school(MagicSchool.CULTIST)
            .pos(SPELL_DIST, 0)
            .requires("cultist_regen")
            .cost(1)
            .spell("apprentice_curse")
            .build());

    // ─── Путь Марионетки (вниз, +Y) ──────────────────────────────────────────

    public static final SkillNode PUPPET_GATEWAY = reg(new SkillNode.Builder("puppet_gateway")
            .name("Врата Марионетки")
            .description("Нити судьбы тянутся к руке призывателя.\n"
                    + "Открывает ветку Пути Марионетки.")
            .type(SkillNode.NodeType.GATEWAY)
            .school(MagicSchool.PUPPET)
            .pos(0, GATEWAY_DIST)
            .requires("mana_core")
            .cost(1)
            .build());

    public static final SkillNode PUPPET_REGEN = reg(new SkillNode.Builder("puppet_regen")
            .name("Симбиоз Духа")
            .description("Связь с призванным питает хозяина.\n"
                    + "+2 к регенерации маны в секунду.")
            .type(SkillNode.NodeType.PASSIVE)
            .school(MagicSchool.PUPPET)
            .pos(0, PASSIVE_DIST)
            .requires("puppet_gateway")
            .cost(1)
            .bonus("+2 реген. маны/сек")
            .build());

    public static final SkillNode PUPPET_CHICKEN = reg(new SkillNode.Builder("puppet_chicken")
            .name("Призыв Курицы")
            .description("Марионеточник начинает с малого.\n"
                    + "Призывает обычную курицу.\n"
                    + "Мана: 15  ·  Откат: 30 сек")
            .type(SkillNode.NodeType.SPELL)
            .school(MagicSchool.PUPPET)
            .pos(0, SPELL_DIST)
            .requires("puppet_regen")
            .cost(1)
            .spell("summon_chicken")
            .build());

    // ─── Связи для рендеринга линий ───────────────────────────────────────────

    private static final List<String[]> CONNECTIONS = List.of(
            new String[]{"mana_core",        "crimson_gateway"},
            new String[]{"mana_core",        "holy_gateway"},
            new String[]{"mana_core",        "cultist_gateway"},
            new String[]{"mana_core",        "puppet_gateway"},

            new String[]{"crimson_gateway",  "crimson_regen"},
            new String[]{"crimson_regen",    "crimson_spark"},

            new String[]{"holy_gateway",     "holy_regen"},
            new String[]{"holy_regen",       "holy_ray"},

            new String[]{"cultist_gateway",  "cultist_regen"},
            new String[]{"cultist_regen",    "cultist_curse"},

            new String[]{"puppet_gateway",   "puppet_regen"},
            new String[]{"puppet_regen",     "puppet_chicken"}
    );

    // ─── Вспомогательный метод регистрации ───────────────────────────────────

    private static SkillNode reg(SkillNode node) {
        NODES_MAP.put(node.getId(), node);
        NODES_LIST.add(node);
        return node;
    }

    // ─── Публичное API ────────────────────────────────────────────────────────

    public static SkillNode getById(String id) {
        return NODES_MAP.get(id);
    }

    public static List<SkillNode> getAllNodes() {
        return Collections.unmodifiableList(NODES_LIST);
    }

    public static List<String[]> getConnections() {
        return CONNECTIONS;
    }

    public static Map<String, SkillNode> getNodesMap() {
        return Collections.unmodifiableMap(NODES_MAP);
    }
}
