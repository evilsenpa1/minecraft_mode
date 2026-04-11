package com.example.mymod.client.screen;

import com.example.mymod.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Главное меню настроек мода.
 *
 * Визуальный стиль: тёмное фэнтези / магический гримуар.
 * Открывается клавишей G (можно переназначить в настройках управления).
 *
 * Чтобы добавить новую настройку:
 *   1. Добавь значение в ModConfig
 *   2. Добавь новую панель-секцию методом drawSection*()
 *   3. Увеличь WINDOW_HEIGHT если нужно больше места
 */
public class ModSettingsScreen extends Screen {

    // ─── Размеры окна ─────────────────────────────────────────────────────────
    private static final int WINDOW_W = 340;
    private static final int WINDOW_H = 230;

    // ─── Цветовая палитра ─────────────────────────────────────────────────────
    // Фон
    private static final int C_WIN_BG       = 0xFF08060F;  // почти чёрный с фиолетовым оттенком
    private static final int C_WIN_INNER    = 0xFF0D0B1A;  // тёмно-фиолетовый
    // Золотые рамки
    private static final int C_GOLD         = 0xFFC9A227;  // старое золото
    private static final int C_GOLD_DIM     = 0xFF6B4E11;  // тёмное золото
    private static final int C_GOLD_BRIGHT  = 0xFFFFD966;  // яркое золото (hover)
    // Фиолетовые акценты (панели, кнопки выбора)
    private static final int C_PANEL_BG     = 0xFF0F0C1E;  // фон секции
    private static final int C_PANEL_BORDER = 0xFF3A2860;  // граница секции
    private static final int C_BTN_SEL      = 0xFF2B1858;  // фон выбранной кнопки
    private static final int C_BTN_NORM     = 0xFF160F2C;  // фон обычной кнопки
    private static final int C_BTN_HOVER    = 0xFF1F1640;  // фон при наведении
    private static final int C_BTN_BRD_SEL  = 0xFFB040FF;  // граница выбранной
    private static final int C_BTN_BRD_HOV  = 0xFF7030CC;  // граница при наведении
    private static final int C_BTN_BRD_NORM = 0xFF3D2B6B;  // граница обычная
    // Текст
    private static final int C_TEXT_TITLE   = 0xFFE8C96D;  // золотой заголовок
    private static final int C_TEXT_SUB     = 0xFF6B5C8A;  // приглушённый фиолетовый
    private static final int C_TEXT_SECTION = 0xFFB09060;  // заголовок секции
    private static final int C_TEXT_LABEL   = 0xFF9080A8;  // подпись поля
    private static final int C_TEXT_BTN_SEL = 0xFFE0B0FF;  // текст выбранной кнопки
    private static final int C_TEXT_BTN_HOV = 0xFFCCA0EE;  // текст при наведении
    private static final int C_TEXT_BTN_NRM = 0xFF7050A0;  // текст обычный

    // ─── Координаты окна (вычисляются в init) ────────────────────────────────
    private int wLeft, wTop;

    // ─── Состояние настроек ───────────────────────────────────────────────────
    private ModConfig.ManaHudStyle hudStyle;

    // ─── Ссылки на кнопки (чтобы обновлять selected) ─────────────────────────
    private MagicToggleButton btnIcon;
    private MagicToggleButton btnBar;

    // ─── Анимация ─────────────────────────────────────────────────────────────
    // Координаты "звёзд" относительно wLeft/wTop (пары x,y)
    private static final int[] STARS = {
        18,  9,  35, 22,  58, 11,  82, 30, 103,  8,
       140, 15, 175,  5, 210, 25, 250, 12, 290, 20,
       315, 35, 322,  8, 308, 45, 332, 18,  10, 40,
         5, 60, 330, 60, 170, 210, 80, 205, 260, 210
    };

    public ModSettingsScreen() {
        super(Component.empty());
    }

    // ─── Инициализация ───────────────────────────────────────────────────────

