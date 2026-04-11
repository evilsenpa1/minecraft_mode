# MyMod — CLAUDE.md

## Stack
- **Minecraft** 1.20.1
- **Forge** 47.3.0
- **Java** 17
- **Gradle** 8.1.1

## Project structure

```
src/main/
  java/com/example/mymod/
    MyMod.java              — @Mod entry point, registers all DeferredRegisters
    block/
      ModBlocks.java        — DeferredRegister<Block>
    item/
      ModItems.java         — DeferredRegister<Item> + BlockItems
    tab/
      ModCreativeTabs.java  — DeferredRegister<CreativeModeTab>
    event/
      ModEvents.java        — @Mod.EventBusSubscriber (FORGE bus = world events)
  resources/
    META-INF/mods.toml      — mod metadata (id, name, version, dependencies)
    pack.mcmeta             — resource pack format (15 for 1.20.1)
    assets/mymod/
      lang/en_us.json       — display names for items/blocks/tabs
      blockstates/          — which model variant to use per block state
      models/block/         — block models (cube_all, cross, etc.)
      models/item/          — item models (generated, handheld, or block parent)
      textures/block/       — 16×16 PNG textures for blocks
      textures/item/        — 16×16 PNG textures for items
    data/mymod/
      loot_tables/blocks/   — what drops when a block is broken
      recipes/              — crafting / smelting recipes (JSON)
```

## First-time setup

```bash
# Requires Java 17 on PATH
./gradlew genEclipseRuns    # generate run configs (Eclipse / VSCode Java LS)
./gradlew build             # compile + package → build/libs/mymod-1.0.0.jar
```

> **Important:** `gradle-wrapper.jar` is not committed.
> Run `gradle wrapper --gradle-version 8.1.1` once, or copy it from the Forge MDK.

## Running in-game

```bash
./gradlew runClient    # launches Minecraft client with mod loaded
./gradlew runServer    # launches dedicated server
```

## Key conventions
- Use **DeferredRegister** for everything — never register in `FMLCommonSetupEvent`
- **MOD bus** (`modEventBus`) → registration events (`RegisterEvent`, `FMLCommonSetupEvent`)
- **FORGE bus** (`MinecraftForge.EVENT_BUS`) → world/game events (player, entity, block)
- Block textures go in `textures/block/`, item textures in `textures/item/`
- Loot tables must exist for a block to drop itself when mined
- `pack_format` for 1.20.1 = **15**


## Концепция мода — Магия

Мод добавляет в Minecraft 1.20.1 (Forge) систему магии. Цель — создать полноценную магическую прогрессию с уникальными механиками.

### Планируемые системы
- **Мана** — ресурс игрока, тратится на заклинания, восстанавливается со временем
- **Посохи / жезлы** — магическое оружие, кастует заклинания
- **Заклинания (Spells)** — активные способности (огонь, молния, лёд, телекинез и т.д.)
- **Алтари / ритуалы** — структуры для крафта магических предметов и прогрессии
- **Магические блоки** — генерируются в мире (кристаллы, руины, порталы)
- **Книга заклинаний (Grimoire)** — гайд-предмет для игрока (аналог Patchouli)
- **Зачарования** — кастомные энчанты для магического снаряжения

### Школы магии

#### 1. Багровый путь *(магия разрушения)*
- **Стихии:** Огонь, Молния, немного Льда
- **Роль:** Чистый DPS, уничтожение одиночных целей и групп
- **Примеры заклинаний:** Fireball, Chain Lightning, Blizzard Shard, Meteor Strike
- **Фокус:** Максимальный урон, взрывные AoE, поджоги и оглушения

#### 2. Путь Святого Тимофея *(святая магия)*
- **Стихии:** Свет, Божественная энергия
- **Роль:** Паладин-ближник, хил, защита
- **Примеры заклинаний:** Holy Strike, Barrier of Faith, Smite, Aura of Healing
- **Уникальная механика:** **Руны** — наносятся на оружие и броню, дают пассивные/активные эффекты (единственная школа с поддержкой ближнего боя)
- **Фокус:** Единственная школа для билда паладина; руническая система открывает комбинации эффектов

#### 3. Путь Культиста *(тёмная магия)*
- **Стихии:** Тьма, Яд, Проклятие
- **Роль:** DoT-маг, дебаффер, контроль
- **Примеры заклинаний:** Curse of Decay, Shadow Bolt, Plague Cloud, Soul Drain
- **Фокус:** Урон со временем, стаки проклятий, негативные статус-эффекты на врагов

#### 4. Путь Марионетки *(магия призыва)*
- **Стихии:** Магия связи, Хаос
- **Роль:** Саммонер, контролёр, симбионт
- **Примеры заклинаний:** Summon Familiar, Entangle, Puppet Bind, Symbiosis
- **Уникальная механика:** **Симбиоз** — призванные существа дают пассивные бонусы владельцу; чем сильнее питомец, тем сильнее хозяин
- **Фокус:** Контроль толпы, армия призывников, усиление через связь с существами

#### 5. Магия усиления и поддержки *(общая школа)*
- **Роль:** Универсальная поддержка, доступна всем школам
- **Примеры заклинаний:** Mana Shield, Blink, Haste, Ward, Phase Step
- **Фокус:** Барьеры, баффы на перемещение, утилита — не требует специализации

