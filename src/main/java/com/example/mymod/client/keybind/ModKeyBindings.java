package com.example.mymod.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиши мода. Все биндинги регистрируются в категории "MyMod"
 * и доступны в настройках управления Minecraft для перебиндинга.
 *
 * G — настройки мода
 * B — Гримуар Знаний (энциклопедия)
 * P — Древо Познания (дерево прокачки)
 * R — Колесо заклинаний (открывает круговой селектор)
 * F — Применить заклинание (каст активного слота)
 *
 * Проверка занятости клавиш в ванильном Minecraft 1.20.1:
 *   G, B, P, R, F — свободны.
 */
public class ModKeyBindings {

    public static final String CATEGORY = "key.categories.mymod";

    /** Открыть меню настроек мода (G) */
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.mymod.open_settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    /** Открыть Гримуар Знаний — энциклопедию мода (B) */
    public static final KeyMapping OPEN_GRIMOIRE = new KeyMapping(
            "key.mymod.open_grimoire",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY
    );

    /** Открыть Древо Познания — дерево прокачки (P) */
    public static final KeyMapping OPEN_SKILL_TREE = new KeyMapping(
            "key.mymod.open_skill_tree",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            CATEGORY
    );

    /**
     * Открыть колесо заклинаний (R).
     * Нажатие открывает круговой селектор — выбор активного слота мышью.
     */
    public static final KeyMapping OPEN_SPELL_WHEEL = new KeyMapping(
            "key.mymod.open_spell_wheel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
    );

    /**
     * Применить заклинание из активного слота (F).
     * Отправляет CastSpellPacket на сервер.
     */
    public static final KeyMapping CAST_SPELL = new KeyMapping(
            "key.mymod.cast_spell",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            CATEGORY
    );

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS);
        event.register(OPEN_GRIMOIRE);
        event.register(OPEN_SKILL_TREE);
        event.register(OPEN_SPELL_WHEEL);
        event.register(CAST_SPELL);
    }
}