    @Override
    protected void init() {
        wLeft = (width  - WINDOW_W) / 2;
        wTop  = (height - WINDOW_H) / 2;

        // Читаем актуальное значение конфига при каждом открытии
        hudStyle = ModConfig.MANA_HUD_STYLE.get();

        // Кнопки выбора стиля HUD
        int btnW    = 118;
        int btnH    = 22;
        int gap     = 12;
        int totalBW = btnW * 2 + gap;
        int btnStartX = wLeft + (WINDOW_W - totalBW) / 2;
        int btnY      = wTop + 128;

        btnIcon = new MagicToggleButton(
                btnStartX, btnY, btnW, btnH,
                "◆   Значок",
                hudStyle == ModConfig.ManaHudStyle.ICON,
                () -> selectHudStyle(ModConfig.ManaHudStyle.ICON)
        );

        btnBar = new MagicToggleButton(
                btnStartX + btnW + gap, btnY, btnW, btnH,
                "▬   Полоска",
                hudStyle == ModConfig.ManaHudStyle.BAR,
                () -> selectHudStyle(ModConfig.ManaHudStyle.BAR)
        );

        // Кнопка закрытия
        int closeBtnW = 130;
        MagicActionButton btnClose = new MagicActionButton(
                wLeft + (WINDOW_W - closeBtnW) / 2,
                wTop + WINDOW_H - 40,
                closeBtnW, 22,
                "✦   ЗАКРЫТЬ   ✦",
                this::onClose
        );

        addRenderableWidget(btnIcon);
        addRenderableWidget(btnBar);
        addRenderableWidget(btnClose);
    }

    /** Применить выбор стиля HUD и обновить кнопки */
    private void selectHudStyle(ModConfig.ManaHudStyle style) {
        hudStyle = style;
        ModConfig.MANA_HUD_STYLE.set(style);
        btnIcon.setSelected(style == ModConfig.ManaHudStyle.ICON);
        btnBar.setSelected(style == ModConfig.ManaHudStyle.BAR);
    }

    // ─── Рендер ──────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mx, int my, float dt) {
        // Стандартное затемнение фона (блюр или текстура dirt)
        renderBackground(gfx);

        drawWindow(gfx);
        drawTitle(gfx);
        drawManaSection(gfx);
        drawStars(gfx);
        drawRuneDecor(gfx);

