package com.example.mymod.client;

import com.example.mymod.capability.ManaCapability;
import com.example.mymod.config.ModConfig;
import com.example.mymod.skill.PlayerSkillsCapability;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
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

    // ─── Текстуры HUD Кристального Столпа ────────────────────────────────────

    // Тёмный фон пустого вертикального бара (20×185)
    private static final ResourceLocation TEX_MANA_BG    =
            new ResourceLocation("mymod", "textures/gui/mana_bar_bg.png");
    // Светящийся портальный слой заливки (20×185)
    private static final ResourceLocation TEX_MANA_FILL  =
            new ResourceLocation("mymod", "textures/gui/mana_bar_fill.png");
    // Золотая рамка + кристальный декор (32×256)
    private static final ResourceLocation TEX_MANA_FRAME =
            new ResourceLocation("mymod", "textures/gui/mana_bar_frame.png");

    // Размеры текстур (должны совпадать с gen_textures.py)
    private static final int TEX_FILL_W  = 20,  TEX_FILL_H  = 185;
    private static final int TEX_FRAME_W = 32,  TEX_FRAME_H = 256;

    // Смещение внутренней зоны в текстуре рамки:
    //   x=3..22 — горизонтальная прозрачная зона (золотые inner-бордеры на x=3 и x=22)
    //   y=48..232 — вертикальная прозрачная зона (185px = BAR_H)
    private static final int FRAME_INNER_X = 3;
    private static final int FRAME_INNER_Y = 48;

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
    // СТИЛЬ 1: ICON — «Орб Магии» — плашка с шаром маны, искрами и гемом
    // =========================================================================

    /**
     * Рисует горизонтальную магическую плашку:
     *   — тёмно-синий фон с золотой рамкой и угловыми акцентами
     *   — анимированный светящийся орб слева (синий/фиолетовый шар с глянцем)
     *   — мерцающие звёздочки на фоне и вокруг орба
     *   — текст «75 / 100» и «75%» по центру
     *   — маленький кристалл-гем справа
     *
     * Позиция: правый нижний угол, над хотбаром.
     */
    private static void renderIconStyle(GuiGraphics gfx, Minecraft mc,
                                         int current, int max,
                                         int screenW, int screenH) {
        // ── Геометрия ────────────────────────────────────────────────────────
        final int PANEL_W    = 185; // ширина плашки
        final int PANEL_H    = 32;  // высота плашки
        final int FRAME_T    = 2;   // толщина золотой рамки
        final int MARGIN_R   = 14;  // отступ от правого края экрана
        final int MARGIN_BOT = 68;  // отступ от нижнего края (выше хотбара)
        final int ORB_R      = 12;  // радиус орба маны

        // Левый верхний угол плашки
        int px  = screenW - PANEL_W - MARGIN_R;
        int py  = screenH - PANEL_H - MARGIN_BOT;
        int px2 = px + PANEL_W;
        int py2 = py + PANEL_H;

        // Центр орба — у левого края внутри рамки
        int orbCX = px + FRAME_T + 4 + ORB_R;
        int orbCY = py + PANEL_H / 2;

        long   now = System.currentTimeMillis();
        double PI2 = Math.PI * 2;

        // ── 1. Внешнее пульсирующее свечение орба (рисуем ДО рамки) ─────────
        float glowPulse = (float)(Math.sin((now % 1800) / 1800.0 * PI2) * 0.5 + 0.5);
        drawCircleFilled(gfx, orbCX, orbCY, ORB_R + 11, argb((int)(0x22 + glowPulse * 0x1A), 0x44, 0xAA, 0xFF));
        drawCircleFilled(gfx, orbCX, orbCY, ORB_R + 6,  argb((int)(0x18 + glowPulse * 0x14), 0x55, 0xCC, 0xFF));

        // ── 2. Золотая рамка с тёмным фоном ──────────────────────────────────
        gfx.fill(px - 1, py - 1, px2 + 1, py2 + 1, 0xFF3A2508); // внешний тёмный обвод
        gfx.fill(px,     py,     px2,     py2,     0xFFCC9820); // основное золото
        gfx.fill(px + 1, py + 1, px2 - 1, py2 - 1, 0xFFD4A830); // внутреннее золото (светлее)
        // Тёмно-синий фон внутри рамки
        gfx.fill(px + FRAME_T, py + FRAME_T, px2 - FRAME_T, py2 - FRAME_T, 0xFF060A1E);
        // Бликовые линии рамки
        gfx.fill(px, py, px2, py + 1, 0xFFFFE060);              // верхний блик
        gfx.fill(px, py, px + 1, py2, 0xFFFFE060);              // левый блик
        gfx.fill(px, py2 - 1, px2, py2, 0xFF7A5210);            // нижняя тень
        gfx.fill(px2 - 1, py, px2, py2, 0xFF7A5210);            // правая тень
        // Угловые золотые акценты (3×3)
        gfx.fill(px - 1, py - 1, px + 3,  py + 3,  0xFFFFD700);
        gfx.fill(px2 - 3, py - 1, px2 + 1, py + 3,  0xFFFFD700);
        gfx.fill(px - 1, py2 - 3, px + 3,  py2 + 1, 0xFFFFD700);
        gfx.fill(px2 - 3, py2 - 3, px2 + 1, py2 + 1, 0xFFFFD700);

        // ── 3. Фоновые звёздочки-искры внутри плашки ─────────────────────────
        int innerX1 = px + FRAME_T + 1;
        int innerX2 = px2 - FRAME_T - 1;
        int innerY1 = py + FRAME_T + 1;
        int innerY2 = py2 - FRAME_T - 1;
        Random starRng = new Random((now / 700) * 6133L);
        for (int i = 0; i < 14; i++) {
            int sx = innerX1 + starRng.nextInt(innerX2 - innerX1);
            int sy = innerY1 + starRng.nextInt(innerY2 - innerY1);
            if (starRng.nextFloat() < 0.45f) {
                boolean bright   = starRng.nextFloat() < 0.30f;
                boolean isPurple = starRng.nextBoolean();
                int starA = bright ? 0xEE : 0x77;
                int starColor = isPurple
                    ? argb(starA, 0xBB, 0x66, 0xFF)
                    : argb(starA, 0x66, 0xCC, 0xFF);
                gfx.fill(sx, sy, sx + 1, sy + 1, starColor);
                if (bright) {
                    // 4-конечная звёздочка
                    gfx.fill(sx - 1, sy, sx + 2, sy + 1, argb(0x44, 0xFF, 0xFF, 0xFF));
                    gfx.fill(sx, sy - 1, sx + 1, sy + 2, argb(0x44, 0xFF, 0xFF, 0xFF));
                }
            }
        }

        // ── 4. Тело орба (попиксельный рендер по дистанции от центра) ────────
        float colorShift  = (float)(Math.sin((now % 3200) / 3200.0 * PI2) * 0.5 + 0.5);
        float pulseBright = (float)(Math.sin((now % 1400) / 1400.0 * PI2) * 0.12 + 0.88);

        for (int dy = -ORB_R; dy <= ORB_R; dy++) {
            for (int dx = -ORB_R; dx <= ORB_R; dx++) {
                double dist = Math.sqrt((double)dx * dx + (double)dy * dy);
                if (dist > ORB_R) continue;
                float t = (float)(dist / ORB_R);           // 0=центр, 1=край
                float brightness = (1.0f - t * t) * pulseBright;
                // Верхняя полусфера получает лёгкое осветление — эффект объёма
                float topBoost = Math.max(0, (float)(-dy) / ORB_R) * 0.15f;

                int r = Math.min(255, (int)(lerpInt(lerpInt(0x0C, 0x55, colorShift), 0xFF, brightness * 0.75f + topBoost)));
                int g = Math.min(255, (int)(lerpInt(lerpInt(0x0E, 0x1A, colorShift), 0xCC, brightness * 0.60f + topBoost)));
                int b = Math.min(255, (int)(lerpInt(0x88, 0xFF, brightness * 0.92f + topBoost * 0.5f)));

                gfx.fill(orbCX + dx, orbCY + dy, orbCX + dx + 1, orbCY + dy + 1, argb(0xFF, r, g, b));
            }
        }

        // ── 5. Глянцевый белый блик (верхний левый) ──────────────────────────
        float blinkPhase = (float)(Math.sin((now % 2200) / 2200.0 * PI2) * 0.25 + 0.75);
        float glintR = ORB_R * 0.52f;
        for (int dy = -ORB_R + 2; dy < 0; dy++) {
            for (int dx = -ORB_R + 2; dx < 0; dx++) {
                double dist = Math.sqrt((double)dx * dx + (double)dy * dy);
                if (dist > glintR) continue;
                float t = (float)(dist / glintR);
                int a = (int)((1.0f - t) * 155 * blinkPhase);
                if (a < 5) continue;
                gfx.fill(orbCX + dx, orbCY + dy, orbCX + dx + 1, orbCY + dy + 1, argb(a, 0xFF, 0xFF, 0xFF));
            }
        }

        // ── 6. Розово-маджентовый блик (нижний правый — как на скриншоте) ────
        float pinkR = ORB_R * 0.42f;
        for (int dy = 1; dy <= ORB_R - 2; dy++) {
            for (int dx = 1; dx <= ORB_R - 2; dx++) {
                double dist = Math.sqrt((double)dx * dx + (double)dy * dy);
                if (dist > pinkR) continue;
                float t = (float)(dist / pinkR);
                int a = (int)((1.0f - t) * 80);
                if (a < 5) continue;
                gfx.fill(orbCX + dx, orbCY + dy, orbCX + dx + 1, orbCY + dy + 1, argb(a, 0xFF, 0x44, 0xBB));
            }
        }

        // ── 7. Мерцающие искры вокруг орба ───────────────────────────────────
        int[][] sparkOffsets = {
            {-ORB_R - 5, -2}, {ORB_R + 6, -ORB_R - 3},
            {-ORB_R - 7, ORB_R - 1}, {4, -ORB_R - 5},
            {-3, ORB_R + 5},  {ORB_R + 4, 3}
        };
        double[] sparkFreq = {1.1, 0.75, 1.35, 0.9, 1.55, 0.8};
        for (int i = 0; i < sparkOffsets.length; i++) {
            float sp = (float)(Math.sin((now / 480.0 * sparkFreq[i] + i * 1.15) * Math.PI) * 0.5 + 0.5);
            if (sp > 0.22f) {
                int sA = (int)(sp * 210);
                int sC = (i % 2 == 0)
                    ? argb(sA, 0xCC, 0x77, 0xFF) // фиолетовая
                    : argb(sA, 0x55, 0xCC, 0xFF); // голубая
                drawStarSparkle(gfx, orbCX + sparkOffsets[i][0], orbCY + sparkOffsets[i][1],
                    sp > 0.65f ? 2 : 1, sC);
            }
        }

        // ── 8. Текст маны ─────────────────────────────────────────────────────
        // Левая граница текстовой зоны — сразу после орба
        int textX  = orbCX + ORB_R + 8;
        int textY1 = py + PANEL_H / 2 - mc.font.lineHeight - 1; // строка 1 («XX / MAX»)
        int textY2 = py + PANEL_H / 2 + 1;                       // строка 2 («XX%»)

        int percent = (max > 0) ? (current * 100 / max) : 0;

        // Строка 1: «текущая / максимум»
        String numbers = current + " / " + max;
        gfx.drawString(mc.font, numbers, textX + 1, textY1 + 1, 0x33000000, false); // тень
        gfx.drawString(mc.font, numbers, textX,     textY1,     COLOR_TEXT_MAIN, false);

        // Строка 2: проценты
        String pctText = percent + "%";
        gfx.drawString(mc.font, pctText, textX + 1, textY2 + 1, 0x33000000, false); // тень
        gfx.drawString(mc.font, pctText, textX,     textY2,     COLOR_TEXT_PERCENT, false);

        // ── 9. Кристалл-гем справа ────────────────────────────────────────────
        int gemCX = px2 - FRAME_T - 9;
        int gemCY = py + PANEL_H / 2;
        drawGem(gfx, gemCX, gemCY, now);
    }

    /**
     * Заливает круг (окружность) пикселями по дистанции от центра.
     * Используется для многослойного свечения орба.
     *
     * @param cx     центр по X
     * @param cy     центр по Y
     * @param radius радиус круга
     * @param color  ARGB-цвет
     */
    private static void drawCircleFilled(GuiGraphics gfx, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            // Быстрое исключение строк вне круга без sqrt
            int maxDx = (int)Math.sqrt(Math.max(0, (double)radius * radius - (double)dy * dy));
            if (maxDx == 0) continue;
            gfx.fill(cx - maxDx, cy + dy, cx + maxDx + 1, cy + dy + 1, color);
        }
    }

    /**
     * Рисует маленький пиксельный кристалл-гем в виде восьмиугольника
     * с анимированным синим/фиолетовым цветом и боковым бликом.
     *
     * @param cx  центр по X
     * @param cy  центр по Y
     * @param now время в мс (для анимации)
     */
    private static void drawGem(GuiGraphics gfx, int cx, int cy, long now) {
        float gemPulse = (float)(Math.sin((now % 2100) / 2100.0 * Math.PI * 2) * 0.5 + 0.5);
        int r = lerpInt(0x44, 0x88, gemPulse);
        int g = lerpInt(0x22, 0x55, gemPulse);
        int b = 0xFF;

        int gemMain  = argb(0xFF, r, g, b);
        int gemShine = argb(0xFF, Math.min(255, r + 80), Math.min(255, g + 50), 0xFF);
        int gemDark  = argb(0xFF, Math.max(0, r - 30), Math.max(0, g - 15), 0xBB);

        // Свечение вокруг гема
        gfx.fill(cx - 7, cy - 7, cx + 8, cy + 8,
            argb((int)(0x18 + gemPulse * 0x22), r, g, b));

        // Форма гема (пиксельный восьмиугольник, высота 10, ширина 8):
        //   строки:  ширина
        //    -4:      2   (верхнее острие)
        //    -3:      4
        //    -2..+2:  8   (широкая часть)
        //    +3:      6
        //    +4:      4   (нижнее сужение)
        int[][] rows = {
            // {dy, x0_offset, width}  — x0 = cx - x0_offset
            {-4, 1, 2}, {-3, 2, 4}, {-2, 4, 8}, {-1, 4, 8},
            { 0, 4, 8}, { 1, 4, 8}, { 2, 4, 8},
            { 3, 3, 6}, { 4, 2, 4},
        };
        for (int[] row : rows) {
            int dy = row[0];
            int x0 = cx - row[1];
            int w  = row[2];
            gfx.fill(x0, cy + dy, x0 + w, cy + dy + 1, gemMain);
        }

        // Левый блик (вертикальная светлая полоска)
        for (int[] row : rows) {
            int dy = row[0];
            int x0 = cx - row[1];
            if (row[2] >= 3) {
                gfx.fill(x0, cy + dy, x0 + 2, cy + dy + 1, gemShine);
            }
        }

        // Нижняя тень (правый угол)
        for (int[] row : rows) {
            if (row[0] > 1) {
                int dy = row[0];
                int x0 = cx - row[1];
                int w  = row[2];
                gfx.fill(x0 + w - 2, cy + dy, x0 + w, cy + dy + 1, gemDark);
            }
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
     *
     * Слои рендера (снизу вверх):
     *   1. Пульсирующее внешнее свечение (программные полупрозрачные прямоугольники)
     *   2. Тёмный фон пустого бара (mana_bar_bg.png)
     *   3. Портальная заливка маны (mana_bar_fill.png), обрезается по уровню снизу вверх
     *   4. Золотая рамка + кристаллы (mana_bar_frame.png)
     *   5. Анимированные внешние звёздочки-искры
     *
     * Позиция: правый край экрана, вертикально по центру. Без цифр и процентов.
     */
    private static void renderVerticalBarStyle(GuiGraphics gfx,
                                                int current, int max,
                                                int screenW, int screenH) {
        // ── Геометрия ────────────────────────────────────────────────────────
        // BAR_W и BAR_H должны совпадать с TEX_FILL_W / TEX_FILL_H и gen_textures.py
        final int BAR_W        = TEX_FILL_W;   // 20px
        final int BAR_H        = TEX_FILL_H;   // 185px
        final int MARGIN_RIGHT = 22;            // отступ от правого края экрана

        // Позиция внутреннего бара на экране
        int barX   = screenW - MARGIN_RIGHT - BAR_W;
        int barY   = screenH / 2 - BAR_H / 2;
        int barBot = barY + BAR_H;

        // Позиция текстуры рамки на экране:
        //   frame x=FRAME_INNER_X выравнивается с barX
        //   frame y=FRAME_INNER_Y выравнивается с barY
        int frameX = barX - FRAME_INNER_X;
        int frameY = barY - FRAME_INNER_Y;

        // Приближённые границы видимой части рамки (для расчёта свечения).
        // Золотой бордер рамки — 3px с каждой стороны (совпадает с gen_textures.py).
        int frameL = barX - 3;
        int frameR = barX + BAR_W + 3;

        // Заполнение (снизу вверх)
        int fillH   = (max > 0) ? (current * BAR_H / max) : 0;
        int fillTop = barBot - fillH;

        // Время для анимации
        long   now = System.currentTimeMillis();
        double PI2 = Math.PI * 2;

        // ── 1. Пульсирующее внешнее свечение (программные полупрозрачные слои) ─
        // Учитываем расширение за счёт кристаллов: +FRAME_INNER_Y выше, +~20px ниже
        float glowPulse = (float)(Math.sin((now % 2000) / 2000.0 * PI2) * 0.5 + 0.5);
        int gA1 = (int)(0x26 + glowPulse * 0x1A);
        int gA2 = (int)(0x12 + glowPulse * 0x10);
        int gA3 = (int)(0x07 + glowPulse * 0x08);
        gfx.fill(frameL - 10, frameY,      frameR + 10, frameY + TEX_FRAME_H, argb(gA1, 0x20, 0x55, 0xFF));
        gfx.fill(frameL - 22, frameY - 12, frameR + 22, frameY + TEX_FRAME_H + 12, argb(gA2, 0x15, 0x40, 0xCC));
        gfx.fill(frameL - 36, frameY - 24, frameR + 36, frameY + TEX_FRAME_H + 24, argb(gA3, 0x08, 0x25, 0x99));

        // ── 2. Тёмный фон пустого бара (mana_bar_bg.png) ─────────────────────
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        gfx.blit(TEX_MANA_BG, barX, barY, 0, 0, BAR_W, BAR_H, TEX_FILL_W, TEX_FILL_H);

        // ── 3. Заливка маны (mana_bar_fill.png), обрезается снизу вверх ───────
        if (fillH > 0) {
            // Пульс яркости: ±7% (период 1.5 с)
            float bright = (float)(Math.sin((now % 1500) / 1500.0 * PI2) * 0.07 + 0.93);
            RenderSystem.setShaderColor(bright, bright, 1f, 1f);
            // Рисуем только нижнюю fillH часть текстуры (UV offset = TEX_FILL_H - fillH)
            int uvFillY = TEX_FILL_H - fillH;
            gfx.blit(TEX_MANA_FILL, barX, fillTop, 0, uvFillY, BAR_W, fillH, TEX_FILL_W, TEX_FILL_H);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            // Пульсирующая верхняя кромка заполнения (белая линия поверх текстуры)
            float ep = (float)(Math.sin((now % 700) / 700.0 * PI2) * 0.5 + 0.5);
            gfx.fill(barX,     fillTop,     barX + BAR_W,     fillTop + 1,
                    argb((int)(0x88 + ep * 0x77), 0xFF, 0xFF, 0xFF));
            gfx.fill(barX + 2, fillTop + 1, barX + BAR_W - 2, fillTop + 2,
                    argb((int)(ep * 0xAA), 0xCC, 0xEE, 0xFF));
        }

        // ── 4. Золотая рамка + кристаллы (mana_bar_frame.png) ────────────────
        // Рамка имеет прозрачность внутри — fill-текстура видна сквозь неё
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        gfx.blit(TEX_MANA_FRAME, frameX, frameY, 0, 0, TEX_FRAME_W, TEX_FRAME_H, TEX_FRAME_W, TEX_FRAME_H);

        // ── 5. Анимированные внешние звёздочки вокруг столпа ─────────────────
        int[][] starPos = {
            {barX - 16, barY + 18},  {barX + BAR_W + 14, barY + 32},
            {barX - 24, barY + 62},  {barX + BAR_W + 21, barY + 78},
            {barX - 18, barY + 108}, {barX + BAR_W + 16, barY + 120},
            {barX - 12, barY + 158}, {barX + BAR_W + 10, barY + 168},
            {barX - 14, barY - 38},  {barX + BAR_W + 12, barY - 22},
            {barX + BAR_W / 2 - 28, barY - 55}, {barX + BAR_W / 2 + 24, barY + BAR_H + 40},
        };
        double[] starFreq = {0.9, 1.1, 0.7, 1.3, 0.8, 1.05, 1.4, 0.6, 1.2, 0.85, 1.55, 0.75};
        for (int i = 0; i < starPos.length; i++) {
            float sp = (float)(Math.sin((now / 650.0 * starFreq[i] + i * 0.9) * Math.PI) * 0.5 + 0.5);
            if (sp > 0.20f) {
                int sA = (int)(sp * 215);
                int sC = (i % 3 == 2)
                    ? argb(sA, 0xCC, 0x88, 0xFF)  // фиолетовая
                    : argb(sA, 0x88, 0xDD, 0xFF);  // голубая
                drawStarSparkle(gfx, starPos[i][0], starPos[i][1], sp > 0.70f ? 3 : 2, sC);
            }
        }

        RenderSystem.disableBlend();
    }

    // ── Вспомогательные рисовальщики ─────────────────────────────────────────

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
