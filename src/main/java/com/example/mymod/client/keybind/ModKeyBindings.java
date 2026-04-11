package com.example.mymod.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиши мода.
 *
 * G — "Grimoire settings"  — не занята ванильным Minecraft 1.20.1.
 * B — "Book of knowledge"  — не занята ванильным Minecraft 1.20.1.
 * Обе клавиши присутствуют на 75%-ных, TKL и полноразмерных клавиатурах.
 */
public class ModKeyBindings {

    /** Категория в настройках управления (отображается как заголовок группы) */
    public static final String CATEGORY = "key.categories.mymod";

    /** Открыть меню настроек мода (G по умолчанию) */
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.mymod.open_settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    /** Открыть Гримуар Знаний — энциклопедию мода (B по умолчанию) */
    public static final KeyMapping OPEN_GRIMOIRE = new KeyMapping(
            "key.mymod.open_grimoire",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY
    );

    /** Зарегистрировать все клавиши мода — вызывается из ModClientEvents */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS);
        event.register(OPEN_GRIMOIRE);
    }
}