        // Виджеты (кнопки) — рисуем последними, поверх фона
        super.render(gfx, mx, my, dt);
    }

    // ─── Рисование окна ──────────────────────────────────────────────────────

    /**
     * Основная панель окна: фон + двойная рамка + угловые украшения.
     */
    private void drawWindow(GuiGraphics gfx) {
        int l = wLeft, t = wTop, r = wLeft + WINDOW_W, b = wTop + WINDOW_H;

        // Тень под окном (небольшое смещение)
        gfx.fill(l + 4, t + 4, r + 4, b + 4, 0x66000000);

        // Внешний тёмный ободок
        gfx.fill(l - 2, t - 2, r + 2, b + 2, 0xFF030208);

        // Внешняя золотая рамка
        fillBorder(gfx, l - 1, t - 1, r + 1, b + 1, 1, C_GOLD_DIM);

        // Основной фон окна
        gfx.fill(l, t, r, b, C_WIN_BG);

        // Внутренний тонкий фиолетовый градиент (имитация объёма)
        gfx.fill(l, t, r, t + 60, 0x18280A4E);  // верхняя часть чуть светлее

        // Внутренняя золотая рамка
        fillBorder(gfx, l, t, r, b, 1, C_GOLD);

        // Вторая внутренняя рамка (тонкая, тёмная)
        fillBorder(gfx, l + 2, t + 2, r - 2, b - 2, 1, 0xFF1A1030);

        // Угловые украшения
        drawCorners(gfx, l, t, r, b);

        // Горизонтальная линия-разделитель под заголовком
        int divY = t + 46;
        gfx.fill(l + 10, divY,     r - 10, divY + 1, C_GOLD_DIM);
        gfx.fill(l + 10, divY + 1, r - 10, divY + 2, 0x33C9A227);
        // Ромб на середине разделителя
        drawDiamond(gfx, l + WINDOW_W / 2, divY + 1, 4, C_GOLD);
    }

    /**
     * Угловые украшения: L-образные золотые линии + маленький ромб в каждом углу.
     */
    private void drawCorners(GuiGraphics gfx, int l, int t, int r, int b) {
        int len = 12; // длина "усика" в углу
        int g   = C_GOLD;

        // Верхний левый
        gfx.fill(l + 3, t + 3, l + 3 + len, t + 4, g);
        gfx.fill(l + 3, t + 3, l + 4,       t + 3 + len, g);
        drawDiamond(gfx, l + 3, t + 3, 2, g);

        // Верхний правый
        gfx.fill(r - 3 - len, t + 3, r - 3, t + 4, g);
        gfx.fill(r - 4,       t + 3, r - 3, t + 3 + len, g);
        drawDiamond(gfx, r - 3, t + 3, 2, g);

        // Нижний левый
        gfx.fill(l + 3, b - 4, l + 3 + len, b - 3, g);
        gfx.fill(l + 3, b - 3 - len, l + 4, b - 3, g);
        drawDiamond(gfx, l + 3, b - 3, 2, g);

        // Нижний правый
        gfx.fill(r - 3 - len, b - 4, r - 3, b - 3, g);
        gfx.fill(r - 4,       b - 3 - len, r - 3, b - 3, g);
        drawDiamond(gfx, r - 3, b - 3, 2, g);
    }

    /**
     * Заголовок окна: большой текст с золотым свечением + подзаголовок.
     */
    private void drawTitle(GuiGraphics gfx) {
        int cx = wLeft + WINDOW_W / 2;
        int ty = wTop + 12;

        // Фон заголовка (слегка тёмнее основного окна)
        gfx.fill(wLeft + 14, wTop + 5, wLeft + WINDOW_W - 14, wTop + 40, 0x55000000);
        // Тонкая рамка у фона заголовка
        fillBorder(gfx, wLeft + 14, wTop + 5, wLeft + WINDOW_W - 14, wTop + 40, 1, 0xFF1E1438);

        // Свечение вокруг текста (несколько размытых слоёв)
        gfx.drawCenteredString(font, "✦  ГРИМУАР НАСТРОЕК  ✦", cx - 1, ty,     0x444A1D7A);
        gfx.drawCenteredString(font, "✦  ГРИМУАР НАСТРОЕК  ✦", cx + 1, ty,     0x444A1D7A);
        gfx.drawCenteredString(font, "✦  ГРИМУАР НАСТРОЕК  ✦", cx,     ty - 1, 0x444A1D7A);
        gfx.drawCenteredString(font, "✦  ГРИМУАР НАСТРОЕК  ✦", cx,     ty + 1, 0x444A1D7A);
        // Основной текст заголовка — золотой
        gfx.drawCenteredString(font, "✦  ГРИМУАР НАСТРОЕК  ✦", cx, ty, C_TEXT_TITLE);

        // Подзаголовок
        gfx.drawCenteredString(font, "~ Arcane Configuration ~", cx, ty + 14, C_TEXT_SUB);
    }

    /**
     * Панель секции "Отображение маны":
     * рамка, заголовок секции, подпись поля.
     */
    private void drawManaSection(GuiGraphics gfx) {
        int pL = wLeft + 16;
        int pT = wTop  + 56;
        int pR = wLeft + WINDOW_W - 16;
        int pB = wTop  + 162;

        // Фон панели
        gfx.fill(pL, pT, pR, pB, C_PANEL_BG);

        // Внешняя граница панели
        fillBorder(gfx, pL, pT, pR, pB, 1, C_PANEL_BORDER);

        // Внутренняя тонкая рамка (чуть темнее)
        fillBorder(gfx, pL + 1, pT + 1, pR - 1, pB - 1, 1, 0xFF1A1232);

        // Ромбики по углам панели
        drawDiamond(gfx, pL + 1, pT + 1, 2, C_PANEL_BORDER);
        drawDiamond(gfx, pR - 1, pT + 1, 2, C_PANEL_BORDER);
        drawDiamond(gfx, pL + 1, pB - 1, 2, C_PANEL_BORDER);
        drawDiamond(gfx, pR - 1, pB - 1, 2, C_PANEL_BORDER);

        // Заголовок секции (с ромбами по бокам)
        int cx = wLeft + WINDOW_W / 2;
        gfx.drawCenteredString(font, "◈  ОТОБРАЖЕНИЕ МАНЫ  ◈", cx, pT + 9, C_TEXT_SECTION);

        // Разделитель под заголовком
        gfx.fill(pL + 6, pT + 22, pR - 6, pT + 23, 0xFF2A1E4A);

        // Подпись поля "Стиль интерфейса:"
        gfx.drawString(font, "Стиль интерфейса:", pL + 12, pT + 29, C_TEXT_LABEL, false);

        // Маленький ромб-акцент перед подписью
        drawDiamond(gfx, pL + 7, pT + 33, 2, 0xFF4A2870);
    }

    /**
     * Анимированные мерцающие "звёзды" на фоне окна.
     * Каждая точка мерцает по своей фазе синусоиды.
     */
    private void drawStars(GuiGraphics gfx) {
        long time = System.currentTimeMillis();
        for (int i = 0; i < STARS.length; i += 2) {
            int sx = wLeft + STARS[i];
            int sy = wTop  + STARS[i + 1];

            // Уникальная фаза для каждой звезды
            double phase = (time / 1200.0 + i * 0.41) % (Math.PI * 2);
            int alpha    = (int)(60 + 80 * Math.sin(phase));

            // Чередуем цвет: золотые и голубые звёзды
            int baseColor = (i % 4 == 0) ? 0x00C9A227 : 0x003060FF;
            gfx.fill(sx, sy, sx + 1, sy + 1, (alpha << 24) | baseColor);
        }
    }

    /**
     * Декоративные символы-руны по бокам окна.
     * Тихо мерцают, создавая атмосферу магии.
     */
    private void drawRuneDecor(GuiGraphics gfx) {
        long time  = System.currentTimeMillis();
        int  alpha = (int)(90 + 50 * Math.sin(time / 800.0));
        int  color = (alpha << 24) | 0x4A1D7A;

        // Левый столбец рун
        String[] leftRunes  = {"ᚠ", "ᚢ", "ᚦ"};
        String[] rightRunes = {"ᚱ", "ᚲ", "ᚷ"};

        for (int i = 0; i < leftRunes.length; i++) {
            int ry = wTop + 60 + i * 28;
            gfx.drawString(font, leftRunes[i],  wLeft + 4,            ry, color, false);
            gfx.drawString(font, rightRunes[i], wLeft + WINDOW_W - 12, ry, color, false);
        }
    }

    // ─── Вспомогательные методы рисования ────────────────────────────────────

    /**
     * Нарисовать прямоугольный контур (border) заданной толщины.
     *
     * @param gfx       GuiGraphics
     * @param l, t, r, b   координаты (left/top/right/bottom)
     * @param thickness толщина линии в пикселях
     * @param color     ARGB цвет
     */
    static void fillBorder(GuiGraphics gfx, int l, int t, int r, int b,
                            int thickness, int color) {
        gfx.fill(l,              t,              r,              t + thickness, color); // верх
        gfx.fill(l,              b - thickness,  r,              b,             color); // низ
        gfx.fill(l,              t,              l + thickness,  b,             color); // лево
        gfx.fill(r - thickness,  t,              r,              b,             color); // право
    }

    /**
     * Нарисовать ромб (diamond shape) горизонтальными полосками.
     *
     * @param cx, cy  центр ромба
     * @param size    полуразмер (ромб будет size*2 × size*2 пикселей)
     * @param color   ARGB цвет
     */
    static void drawDiamond(GuiGraphics gfx, int cx, int cy, int size, int color) {
        for (int row = 0; row < size * 2; row++) {
            int y    = cy - size + row;
            int half = (row < size) ? row : (size * 2 - 1 - row);
            gfx.fill(cx - half, y, cx + half + 1, y + 1, color);
        }
    }

    // ─── Прочие методы экрана ────────────────────────────────────────────────

    /**
     * false = не ставить мир на паузу при открытом меню.
     * Игрок видит как восстанавливается мана в реальном времени.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =========================================================================
    // Кнопка переключения (ICON / BAR) — фиолетовый стиль
    // =========================================================================

    /**
     * Кнопка с двумя состояниями: выбрана / не выбрана.
     * Используется для выбора одного варианта из группы.
     */
    private class MagicToggleButton extends AbstractButton {

        private boolean selected;
        private final Runnable action;

        MagicToggleButton(int x, int y, int w, int h,
                          String label, boolean selected, Runnable action) {
            super(x, y, w, h, Component.literal(label));
            this.selected = selected;
            this.action   = action;
        }

        public void setSelected(boolean sel) {
            this.selected = sel;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        public void renderWidget(GuiGraphics gfx, int mx, int my, float dt) {
            int l = getX(), t = getY(), r = l + getWidth(), b = t + getHeight();

            // Фон кнопки
            int bg = selected ? C_BTN_SEL : (isHovered() ? C_BTN_HOVER : C_BTN_NORM);
            gfx.fill(l, t, r, b, bg);

            // Граница кнопки
            int border = selected  ? C_BTN_BRD_SEL
                       : isHovered() ? C_BTN_BRD_HOV
                       : C_BTN_BRD_NORM;
            fillBorder(gfx, l, t, r, b, 1, border);

            // Блик сверху для выбранной кнопки
            if (selected) {
                gfx.fill(l + 1, t + 1, r - 1, t + 3, 0x55B040FF);
                gfx.fill(l + 1, b - 2, r - 1, b - 1, 0x22B040FF);
            }

            // Текст кнопки
            int tc = selected    ? C_TEXT_BTN_SEL
                   : isHovered() ? C_TEXT_BTN_HOV
                   : C_TEXT_BTN_NRM;
            gfx.drawCenteredString(font, getMessage(),
                    l + getWidth() / 2, t + (getHeight() - 8) / 2, tc);
        }
    }

    // =========================================================================
    // Кнопка действия (Закрыть и пр.) — золотой стиль
    // =========================================================================

    /**
     * Кнопка действия в золотом стиле (для "Закрыть" и аналогичных действий).
     */
    private class MagicActionButton extends AbstractButton {

        private final Runnable action;

        MagicActionButton(int x, int y, int w, int h, String label, Runnable action) {
            super(x, y, w, h, Component.literal(label));
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        public void renderWidget(GuiGraphics gfx, int mx, int my, float dt) {
            int l = getX(), t = getY(), r = l + getWidth(), b = t + getHeight();

            // Фон — тёмно-коричневый
            int bg = isHovered() ? 0xFF1C1408 : 0xFF110D05;
            gfx.fill(l, t, r, b, bg);

            // Граница — золотая
            int border = isHovered() ? C_GOLD : C_GOLD_DIM;
            fillBorder(gfx, l, t, r, b, 1, border);

            // Блик при наведении
            if (isHovered()) {
                gfx.fill(l + 1, t + 1, r - 1, t + 2, 0x33FFD966);
            }

            // Текст
            int tc = isHovered() ? C_GOLD_BRIGHT : C_GOLD;
            gfx.drawCenteredString(font, getMessage(),
                    l + getWidth() / 2, t + (getHeight() - 8) / 2, tc);
        }
    }
}
