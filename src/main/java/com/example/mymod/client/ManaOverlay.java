package com.example.mymod.client;

import com.example.mymod.capability.ManaCapability;
import com.example.mymod.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * HUD маны — отображается поверх всех стандартных элементов интерфейса.
 *
 * Два стиля (выбираются в конфиге):
 *   ICON — компактный значок (синий ромб) с числами и процентами
 *   BAR  — горизонтальная полоска с подписью (аналог полоски здоровья)
 */
public class ManaOverlay {

    // ─── Цвета ───────────────────────────────────────────────────────────────

    // Цвет фона значка / полоски (полупрозрачный чёрный)
    private static final int COLOR_BG            = 0x99000000;
    // Основной синий (заливка маны)
    private static final int COLOR_MANA_BASE     = 0xFF1A5FCC;
    // Синий блик сверху (эффект объёма)
    private static final int COLOR_MANA_SHINE    = 0xFF4DA6FF;
    // Тёмный фон полоски
    private static final int COLOR_BAR_EMPTY     = 0xFF1A1A33;
    // Текст основной
    private static final int COLOR_TEXT_MAIN     = 0xFFADD8E6;
    // Текст вторичный (проценты)
    private static final int COLOR_TEXT_PERCENT  = 0xFF87CEEB;

    // ─── Регистрация ─────────────────────────────────────────────────────────

    /**
     * Зарегистрировать оверлей.
     * Вызывается в ModClientEvents через RegisterGuiOverlaysEvent.
     */
    public static void register(RegisterGuiOverlaysEvent event) {
        // registerAboveAll — рисуем поверх всего стандартного HUD
        event.registerAboveAll("mana_hud", ManaOverlay::renderHud);
    }

    // ─── Точка входа рендера ─────────────────────────────────────────────────

    private static void renderHud(ForgeGui gui, GuiGraphics gfx,
                                   float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();

        // Не рисуем: игрок не загружен, или открыт инвентарь, или дебаг-экран
        if (mc.player == null) return;
        if (mc.screen != null) return;
        if (mc.options.renderDebug) return;

        mc.player.getCapability(ManaCapability.MANA).ifPresent(mana -> {
            int current = mana.getMana();
            int max     = mana.getMaxMana();

            switch (ModConfig.MANA_HUD_STYLE.get()) {
                case ICON -> renderIconStyle(gfx, mc, current, max, screenW, screenH);
                case BAR  -> renderBarStyle(gfx, mc, current, max, screenW, screenH);
            }
        });
    }

    // =========================================================================
    // СТИЛЬ 1: ICON — компактный значок с числами
    // =========================================================================

    /**
     * Рисует синий ромб-значок, правее — числа (текущая/максимум) и проценты.
     * Позиция: правый нижний угол, над хотбаром.
     *
     *   [ ромб ]  75 / 100
     *              75%
     */
    private static void renderIconStyle(GuiGraphics gfx, Minecraft mc,
                                         int current, int max,
                                         int screenW, int screenH) {
        // Отступ от правого и нижнего краёв
        int originX = screenW - 95;
        int originY = screenH - 58;

        int iconSize = 10; // полуразмер ромба (итого 20×20 пикселей)
        int iconCX   = originX + iconSize;   // центр ромба по X
        int iconCY   = originY + iconSize;   // центр ромба по Y

        // Тёмный фон-плашка под весь элемент
        gfx.fill(originX - 3, originY - 3, originX + 85, originY + 24, COLOR_BG);

        // Ромб (заполнен двумя слоями: основной цвет + блик сверху)
        drawDiamond(gfx, iconCX, iconCY, iconSize, COLOR_MANA_BASE);
        // Блик — верхняя половина ромба, чуть светлее
        drawDiamondHalf(gfx, iconCX, iconCY, iconSize, COLOR_MANA_SHINE);

        // Текст с числами: "75 / 100"
        int textX = originX + iconSize * 2 + 5;
        String numbers = current + " / " + max;
        gfx.drawString(mc.font, numbers, textX, originY + 2, COLOR_TEXT_MAIN, true);

        // Текст с процентами: "75%"
        int percent = (max > 0) ? (current * 100 / max) : 0;
        gfx.drawString(mc.font, percent + "%", textX, originY + 13, COLOR_TEXT_PERCENT, true);
    }

    /**
     * Рисует ромб горизонтальными полосками.
     *
     * @param cx    центр по X
     * @param cy    центр по Y
     * @param size  полуразмер (итоговый размер = size*2 × size*2)
     * @param color ARGB цвет
     */
    private static void drawDiamond(GuiGraphics gfx, int cx, int cy, int size, int color) {
        for (int row = 0; row < size * 2; row++) {
            int y    = cy - size + row;
            // ширина строки нарастает до середины, потом убывает
            int half = (row < size) ? row : (size * 2 - 1 - row);
            gfx.fill(cx - half, y, cx + half + 1, y + 1, color);
        }
    }

    /**
     * Рисует только верхнюю половину ромба — используется для блика.
     */
    private static void drawDiamondHalf(GuiGraphics gfx, int cx, int cy, int size, int color) {
        for (int row = 0; row < size; row++) {
            int y    = cy - size + row;
            int half = row;
            gfx.fill(cx - half, y, cx + half + 1, y + 1, color);
        }
    }

    // =========================================================================
    // СТИЛЬ 2: BAR — горизонтальная полоска
    // =========================================================================

    /**
     * Рисует горизонтальную полоску маны.
     * Позиция: левый нижний угол, над хотбаром.
     *
     *   Мана: 75 / 100
     *   [████████░░░░░░░░░░░░]
     */
    private static void renderBarStyle(GuiGraphics gfx, Minecraft mc,
                                        int current, int max,
                                        int screenW, int screenH) {
        int barX      = 10;         // отступ от левого края
        int barY      = screenH - 57; // над хотбаром
        int barWidth  = 102;        // полная ширина полоски
        int barHeight = 8;          // высота полоски

        // Ширина заполненной части (пропорционально мане)
        int fillW = (max > 0) ? (current * barWidth / max) : 0;

        // Подпись над полоской
        String label = "Мана: " + current + " / " + max;
        gfx.drawString(mc.font, label, barX, barY - 11, COLOR_TEXT_MAIN, true);

        // Тёмный фон-обводка
        gfx.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, COLOR_BG);

        // Серо-синий фон пустой части
        gfx.fill(barX, barY, barX + barWidth, barY + barHeight, COLOR_BAR_EMPTY);

        // Заполненная часть (синяя)
        if (fillW > 0) {
            // Основной цвет маны
            gfx.fill(barX, barY, barX + fillW, barY + barHeight, COLOR_MANA_BASE);
            // Блик — верхняя 1/3 полоски
            int shineH = Math.max(1, barHeight / 3);
            gfx.fill(barX, barY, barX + fillW, barY + shineH, COLOR_MANA_SHINE);
        }

        // Проценты справа от полоски
        int percent = (max > 0) ? (current * 100 / max) : 0;
        gfx.drawString(mc.font, percent + "%", barX + barWidth + 4, barY, COLOR_TEXT_PERCENT, true);
    }
}
