package com.example.mymod.client;

import com.example.mymod.capability.ManaCapability;
import com.example.mymod.config.ModConfig;
import com.example.mymod.skill.PlayerSkillsCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.Random;

/**
 * HUD маны — отображается поверх всех стандартных элементов интерфейса.
 *
 * Четыре стиля (выбираются в конфиге):
 *   ICON         — компактный значок (синий ромб) с числами и процентами
 *   BAR          — горизонтальная полоска с подписью (аналог полоски здоровья)
 *   VERTICAL_BAR — вертикальная полоска справа экрана с магическим переливом (без цифр)
 *   ENCHANT_BAR  — горизонтальная полоска справа экрана с эффектом зачарования (без цифр)
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

        // HUD маны скрыт до открытия центральной ноды «Пробуждение»
        boolean manaUnlocked = mc.player.getCapability(PlayerSkillsCapability.SKILLS)
                .map(s -> s.isUnlocked("mana_core"))
                .orElse(false);
        if (!manaUnlocked) return;

        // Безопасно получаем стиль HUD: если конфиг ещё не загружен — используем BAR по умолчанию.
        // Forge может поймать исключение из оверлея и просто не рисовать его,
        // поэтому не даём get() кидать исключение и не возвращаем null.
        ModConfig.ManaHudStyle hudStyle;
        try {
            hudStyle = ModConfig.MANA_HUD_STYLE.get();
        } catch (Exception e) {
            hudStyle = null;
        }
        if (hudStyle == null) hudStyle = ModConfig.ManaHudStyle.BAR;
        final ModConfig.ManaHudStyle finalStyle = hudStyle;

        mc.player.getCapability(ManaCapability.MANA).ifPresent(mana -> {
            int current = mana.getMana();
            int max     = mana.getMaxMana();

            switch (finalStyle) {
                case ICON         -> renderIconStyle(gfx, mc, current, max, screenW, screenH);
                case BAR          -> renderBarStyle(gfx, mc, current, max, screenW, screenH);
                case VERTICAL_BAR -> renderVerticalBarStyle(gfx, current, max, screenW, screenH);
                case ENCHANT_BAR  -> renderEnchantBarStyle(gfx, current, max, screenW, screenH);
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
        // Отступ от правого и нижнего краёв (немного выше, чтобы не перекрывать хотбар)
        int originX = screenW - 95;
        int originY = screenH - 68;

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

    // =========================================================================
    // СТИЛЬ 3: VERTICAL_BAR — «Кристальный сосуд»
    // =========================================================================

    /**
     * Рисует вертикальный магический столп — «Кристальный Портал».
     * Стилистика: высокая золотая рама с синим свечением внутри (как портал/туманность),
     * кристаллы сверху и снизу, рассеянные звёздочки вокруг. Без цифр и процентов.
     *
     * Позиция: правая сторона экрана, вертикальный центр.
     *
     * Анимированные элементы:
     *   — Трёхслойное пульсирующее синее/фиолетовое свечение вокруг рамки
     *   — Массивная золотая рамка с угловыми акцентами и 3D-бликами
     *   — Ступенчатые золотые постаменты сверху и снизу с боковыми «колоннами»
     *   — Заполнение: тёмно-синий край → ярко-белый/голубой центр (портальный градиент)
     *   — Мерцающие внутренние звёзды (статичные + крупные крестообразные)
     *   — Три движущихся горизонтальных блика с разной скоростью
     *   — Молниеподобные энергетические трещины
     *   — Пульсирующая верхняя кромка заполнения (белая линия)
     *   — Кластер кристаллов сверху (2 внешних + 2 средних фиолетовых + 1 центральный голубой)
     *   — Кластер кристаллов снизу (4 фиолетовых + 1 центральный голубой)
     *   — Пульсирующее свечение вокруг кристаллов
     *   — 12 внешних мерцающих звёздочек разного цвета
     */
    private static void renderVerticalBarStyle(GuiGraphics gfx,
                                                int current, int max,
                                                int screenW, int screenH) {
        // ── Геометрия ────────────────────────────────────────────────────────
        final int BAR_W        = 20;  // ширина внутреннего бара
        final int BAR_H        = 185; // высота внутреннего бара
        final int FRAME_T      = 3;   // толщина золотой рамки
        final int MARGIN_RIGHT = 22;  // отступ от правого края экрана
        final int PLT_EXTRA    = 7;   // выступ постамента за рамку с каждой стороны

        int barX   = screenW - MARGIN_RIGHT - BAR_W;
        int barY   = screenH / 2 - BAR_H / 2;
        int barBot = barY + BAR_H;

        // Внешние границы золотой рамки
        int frameL = barX - FRAME_T;
        int frameR = barX + BAR_W + FRAME_T;
        int frameT = barY - FRAME_T;
        int frameB = barBot + FRAME_T;

        // Центр бара по X
        int barCX = barX + BAR_W / 2;

        // Заполнение (снизу вверх)
        int fillH   = (max > 0) ? (current * BAR_H / max) : 0;
        int fillTop = barBot - fillH;

        // ── Время для анимации ───────────────────────────────────────────────
        long   now = System.currentTimeMillis();
        double PI2 = Math.PI * 2;

        // ── 1. Трёхслойное пульсирующее внешнее свечение ─────────────────────
        float glowPulse = (float)(Math.sin((now % 2000) / 2000.0 * PI2) * 0.5 + 0.5);
        int gA1 = (int)(0x28 + glowPulse * 0x1C);
        int gA2 = (int)(0x12 + glowPulse * 0x12);
        int gA3 = (int)(0x07 + glowPulse * 0x09);
        // Зоны свечения с учётом кристаллов (~36px сверху, ~22px снизу)
        gfx.fill(frameL - 10, frameT - 36, frameR + 10, frameB + 22, argb(gA1, 0x20, 0x55, 0xFF));
        gfx.fill(frameL - 22, frameT - 48, frameR + 22, frameB + 34, argb(gA2, 0x15, 0x40, 0xCC));
        gfx.fill(frameL - 36, frameT - 62, frameR + 36, frameB + 48, argb(gA3, 0x08, 0x25, 0x99));

        // ── 2. Тёмная подложка под элементом ────────────────────────────────
        gfx.fill(frameL - 3, frameT - 3, frameR + 3, frameB + 3, 0xCC000011);

        // ── 3. Золотая рамка (многослойная 3D) ───────────────────────────────
        gfx.fill(frameL - 1, frameT - 1, frameR + 1, frameB + 1, 0xFF5A3D08); // внешний тёмный обвод
        gfx.fill(frameL,     frameT,     frameR,     frameB,     0xFFCC9820); // основное золото
        gfx.fill(frameL + 1, frameT + 1, frameR - 1, frameB - 1, 0xFFD4A830); // внутреннее золото
        gfx.fill(frameL,     frameT,     frameR,     frameT + 1, 0xFFFFE060); // верхний блик
        gfx.fill(frameL,     frameT,     frameL + 1, frameB,     0xFFFFE060); // левый блик
        gfx.fill(frameL,     frameB - 1, frameR,     frameB,     0xFF7A5210); // нижняя тень
        gfx.fill(frameR - 1, frameT,     frameR,     frameB,     0xFF7A5210); // правая тень
        // Угловые золотые акценты (3×3 пикселя)
        gfx.fill(frameL - 1, frameT - 1, frameL + 2, frameT + 2, 0xFFFFD700);
        gfx.fill(frameR - 2, frameT - 1, frameR + 1, frameT + 2, 0xFFFFD700);
        gfx.fill(frameL - 1, frameB - 2, frameL + 2, frameB + 1, 0xFFFFD700);
        gfx.fill(frameR - 2, frameB - 2, frameR + 1, frameB + 1, 0xFFFFD700);

        // ── 4. Тёмный фон внутри бара ────────────────────────────────────────
        gfx.fill(barX, barY, barX + BAR_W, barBot, 0xFF010115);

        // ── 5. Заполнение: портальный градиент (тёмный край → белый центр) ───
        if (fillH > 0) {
            // Фаза цвета: синий ↔ слегка фиолетово-синий (период 3 с)
            float colorPhase = (float)(Math.sin((now % 3000) / 3000.0 * PI2) * 0.5 + 0.5);
            // Пульс яркости центра (период 1.2 с)
            float centerPulse = (float)(Math.sin((now % 1200) / 1200.0 * PI2) * 0.15 + 0.85);

            // По колонкам: создаём эффект яркого портального окна
            // distC: 0.0 = центр бара, 1.0 = левый/правый край
            for (int col = 0; col < BAR_W; col++) {
                int   x     = barX + col;
                float distC = Math.abs(col - (BAR_W - 1) * 0.5f) / ((BAR_W - 1) * 0.5f);
                // Волна со смещением по колонке — имитирует «движение» в портале
                float wave = (float)(Math.sin(
                        ((now % 2000) / 2000.0 - col / (double)BAR_W * 0.5) * PI2
                ) * 0.5 + 0.5);

                // Центр: ярко-белый/голубой; край: тёмно-синий (квадратичное затухание)
                float bright = (1.0f - distC * distC) * centerPulse;
                int r = Math.min(255, (int)(
                        lerpInt(lerpInt(0x05, 0x28, colorPhase), 255, bright) * (0.7f + wave * 0.3f)));
                int g = Math.min(255, (int)(
                        lerpInt(lerpInt(0x08, 0x18, colorPhase), 238, bright) * (0.7f + wave * 0.3f)));
                int b = Math.min(255, (int)(
                        lerpInt(lerpInt(0x55, 0x80, colorPhase), 255, bright) * (0.8f + wave * 0.2f)));

                gfx.fill(x, fillTop, x + 1, barBot, argb(0xFF, r, g, b));
            }

            // ── Мерцающие внутренние звёзды (туманность/портал) ──────────────
            // Seed меняется каждые 500 мс — звёзды плавно обновляются
            Random starRng = new Random((now / 500) * 3571L);
            for (int i = 0; i < 20; i++) {
                int sx = barX + 1 + starRng.nextInt(BAR_W - 2);
                int sy = fillTop + starRng.nextInt(Math.max(1, fillH));
                if (starRng.nextFloat() < 0.60f) {
                    boolean isBright = starRng.nextFloat() < 0.30f;
                    gfx.fill(sx, sy, sx + 1, sy + 1, isBright ? 0xFFFFFFFF : 0xFFBBEEFF);
                    if (isBright && starRng.nextFloat() < 0.45f) {
                        // Крестообразная крупная звёздочка
                        gfx.fill(sx - 1, sy, sx + 2, sy + 1, 0x77FFFFFF);
                        gfx.fill(sx, sy - 1, sx + 1, sy + 2, 0x77FFFFFF);
                    }
                }
            }

            // ── Три движущихся горизонтальных блика ───────────────────────────
            int s1cy = fillTop + (int)((now % 1600) / 1600.0 * fillH);
            drawHorizontalShimmer(gfx, barX + 3, barX + BAR_W - 3, s1cy,
                    Math.max(3, fillH / 8), fillTop, barBot, 0x4488DDFF);

            int s2cy = fillTop + (int)((now % 2800) / 2800.0 * fillH);
            drawHorizontalShimmer(gfx, barX + 5, barX + BAR_W - 5, s2cy,
                    Math.max(2, fillH / 14), fillTop, barBot, 0xAAFFFFFF);

            int s3cy = fillTop + (int)((now % 4500) / 4500.0 * fillH);
            drawHorizontalShimmer(gfx, barX + 1, barX + BAR_W - 1, s3cy,
                    Math.max(6, fillH / 5), fillTop, barBot, 0x1A5599FF);

            // ── Энергетические трещины (молниеподобные) ───────────────────────
            Random lRng = new Random((now / 400) * 7919L);
            if (fillH > 25 && lRng.nextFloat() < 0.40f) {
                int lx  = barX + 3 + lRng.nextInt(BAR_W - 6);
                int len = Math.min(fillH / 2, 28 + lRng.nextInt(20));
                int ly0 = fillTop + lRng.nextInt(Math.max(1, fillH - len));
                int ltA = (int)((Math.sin((now % 250) / 250.0 * PI2) * 0.3 + 0.7) * 0xDD);
                for (int ly = ly0; ly < ly0 + len; ly += 2) {
                    int off = (lRng.nextBoolean() ? 1 : -1) * (1 + lRng.nextInt(2));
                    lx = Math.max(barX + 1, Math.min(barX + BAR_W - 2, lx + off));
                    gfx.fill(lx, ly, lx + 1, ly + 2, argb(ltA, 0xDD, 0xF0, 0xFF));
                }
            }

            // ── Пульсирующая верхняя кромка заполнения (белая) ───────────────
            float ep = (float)(Math.sin((now % 500) / 500.0 * PI2) * 0.5 + 0.5);
            gfx.fill(barX,     fillTop,     barX + BAR_W,     fillTop + 1,
                    argb((int)(0xAA + ep * 0x55), 0xFF, 0xFF, 0xFF));
            gfx.fill(barX + 2, fillTop + 1, barX + BAR_W - 2, fillTop + 2,
                    argb((int)(ep * 0xBB), 0xCC, 0xEE, 0xFF));
        }

        // ── 6. Ступенчатые постаменты сверху и снизу ─────────────────────────
        int pW = PLT_EXTRA;

        // Верхний постамент (две ступени + боковые «колонны»)
        gfx.fill(frameL - pW,     frameT - 6,  frameR + pW,     frameT,     0xFF7A5210); // тёмное основание
        gfx.fill(frameL - pW,     frameT - 5,  frameR + pW,     frameT,     0xFFCC9820); // основное золото
        gfx.fill(frameL - pW,     frameT - 5,  frameR + pW,     frameT - 4, 0xFFFFE060); // блик
        gfx.fill(frameL - pW + 2, frameT - 3,  frameR + pW - 2, frameT,     0xFFD4A830); // нижняя ступень
        // Боковые вертикальные акценты («колонны»)
        gfx.fill(frameL - pW - 2, frameT - 8,  frameL - pW + 2, frameT,     0xFFCC9820);
        gfx.fill(frameR + pW - 2, frameT - 8,  frameR + pW + 2, frameT,     0xFFCC9820);
        gfx.fill(frameL - pW - 2, frameT - 8,  frameL - pW + 2, frameT - 7, 0xFFFFE060);
        gfx.fill(frameR + pW - 2, frameT - 8,  frameR + pW + 2, frameT - 7, 0xFFFFE060);

        // Нижний постамент (аналогичный)
        gfx.fill(frameL - pW,     frameB,      frameR + pW,     frameB + 6, 0xFF7A5210);
        gfx.fill(frameL - pW,     frameB,      frameR + pW,     frameB + 5, 0xFFCC9820);
        gfx.fill(frameL - pW,     frameB + 1,  frameR + pW,     frameB + 2, 0xFFFFE060);
        gfx.fill(frameL - pW + 2, frameB,      frameR + pW - 2, frameB + 3, 0xFFD4A830);
        gfx.fill(frameL - pW - 2, frameB,      frameL - pW + 2, frameB + 8, 0xFFCC9820);
        gfx.fill(frameR + pW - 2, frameB,      frameR + pW + 2, frameB + 8, 0xFFCC9820);
        gfx.fill(frameL - pW - 2, frameB + 7,  frameL - pW + 2, frameB + 8, 0xFF7A5210);
        gfx.fill(frameR + pW - 2, frameB + 7,  frameR + pW + 2, frameB + 8, 0xFF7A5210);

        // ── 7. Анимация цвета кристаллов ─────────────────────────────────────
        float cp1 = (float)(Math.sin((now % 2200) / 2200.0 * PI2) * 0.5 + 0.5); // голубой
        float cp2 = (float)(Math.sin((now % 3100) / 3100.0 * PI2 + 1.2) * 0.5 + 0.5); // фиолетовый

        int cR1 = lerpInt(0x44, 0xAA, cp1), cG1 = lerpInt(0xBB, 0xFF, cp1);
        int cyanMain  = argb(0xFF, cR1, cG1, 0xFF);
        int cyanShine = argb(0xFF, Math.min(255, cR1 + 60), Math.min(255, cG1 + 10), 0xFF);
        int cyanGlow  = argb(0xBB, Math.min(255, cR1 + 100), Math.min(255, cG1 + 20), 0xFF);

        int pR = lerpInt(0x77, 0xBB, cp2), pG = lerpInt(0x08, 0x30, cp2), pB = lerpInt(0xCC, 0xFF, cp2);
        int purpleMain  = argb(0xFF, pR, pG, pB);
        int purpleShine = argb(0xFF, Math.min(255, pR + 50), Math.min(255, pG + 20), 0xFF);

        // ── 8. Кластер кристаллов сверху (острием вверх) ─────────────────────
        int topBase = frameT - 6; // нижняя опорная точка верхних кристаллов

        float cg = (float)(Math.sin((now % 1800) / 1800.0 * PI2) * 0.5 + 0.5);
        gfx.fill(barCX - 20, topBase - 28, barCX + 20, topBase + 2,
                  argb((int)(0x18 + cg * 0x25), 0x44, 0xAA, 0xFF));

        // Внешние маленькие фиолетовые (самые крайние)
        drawCrystalUp(gfx, barCX - 14, topBase - 8,  3, 8,  purpleMain, purpleShine);
        drawCrystalUp(gfx, barCX + 14, topBase - 7,  3, 7,  purpleMain, purpleShine);
        // Средние фиолетовые
        drawCrystalUp(gfx, barCX - 8,  topBase - 14, 4, 14, purpleMain, purpleShine);
        drawCrystalUp(gfx, barCX + 8,  topBase - 13, 4, 13, purpleMain, purpleShine);
        // Центральный голубой (самый высокий, с дополнительным бликом)
        drawCrystalUp(gfx, barCX,      topBase - 20, 6, 20, cyanMain, cyanShine);
        drawCrystalUp(gfx, barCX,      topBase - 20, 2, 20, cyanGlow,  0xCCFFFFFF);

        // ── 9. Кластер кристаллов снизу (острием вниз) ───────────────────────
        int botBase = frameB + 6; // верхняя опорная точка нижних кристаллов

        gfx.fill(barCX - 16, botBase - 2, barCX + 16, botBase + 18,
                  argb((int)(0x15 + cg * 0x1C), 0x44, 0xAA, 0xFF));

        drawCrystalDown(gfx, barCX - 11, botBase + 9,  3, 9,  purpleMain, purpleShine);
        drawCrystalDown(gfx, barCX + 11, botBase + 9,  3, 9,  purpleMain, purpleShine);
        drawCrystalDown(gfx, barCX - 5,  botBase + 12, 4, 12, purpleMain, purpleShine);
        drawCrystalDown(gfx, barCX + 5,  botBase + 12, 4, 12, purpleMain, purpleShine);
        drawCrystalDown(gfx, barCX,      botBase + 16, 5, 16, cyanMain,   cyanShine);

        // ── 10. Внешние мерцающие звёздочки вокруг столпа ────────────────────
        int[][] starPos = {
            {barX - 16, barY + 18},  {barX + BAR_W + 14, barY + 32},
            {barX - 24, barY + 62},  {barX + BAR_W + 21, barY + 78},
            {barX - 18, barY + 108}, {barX + BAR_W + 16, barY + 120},
            {barX - 12, barY + 158}, {barX + BAR_W + 10, barY + 168},
            {barX - 14, barY - 38},  {barX + BAR_W + 12, barY - 22},
            {barX + BAR_W / 2 - 28, barY - 55}, {barX + BAR_W / 2 + 25, barY + BAR_H + 40},
        };
        double[] starFreq = {0.9, 1.1, 0.7, 1.3, 0.8, 1.05, 1.4, 0.6, 1.2, 0.85, 1.55, 0.75};
        for (int i = 0; i < starPos.length; i++) {
            float sp = (float)(Math.sin((now / 650.0 * starFreq[i] + i * 0.9) * Math.PI) * 0.5 + 0.5);
            if (sp > 0.20f) {
                int sA = (int)(sp * 220);
                int sC = (i % 3 == 2)
                    ? argb(sA, 0xCC, 0x88, 0xFF)  // фиолетовая
                    : argb(sA, 0x88, 0xDD, 0xFF);  // голубая
                drawStarSparkle(gfx, starPos[i][0], starPos[i][1], sp > 0.70f ? 3 : 2, sC);
            }
        }
    }

    // ── Вспомогательные рисовальщики ─────────────────────────────────────────

    /**
     * Рисует горизонтальную полосу-блик, ограниченную зоной заполнения.
     *
     * @param x0, x1   левая и правая граница
     * @param centerY  центр полосы по Y
     * @param halfH    полувысота полосы
     * @param clipTop  верхний clip (не выходить за fillTop)
     * @param clipBot  нижний clip (не выходить за barBot)
     * @param color    ARGB-цвет блика
     */
    private static void drawHorizontalShimmer(GuiGraphics gfx, int x0, int x1,
                                               int centerY, int halfH,
                                               int clipTop, int clipBot, int color) {
        int top = Math.max(clipTop, centerY - halfH / 2);
        int bot = Math.min(clipBot, centerY + halfH / 2);
        if (top < bot) gfx.fill(x0, top, x1, bot, color);
    }

    /**
     * Рисует пиксельный кристалл острием вверх.
     * Форма: острие (верхние ~40% — сужающийся треугольник) + прямоугольное тело.
     *
     * @param cx    центр по X
     * @param tipY  Y кончика острия (самая верхняя точка)
     * @param maxW  ширина основания
     * @param h     полная высота кристалла
     */
    private static void drawCrystalUp(GuiGraphics gfx, int cx, int tipY,
                                       int maxW, int h, int colorMain, int colorShine) {
        int taperH = Math.max(1, h * 2 / 5); // острие = верхние ~40%
        for (int row = 0; row < h; row++) {
            int y = tipY + row;
            int w = (row < taperH)
                ? Math.max(1, (row + 1) * maxW / taperH) // сужается к верху
                : maxW;                                    // прямоугольное тело
            int x0 = cx - w / 2;
            gfx.fill(x0, y, x0 + w, y + 1, colorMain);
            if (w > 1) gfx.fill(x0, y, x0 + 1, y + 1, colorShine); // левый блик
        }
    }

    /**
     * Рисует пиксельный кристалл острием вниз.
     *
     * @param cx    центр по X
     * @param tipY  Y кончика острия (самая нижняя точка)
     * @param maxW  ширина основания (верхний край)
     * @param h     полная высота кристалла
     */
    private static void drawCrystalDown(GuiGraphics gfx, int cx, int tipY,
                                         int maxW, int h, int colorMain, int colorShine) {
        int taperH = Math.max(1, h * 2 / 5); // острие = нижние ~40%
        for (int row = 0; row < h; row++) {
            int y = tipY - h + row;
            int w = (row >= h - taperH)
                ? Math.max(1, (h - row) * maxW / taperH) // сужается к низу
                : maxW;
            int x0 = cx - w / 2;
            gfx.fill(x0, y, x0 + w, y + 1, colorMain);
            if (w > 1) gfx.fill(x0, y, x0 + 1, y + 1, colorShine);
        }
    }

    /**
     * Рисует 4-конечную звёздочку (крест из двух линий + белый центральный пиксель).
     *
     * @param cx    центр по X
     * @param cy    центр по Y
     * @param size  «радиус» лучей в пикселях
     * @param color ARGB-цвет лучей
     */
    private static void drawStarSparkle(GuiGraphics gfx, int cx, int cy, int size, int color) {
        gfx.fill(cx - size, cy,        cx + size + 1, cy + 1,        color); // горизонталь
        gfx.fill(cx,        cy - size, cx + 1,        cy + size + 1, color); // вертикаль
        gfx.fill(cx,        cy,        cx + 1,        cy + 1,        0xFFFFFFFF); // белый центр
    }

    // =========================================================================
    // СТИЛЬ 4: ENCHANT_BAR — горизонтальная полоска с эффектом зачарования
    // =========================================================================

    /**
     * Рисует горизонтальную полоску маны на правой стороне экрана.
     * Позиция: вертикальный центр экрана, у правого края.
     * Полоска заполняется справа налево. Без цифр и процентов.
     *
     * Эффект переливания максимально близок к блеску зачарования Minecraft:
     *   — плавный перелив цвета (синий ↔ фиолетово-синий) по всей длине заполнения
     *   — три независимых движущихся блика (два слева направо, один справа налево)
     *   — случайные мерцающие точки-блёстки (seed меняется каждые 120 мс)
     *   — пульсирующая кромка у правого края заполнения
     *   — диагональный цветовой градиент вдоль полоски
     */
    private static void renderEnchantBarStyle(GuiGraphics gfx,
                                               int current, int max,
                                               int screenW, int screenH) {
        // ── Геометрия ────────────────────────────────────────────────────────
        final int BAR_H        = 12;   // высота полоски
        final int BAR_W        = 120;  // полная ширина полоски
        final int PADDING      = 3;    // отступ фона вокруг бара
        final int MARGIN_RIGHT = 18;   // отступ от правого края экрана

        // Правый край бара у правой границы экрана
        int barRight = screenW - MARGIN_RIGHT;
        int barLeft  = barRight - BAR_W;

        // Вертикальный центр экрана
        int barY   = screenH / 2 - BAR_H / 2;
        int barBot = barY + BAR_H;

        // Ширина заполнения (растёт справа налево)
        int fillW    = (max > 0) ? (current * BAR_W / max) : 0;
        // Левая граница заполненной части
        int fillLeft = barRight - fillW;

        // ── Время для анимации ───────────────────────────────────────────────
        long now = System.currentTimeMillis();

        // ── Фон-плашка ───────────────────────────────────────────────────────
        gfx.fill(barLeft - PADDING, barY - PADDING,
                  barRight + PADDING, barBot + PADDING, COLOR_BG);

        // ── Пустая часть бара ────────────────────────────────────────────────
        gfx.fill(barLeft, barY, barRight, barBot, COLOR_BAR_EMPTY);

        // ── Заполненная часть: переливающийся цвет ───────────────────────────
        if (fillW > 0) {
            // Базовый цвет плавно переливается синий ↔ фиолетово-синий (период 3 с)
            float colorPhase = (float)(Math.sin((now % 3000) / 3000.0 * Math.PI * 2) * 0.5 + 0.5);
            int rBase = lerpInt(0x1A, 0x4A, colorPhase);
            int gBase = lerpInt(0x5F, 0x20, colorPhase);
            int bBase = lerpInt(0xCC, 0xCC, colorPhase);

            // Рисуем базовый цвет колонками — создаём диагональный градиент,
            // аналогичный зачарованию: каждая колонка слегка смещена по фазе
            for (int col = 0; col < fillW; col++) {
                int x = fillLeft + col;
                // Сдвиг фазы вдоль полоски — имитирует "движение волны" по оси X
                float localPhase = (float)(Math.sin(
                        ((now % 2500) / 2500.0 - col / (double) BAR_W) * Math.PI * 2
                ) * 0.5 + 0.5);
                int r = lerpInt(0x1A, 0x4A, localPhase);
                int g = lerpInt(0x5F, 0x20, localPhase);
                int b = lerpInt(0xCC, 0xCC, localPhase);
                gfx.fill(x, barY, x + 1, barBot, argb(0xFF, r, g, b));
            }

            // ── Верхний блик (горизонтальная светлая полоска по верхней трети) ──
            int shineH = Math.max(1, BAR_H / 3);
            gfx.fill(fillLeft, barY, barRight, barY + shineH, 0x33ADD8E6);

            // ── Блик 1: движется слева направо по заполнению (период 1.6 с) ──
            float t1  = (float)((now % 1600) / 1600.0);
            int bx1   = fillLeft + (int)(t1 * fillW);
            int bw1   = Math.max(3, fillW / 5);
            int bl1   = Math.max(fillLeft, bx1 - bw1 / 2);
            int br1   = Math.min(barRight, bx1 + bw1 / 2);
            if (bl1 < br1) gfx.fill(bl1, barY, br1, barBot, 0x774DA6FF);

            // ── Блик 2: движется справа налево (период 2.2 с) ───────────────
            float t2  = 1.0f - (float)((now % 2200) / 2200.0);
            int bx2   = fillLeft + (int)(t2 * fillW);
            int bw2   = Math.max(2, fillW / 7);
            int bl2   = Math.max(fillLeft, bx2 - bw2 / 2);
            int br2   = Math.min(barRight, bx2 + bw2 / 2);
            if (bl2 < br2) gfx.fill(bl2, barY, br2, barBot, 0x554DA6FF);

            // ── Блик 3: широкий медленный (период 4 с) ──────────────────────
            float t3  = (float)((now % 4000) / 4000.0);
            int bx3   = fillLeft + (int)(t3 * fillW);
            int bw3   = Math.max(5, fillW / 4);
            int bl3   = Math.max(fillLeft, bx3 - bw3 / 2);
            int br3   = Math.min(barRight, bx3 + bw3 / 2);
            if (bl3 < br3) gfx.fill(bl3, barY, br3, barBot, 0x225090FF);

            // ── Мерцающие точки-блёстки (аналог блеска зачарования) ─────────
            // Seed меняется каждые 120 мс
            Random rng = new Random(now / 120);
            int sparkCount = 7;
            for (int i = 0; i < sparkCount; i++) {
                int sparkX = fillLeft + rng.nextInt(fillW);
                int sparkY = barY + rng.nextInt(BAR_H);
                if (rng.nextFloat() < 0.55f) {
                    // Белые или голубые искры разного размера
                    int sparkColor = (rng.nextFloat() < 0.35f) ? 0xFFFFFFFF : 0xFFADD8E6;
                    int sparkSize  = (rng.nextFloat() < 0.2f) ? 2 : 1;
                    gfx.fill(sparkX, sparkY, sparkX + sparkSize, sparkY + sparkSize, sparkColor);
                }
            }

            // ── Пульсирующая правая кромка заполнения ───────────────────────
            float edgePulse = (float)(Math.sin((now % 800) / 800.0 * Math.PI * 2) * 0.5 + 0.5);
            int edgeAlpha   = (int)(0x66 + edgePulse * 0x99); // 0x66..0xFF
            int edgeColor   = argb(edgeAlpha, 0xAD, 0xD8, 0xE6);
            // Правая кромка — 1px вертикальная линия у правого края заполнения
            gfx.fill(barRight - 1, barY, barRight, barBot, edgeColor);
        }

        // ── Внешняя рамка бара ────────────────────────────────────────────────
        // Верхняя и нижняя горизонтальные линии
        gfx.fill(barLeft - 1, barY - 1,  barRight + 1, barY,      0x66FFFFFF);
        gfx.fill(barLeft - 1, barBot,    barRight + 1, barBot + 1, 0x66FFFFFF);
        // Левая и правая вертикальные линии
        gfx.fill(barLeft - 1,  barY, barLeft,    barBot, 0x66FFFFFF);
        gfx.fill(barRight,     barY, barRight + 1, barBot, 0x66FFFFFF);
    }

    // ── Вспомогательные методы для цвета ─────────────────────────────────────

    /**
     * Линейная интерполяция между двумя целыми значениями канала цвета (0–255).
     *
     * @param a     начальное значение
     * @param b     конечное значение
     * @param t     коэффициент 0.0..1.0
     * @return      интерполированное значение
     */
    private static int lerpInt(int a, int b, float t) {
        return (int)(a + (b - a) * t);
    }

    /**
     * Собирает ARGB-цвет из четырёх компонентов (0–255 каждый).
     */
    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