### Система прокачки — Древо умений (аналог Path of Exile)
- Глобальное дерево пассивных узлов: каждая ветка усиливает свою школу
- Вкладывая очки в ветку → растёт бонус к урону/эффектам той школы
- Можно брать узлы соседних школ — гибридные билды возможны, но дорого
- **Мастерство школы** достигается только через полную прокачку ветки + сет брони

### Сетовая броня школ
Каждая школа имеет уникальный сет брони. Только в полном сете открываются **высшие заклинания** школы (tier 3+). Без сета — базовые и средние способности.

| Школа               | Сет брони          | Бонус сета                                  |
|---------------------|--------------------|---------------------------------------------|
| Багровый путь       | Crimson Mage Set   | +урон огнём/молнией, AoE радиус             |
| Путь Тимофея        | Saintly Plate Set  | +броня, руны наносятся быстрее              |
| Путь Культиста      | Cultist Shroud Set | +длительность проклятий, стаки DoT          |
| Путь Марионетки     | Puppeteer Set      | +макс. кол-во призывников, симбиоз сильнее |

### Масштаб проекта
- **Боссы** — уникальные боссы для каждой школы + финальный (endgame)
- **Серверные битвы** — высокоуровневые дуэли с масштабными разрушениями (разрушаемые блоки от мощных заклинаний)
- **PvP-фокус** — баланс между школами для интересных противостояний на серверах

### Структура пакетов (расширенная)
```
java/com/example/mymod/
  spell/
    Spell.java              — базовый абстрактный класс заклинания
    SpellRegistry.java      — DeferredRegister для заклинаний
    impl/                   — конкретные заклинания
  capability/
    ManaCapability.java     — capability для маны игрока
    IMana.java              — интерфейс маны
  entity/
    ModEntities.java        — кастомные существа (снаряды, призванные мобы)
  effect/
    ModEffects.java         — кастомные статус-эффекты
  enchantment/
    ModEnchantments.java    — кастомные зачарования
  network/
    ModNetwork.java         — пакеты клиент ↔ сервер (для синхронизации маны/заклинаний)
  worldgen/
    ModFeatures.java        — генерация структур и руд в мире
```

### Ключевые принципы разработки мода
- Мана хранится через **Capability** (`ICapabilityProvider`) — синхронизируется пакетами
- Заклинания — **зарегистрированные объекты** (DeferredRegister), не хардкод
- Все сущности-снаряды наследуются от `Projectile` или `ThrowableProjectile`
- Кастомные структуры регистрируются через `StructureType` + JSON в `data/mymod/structures/`
- Эффекты заклинаний на клиенте — через `ParticleOptions` и пакеты

## Гримуар Знаний (энциклопедия мода)

Внутриигровая энциклопедия — файл `client/screen/encyclopedia/EncyclopediaData.java`.

**Правило:** После добавления или значительного обновления любой игровой механики
(заклинание, система, предмет, структура, школа, босс) — **обязательно** обнови
энциклопедию: добавь или отредактируй статью в соответствующей вкладке.

### Как добавить статью

```java
// В нужном методе buildXxxTab() в EncyclopediaData.java:
new EncyclopediaEntry(
    "Название статьи",
    new String[]{"тег1", "тег2"},   // слова для поиска (строчные)
    List.of(
        h1("ЗАГОЛОВОК"),
        separator(),
        flavor("Атмосферная цитата или лоровая строка."),
        separator(),
        body("Описание механики — что это такое и как работает."),
        separator(),
        h2("ХАРАКТЕРИСТИКИ"),
        body("Конкретные числа и параметры."),
        separator(),
        tip("Практический совет игроку.")
    )
)
```

### Правила написания контента

- **Стиль** — тёмное фэнтези: мрачно, поэтично, без лишних слов
- **flavor** — всегда короткие (1–2 строки), как цитата из книги или пословица
- **body** — конкретно и по делу, без воды; длинный текст дроби на несколько `body()`
- **tip** — только реально полезные советы по геймплею
- **Числа** — всегда актуальные; при изменении баланса — обновляй статью
- **Заготовки** — если механика не реализована, пиши `flavor("[ В разработке ]")` и план

### Вкладки энциклопедии

| Метод | Вкладка | Что добавлять |
|---|---|---|
| `buildLoreTab()` | ✦ Лор | Мировая история, фракции, НПС, события |
| `buildManaTab()` | ◈ Мана | Всё о системе маны |
| `buildSpellsTab()` | ✵ Заклинания | Каждое заклинание — отдельная статья |
| `buildSchoolsTab()` | ⚑ Школы | Школы магии, броня, таланты |
| `buildStructuresTab()` | ⌂ Структуры | Структуры мира, алтари, порталы |

## For user
- отвечай на русском языке
- пиши код как опытный java senior разработчик
- комментируй код на русском, код будет поддерживать джуниор по джаве
- код должен быть структурированым и поддерживаемым
- сам мод должен быть на русском языке и в одном стиле
- мод в стиле красивой фентези магии с примесями дарк фентези, также можно использовать типичные элементы жанра: эльфы, дварфы, демоны, всякие сказочные странные твари, драконы и тд. (не используй крысолюдей)