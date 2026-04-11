package com.example.mymod.spell;

import com.example.mymod.spell.impl.*;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Статический реестр всех заклинаний мода.
 *
 * Не использует Forge DeferredRegister (заклинания — не игровые объекты, а Java-объекты).
 * Заклинания инициализируются при первом обращении к классу (static init).
 *
 * Получить заклинание: SpellRegistry.get("spark")
 * Получить все:        SpellRegistry.getAll()
 */
public class SpellRegistry {

    private static final Map<String, Spell> REGISTRY = new LinkedHashMap<>();

    // ─── Регистрация заклинаний ───────────────────────────────────────────────

    /** Искра (Багровый путь) — поджигает блок, как огниво */
    public static final Spell SPARK           = register(new SparkSpell());

    /** Луч Святости (Путь Тимофея) — хитскан урон */
    public static final Spell HOLY_RAY        = register(new HolyRaySpell());

    /** Проклятье Ученика (Путь Культиста) — DoT эффект */
    public static final Spell APPRENTICE_CURSE = register(new ApprenticesCurseSpell());

    /** Призыв Курицы (Путь Марионетки) — спавнит курицу */
    public static final Spell SUMMON_CHICKEN  = register(new SummonChickenSpell());

    // ─── API ──────────────────────────────────────────────────────────────────

    private static Spell register(Spell spell) {
        REGISTRY.put(spell.getId(), spell);
        return spell;
    }

    /** Получить заклинание по ID. Вернёт null, если не найдено. */
    public static Spell get(String id) {
        return REGISTRY.get(id);
    }

    /** Все зарегистрированные заклинания */
    public static Collection<Spell> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}
