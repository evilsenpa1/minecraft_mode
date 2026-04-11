package com.example.mymod.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Клиентская конфигурация мода.
 * Файл создаётся автоматически в папке config/ при первом запуске.
 * Игрок может открыть его текстовым редактором или изменить через Mod Menu.
 */
public class ModConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** Итоговая спецификация — передаётся в registerConfig() */
    public static final ForgeConfigSpec SPEC;

    /** Текущий выбранный стиль HUD маны */
    public static final ForgeConfigSpec.EnumValue<ManaHudStyle> MANA_HUD_STYLE;

    static {
        BUILDER.comment("=== Настройки интерфейса MyMod ===").push("hud");

        MANA_HUD_STYLE = BUILDER
                .comment(
                    "Стиль отображения маны на экране.",
                    "  ICON - маленький значок (ромб) с числом и процентами справа",
                    "  BAR  - горизонтальная полоска с подписью (как полоска опыта)"
                )
                .defineEnum("manaHudStyle", ManaHudStyle.BAR);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    /**
     * Доступные стили HUD маны.
     * Расширяй при добавлении новых вариантов отображения.
     */
    public enum ManaHudStyle {
        /** Значок ромба + числа + проценты (компактный) */
        ICON,
        /** Горизонтальная полоска с заливкой (наглядный) */
        BAR
    }
}
