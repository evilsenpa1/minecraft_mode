package com.example.mymod.client.screen;

import com.example.mymod.client.screen.encyclopedia.EncyclopediaData;
import com.example.mymod.client.screen.encyclopedia.EncyclopediaEntry;
import com.example.mymod.client.screen.encyclopedia.EncyclopediaEntry.Line.Type;
import com.example.mymod.client.screen.encyclopedia.EncyclopediaTab;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Экран энциклопедии мода — "Гримуар Знаний".
 *
 * Структура экрана:
 *   ┌─ Заголовок + Поиск ──────────────────────────┐
 *   ├─ Вкладки (Лор / Мана / Заклинания / ...) ────┤
 *   ├─ Список статей ──┬─ Контент статьи ──────────┤
 *   │  (прокручиваем) │  (прокручиваем)            │
 *   ├──────────────────┴────────────────────────────┤
 *   └─ Подвал: навигация Пред / N/Total / След ─────┘
 *
 * Открывается клавишей B (переназначается в настройках управления).
 */
public class GrimoireScreen extends Screen {

    // ─── Размеры окна ────────────────────────────────────────────────────────
    private static final int W         = 420;
    private static final int H         = 272;
    private static final int HEADER_H  = 44;   // высота шапки
    private static final int TABS_H    = 20;   // высота строки вкладок
    private static final int MAIN_Y    = HEADER_H + TABS_H;  // y начала рабочей области
    private static final int FOOTER_H  = 22;  // оставляем константу, но footer скрыт
    private static final int MAIN_H    = H - MAIN_Y - 2; // рабочая область до самого низа
    private static final int LIST_W    = 120;  // ширина левой панели
    private static final int DIVIDER_W = 3;    // зазор между панелями
    private static final int MARGIN    = 8;
    private static final int ROW_H     = 13;   // высота строки списка
    private static final int SCROLL_W  = 5;    // ширина полосы прокрутки
    private static final int LINE_H    = 10;   // высота строки контента
    private static final int SEP_H     = 8;    // высота строки-разделителя

    // ─── Цветовая палитра ────────────────────────────────────────────────────
    private static final int C_WIN_BG       = 0xFF08060F;
    private static final int C_GOLD         = 0xFFC9A227;
    private static final int C_GOLD_DIM     = 0xFF6B4E11;
    private static final int C_GOLD_BRIGHT  = 0xFFFFD966;
    private static final int C_PANEL_BG     = 0xFF0F0C1E;
    private static final int C_PANEL_BRD    = 0xFF3A2860;
    private static final int C_DIVIDER      = 0xFF2A1E4A;
    // Вкладки
    private static final int C_TAB_ACTIVE   = 0xFF1E1438;
    private static final int C_TAB_HOVER    = 0xFF160F2A;
    private static final int C_TAB_NORMAL   = 0xFF0D0B18;
    private static final int C_TAB_TXT_ACT  = 0xFFE8C96D;
    private static final int C_TAB_TXT_HOV  = 0xFFCCBB80;
    private static final int C_TAB_TXT_NORM = 0xFF7060A0;
    // Список
    private static final int C_ENTRY_SEL    = 0xFF2B1858;
    private static final int C_ENTRY_HOV    = 0xFF1A1230;
    private static final int C_ENTRY_ACCENT = 0xFF8040D0;  // боковая полоска выбранной
    private static final int C_ENTRY_TXT    = 0xFFAA9080;
    private static final int C_ENTRY_TXT_S  = 0xFFEED080;  // выбранная
    private static final int C_ENTRY_TXT_H  = 0xFFBBAA70;  // под курсором
    // Скроллбар
    private static final int C_SCROLL_BG    = 0xFF0A0816;
    private static final int C_SCROLL_THUMB = 0xFF3A2860;
    // Контент
    private static final int C_TEXT_TITLE   = 0xFFE8C96D;
    private static final int C_TEXT_H1      = 0xFFE0C060;
    private static final int C_TEXT_H2      = 0xFFB09040;
    private static final int C_TEXT_BODY    = 0xFFCCBB99;
    private static final int C_TEXT_FLAVOR  = 0xFF887868;
    private static final int C_TIP_BG       = 0xFF0A1A0C;
    private static final int C_TIP_BRD      = 0xFF2A5030;
    private static final int C_TEXT_TIP     = 0xFF88C090;
    // Поиск
    private static final int C_SEARCH_BG    = 0xFF0A0816;

    // ─── Координаты панелей (вычисляются в init) ─────────────────────────────
    private int wLeft, wTop;
    private int listX, listY, listW2, listH;   // панель списка
    private int contX, contY, contW, contH;    // панель контента

    // ─── Состояние ───────────────────────────────────────────────────────────
    private int    activeTab      = 0;
    private int    selectedEntry  = 0;
    private int    listScroll     = 0;   // пиксельный сдвиг прокрутки списка
    private int    contentScroll  = 0;   // пиксельный сдвиг прокрутки контента
    private int    hoveredEntry   = -1;
    private int    totalContentH  = 0;   // суммарная высота контента (для скроллбара)

    // Перетаскивание ползунка скроллбара
    private boolean draggingList    = false;
    private boolean draggingContent = false;
    private double  dragStartY      = 0;
    private int     dragStartScroll = 0;

    private EditBox searchBox;

    /** Отфильтрованный список статей текущей вкладки */
    private List<EncyclopediaEntry> visible = new ArrayList<>();

    /**
     * Кэш строк для отрисовки контента текущей выбранной статьи.
     * Перестраивается при смене статьи/вкладки/поиска.
     */
    private List<RenderLine> contentCache = new ArrayList<>();
    private boolean contentDirty = true;

    // ─── Вспомогательный тип: готовая к рендеру строка контента ─────────────

    private enum LineKind { H1, H2, BODY, FLAVOR, SEPARATOR, TIP }

    private record RenderLine(LineKind kind, FormattedCharSequence text) {
        static RenderLine separator() {
            return new RenderLine(LineKind.SEPARATOR, FormattedCharSequence.EMPTY);
        }
    }

    // ─── Звёздочки на фоне (x,y относительно wLeft/wTop) ────────────────────
    private static final int[] STARS = {
        18,  9,  35, 22, 58, 11, 82, 30, 102, 7,
       370, 10, 390, 26, 408, 6, 412, 32,  10, 38
    };

    // ─────────────────────────────────────────────────────────────────────────

    public GrimoireScreen() {
        super(Component.empty());
    }

    // ─── Инициализация ───────────────────────────────────────────────────────

    @Override
    protected void init() {
        wLeft = (width  - W) / 2;
        wTop  = (height - H) / 2;

        // Координаты левой панели (список статей)
        listX  = wLeft + MARGIN;
        listY  = wTop  + MAIN_Y;
        listW2 = LIST_W;
        listH  = MAIN_H;

        // Координаты правой панели (контент)
        contX = listX + LIST_W + DIVIDER_W;
        contY = wTop  + MAIN_Y;
        contW = W - MARGIN * 2 - LIST_W - DIVIDER_W;
        contH = MAIN_H;

        // Поле поиска — в шапке справа
        int sbW = 128;
        int sbX = wLeft + W - MARGIN - sbW;
        int sbY = wTop  + 16;
        searchBox = new EditBox(font, sbX, sbY, sbW, 13, Component.empty());
        searchBox.setBordered(false);
        searchBox.setMaxLength(30);
        searchBox.setTextColor(0xFFD4CBB8);
        searchBox.setHint(Component.literal("✦ поиск...").withStyle(s -> s.withColor(0xFF4A3A60)));
        searchBox.setResponder(q -> {
            updateFilter();
            selectedEntry = 0;
            listScroll    = 0;
            contentDirty  = true;
        });
        addRenderableWidget(searchBox);

        updateFilter();
    }

    // ─── Рендер ──────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mx, int my, float dt) {
        renderBackground(gfx);

        hoveredEntry = getEntryAt(mx, my);
        if (contentDirty) buildContentCache();

        drawWindow(gfx);
        drawTitle(gfx);
        drawTabs(gfx, mx, my);
        drawListPanel(gfx, mx, my);
        drawContentPanel(gfx);
        drawStars(gfx);

        // Виджеты (EditBox поиска)
        super.render(gfx, mx, my, dt);

        // Рамка поиска поверх самого EditBox
        int sbX = wLeft + W - MARGIN - 128;
        int sbY = wTop  + 16;
        gfx.fill(sbX - 3, sbY - 3, sbX + 128 + 3, sbY + 13 + 3, C_SEARCH_BG);
        fillBorder(gfx, sbX - 3, sbY - 3, sbX + 128 + 3, sbY + 13 + 3, 1, C_GOLD_DIM);
        // Перерисовываем EditBox поверх нашего фона
        searchBox.render(gfx, mx, my, dt);
    }

    // ─── Отрисовка окна ──────────────────────────────────────────────────────

    private void drawWindow(GuiGraphics gfx) {
        int l = wLeft, t = wTop, r = wLeft + W, b = wTop + H;

        // Тень
        gfx.fill(l + 5, t + 5, r + 5, b + 5, 0x66000000);
        // Внешний тёмный ободок
        gfx.fill(l - 2, t - 2, r + 2, b + 2, 0xFF030208);
        // Внешняя золотая рамка
        fillBorder(gfx, l - 1, t - 1, r + 1, b + 1, 1, C_GOLD_DIM);
        // Основной фон
        gfx.fill(l, t, r, b, C_WIN_BG);
        // Тонкий фиолетовый градиент в верхней части
        gfx.fill(l, t, r, t + 80, 0x12280A4E);
        // Внутренняя золотая рамка
        fillBorder(gfx, l, t, r, b, 1, C_GOLD);
        // Тонкая тёмная рамка
        fillBorder(gfx, l + 2, t + 2, r - 2, b - 2, 1, 0xFF1A1030);
        // Угловые украшения
        drawCorners(gfx, l, t, r, b);
        // Разделитель под шапкой
        int dy = wTop + HEADER_H;
        gfx.fill(l + 8, dy, r - 8, dy + 1, C_GOLD_DIM);
        gfx.fill(l + 8, dy + 1, r - 8, dy + 2, 0x22C9A227);
        drawDiamond(gfx, l + W / 2, dy, 4, C_GOLD);
    }

    private void drawCorners(GuiGraphics gfx, int l, int t, int r, int b) {
        int n = 12, g = C_GOLD;
        // Верхний левый
        gfx.fill(l+3, t+3, l+3+n, t+4, g); gfx.fill(l+3, t+3, l+4, t+3+n, g);
        drawDiamond(gfx, l+3, t+3, 2, g);
        // Верхний правый
        gfx.fill(r-3-n, t+3, r-3, t+4, g); gfx.fill(r-4, t+3, r-3, t+3+n, g);
        drawDiamond(gfx, r-3, t+3, 2, g);
        // Нижний левый
        gfx.fill(l+3, b-4, l+3+n, b-3, g); gfx.fill(l+3, b-3-n, l+4, b-3, g);
        drawDiamond(gfx, l+3, b-3, 2, g);
        // Нижний правый
        gfx.fill(r-3-n, b-4, r-3, b-3, g); gfx.fill(r-4, b-3-n, r-3, b-3, g);
        drawDiamond(gfx, r-3, b-3, 2, g);
    }

    private void drawTitle(GuiGraphics gfx) {
        int tx = wLeft + MARGIN + 2;
        int ty = wTop + 10;
        // Свечение
        for (int d = -1; d <= 1; d += 2) {
            gfx.drawString(font, "✦  ГРИМУАР ЗНАНИЙ  ✦", tx + d, ty,     0x334A1D7A, false);
            gfx.drawString(font, "✦  ГРИМУАР ЗНАНИЙ  ✦", tx,     ty + d, 0x334A1D7A, false);
        }
        gfx.drawString(font, "✦  ГРИМУАР ЗНАНИЙ  ✦", tx, ty, C_TEXT_TITLE, false);
        gfx.drawString(font, "Encyclopedia Arcana",   tx + 4, ty + 12, 0xFF5A4A7A, false);
    }

    // ─── Вкладки ─────────────────────────────────────────────────────────────

    private void drawTabs(GuiGraphics gfx, int mx, int my) {
        List<EncyclopediaTab> tabs = EncyclopediaData.TABS;
        int tabY    = wTop + HEADER_H + 1;
        int tabH    = TABS_H - 2;
        int usableW = W - MARGIN * 2;
        int tabW    = usableW / tabs.size();
        int startX  = wLeft + MARGIN;

        for (int i = 0; i < tabs.size(); i++) {
            EncyclopediaTab tab = tabs.get(i);
            int tx = startX + i * tabW;
            int tr = tx + tabW;
            boolean active  = (i == activeTab);
            boolean hovered = mx >= tx && mx < tr && my >= tabY && my < tabY + tabH;

            // Фон
            int bg = active ? C_TAB_ACTIVE : (hovered ? C_TAB_HOVER : C_TAB_NORMAL);
            gfx.fill(tx, tabY, tr, tabY + tabH, bg);

            // Граница
            if (active) {
                fillBorder(gfx, tx, tabY, tr, tabY + tabH, 1, C_GOLD);
                // Стираем нижнюю линию — визуальное слияние с рабочей областью
                gfx.fill(tx + 1, tabY + tabH - 1, tr - 1, tabY + tabH, C_WIN_BG);
            } else {
                fillBorder(gfx, tx, tabY, tr, tabY + tabH, 1, C_PANEL_BRD);
            }

            // Текст
            int tc = active ? C_TAB_TXT_ACT : (hovered ? C_TAB_TXT_HOV : C_TAB_TXT_NORM);
            gfx.drawCenteredString(font, tab.icon() + "  " + tab.name(),
                    tx + tabW / 2, tabY + (tabH - 8) / 2, tc);
        }

        // Разделительная линия под вкладками
        int divY = tabY + tabH;
        gfx.fill(wLeft + MARGIN, divY, wLeft + W - MARGIN, divY + 1, C_GOLD_DIM);
    }

    // ─── Левая панель: список статей ─────────────────────────────────────────

    private void drawListPanel(GuiGraphics gfx, int mx, int my) {
        // Фон панели
        gfx.fill(listX, listY, listX + listW2, listY + listH, C_PANEL_BG);
        fillBorder(gfx, listX, listY, listX + listW2, listY + listH, 1, C_PANEL_BRD);

        // Вертикальный разделитель
        gfx.fill(contX - DIVIDER_W + 1, listY + 6,
                 contX - 1,             listY + listH - 6, C_DIVIDER);

        // ── Scissor: не рисуем за пределами панели ───────────────────────────
        gfx.enableScissor(listX + 1, listY + 1, listX + listW2 - 1, listY + listH - 1);

        int textMaxW = listW2 - SCROLL_W - 10;

        for (int i = 0; i < visible.size(); i++) {
            int rowY = listY + i * ROW_H - listScroll;
            if (rowY + ROW_H < listY || rowY > listY + listH) continue;

            boolean sel = (i == selectedEntry);
            boolean hov = (i == hoveredEntry);

            // Фон строки
            if (sel) {
                gfx.fill(listX + 1, rowY, listX + listW2 - SCROLL_W - 1, rowY + ROW_H, C_ENTRY_SEL);
                gfx.fill(listX + 1, rowY, listX + 3, rowY + ROW_H, C_ENTRY_ACCENT);
            } else if (hov) {
                gfx.fill(listX + 1, rowY, listX + listW2 - SCROLL_W - 1, rowY + ROW_H, C_ENTRY_HOV);
            }

            // Текст: для выбранного — бегущая строка, для остальных — обрезка
            String prefix = sel ? "▶ " : "  ";
            int tc = sel ? C_ENTRY_TXT_S : (hov ? C_ENTRY_TXT_H : C_ENTRY_TXT);
            String fullText = prefix + visible.get(i).title();
            int fullW = font.width(fullText);
            if (sel && fullW > textMaxW) {
                // Бегущая строка для выбранного длинного заголовка
                int marqueeOff = getMarqueeOffset(fullW, textMaxW);
                gfx.enableScissor(listX + 5, rowY, listX + listW2 - SCROLL_W - 2, rowY + ROW_H);
                gfx.drawString(font, fullText, listX + 5 - marqueeOff, rowY + 3, tc, false);
                gfx.disableScissor();
            } else {
                String clipped = font.plainSubstrByWidth(fullText, textMaxW);
                gfx.drawString(font, clipped, listX + 5, rowY + 3, tc, false);
            }
        }

        gfx.disableScissor();

        // Скроллбар списка
        drawScrollbar(gfx,
                listX + listW2 - SCROLL_W, listY + 1,
                SCROLL_W - 1, listH - 2,
                listScroll, visible.size() * ROW_H, listH);

        // Сообщение если поиск ничего не нашёл
        if (visible.isEmpty()) {
            gfx.drawCenteredString(font, "Ничего",
                    listX + listW2 / 2, listY + listH / 2 - 12, 0xFF4A3A6A);
            gfx.drawCenteredString(font, "не найдено",
                    listX + listW2 / 2, listY + listH / 2, 0xFF4A3A6A);
        }
    }

    // ─── Правая панель: контент статьи ───────────────────────────────────────

    private void drawContentPanel(GuiGraphics gfx) {
        // Фон
        gfx.fill(contX, contY, contX + contW, contY + contH, C_PANEL_BG);

        if (visible.isEmpty()) return;

        // ── Scissor ──────────────────────────────────────────────────────────
        gfx.enableScissor(contX + 1, contY + 1, contX + contW - 1, contY + contH - 1);

        int textX    = contX + 7;
        int textMaxW = contW - SCROLL_W - 16;
        int y        = contY + 5 - contentScroll;

        for (RenderLine line : contentCache) {
            int lineHeight = (line.kind() == LineKind.SEPARATOR) ? SEP_H : LINE_H;

            // Рендерим только видимые строки
            if (y + lineHeight >= contY && y <= contY + contH) {
                switch (line.kind()) {

                    case SEPARATOR -> {
                        int sepY = y + SEP_H / 2;
                        gfx.fill(textX, sepY, contX + contW - SCROLL_W - 8, sepY + 1, 0xFF2A1E4A);
                        drawDiamond(gfx, contX + contW / 2 - SCROLL_W / 2, sepY, 2, 0xFF3D2860);
                    }
                    case H1 ->
                        gfx.drawString(font, line.text(), textX, y, C_TEXT_H1, true);
                    case H2 ->
                        gfx.drawString(font, line.text(), textX + 4, y, C_TEXT_H2, true);
                    case BODY ->
                        gfx.drawString(font, line.text(), textX, y, C_TEXT_BODY, false);
                    case FLAVOR ->
                        gfx.drawString(font, line.text(), textX + 6, y, C_TEXT_FLAVOR, false);
                    case TIP -> {
                        int tipR = contX + contW - SCROLL_W - 8;
                        gfx.fill(textX - 2, y - 1, tipR, y + LINE_H - 1, C_TIP_BG);
                        fillBorder(gfx, textX - 2, y - 1, tipR, y + LINE_H - 1, 1, C_TIP_BRD);
                        gfx.drawString(font, line.text(), textX + 2, y, C_TEXT_TIP, false);
                    }
                }
            }
            y += lineHeight;
        }

        gfx.disableScissor();

        // Скроллбар контента
        drawScrollbar(gfx,
                contX + contW - SCROLL_W, contY + 1,
                SCROLL_W - 1, contH - 2,
                contentScroll, totalContentH, contH);
    }

    // ─── Подвал ──────────────────────────────────────────────────────────────

    private void drawFooter(GuiGraphics gfx, int mx, int my) {
        int footY = wTop + H - FOOTER_H;
        // Тонкий разделитель сверху подвала
        gfx.fill(wLeft + 8, footY, wLeft + W - 8, footY + 1, C_GOLD_DIM);

        int btnW = 80, btnH = 14;
        int btnY = footY + 4;
        int prevX = wLeft + W / 2 - btnW - 8;
        int nextX = wLeft + W / 2 + 8;

        boolean hasPrev = selectedEntry > 0;
        boolean hasNext = selectedEntry < visible.size() - 1;

        drawNavButton(gfx, prevX, btnY, btnW, btnH, "◄  Назад",
                mx, my, hasPrev);
        drawNavButton(gfx, nextX, btnY, btnW, btnH, "Вперёд  ►",
                mx, my, hasNext);

        // Позиция (N / Total)
        if (!visible.isEmpty()) {
            String pos = (selectedEntry + 1) + " / " + visible.size();
            gfx.drawCenteredString(font, pos, wLeft + W / 2, btnY + 3, 0xFF5A4A7A);
        }
    }

    private void drawNavButton(GuiGraphics gfx, int x, int y, int w, int h,
                                String label, int mx, int my, boolean enabled) {
        boolean hov = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        int bg     = hov     ? 0xFF1C1408 : 0xFF110D05;
        int border = enabled ? (hov ? C_GOLD : C_GOLD_DIM) : 0xFF2A1E30;
        int tc     = enabled ? (hov ? C_GOLD_BRIGHT : C_GOLD) : 0xFF3A2850;
        gfx.fill(x, y, x + w, y + h, bg);
        fillBorder(gfx, x, y, x + w, y + h, 1, border);
        gfx.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, tc);
    }

    // ─── Анимация ────────────────────────────────────────────────────────────

    private void drawStars(GuiGraphics gfx) {
        long time = System.currentTimeMillis();
        for (int i = 0; i < STARS.length; i += 2) {
            double phase = (time / 1200.0 + i * 0.41) % (Math.PI * 2);
            int    alpha = (int)(50 + 60 * Math.sin(phase));
            int    base  = (i % 4 == 0) ? 0x00C9A227 : 0x003060FF;
            gfx.fill(wLeft + STARS[i], wTop + STARS[i + 1],
                     wLeft + STARS[i] + 1, wTop + STARS[i + 1] + 1,
                     (alpha << 24) | base);
        }
    }

    // ─── Вспомогательные методы ──────────────────────────────────────────────

    private void drawScrollbar(GuiGraphics gfx, int x, int y, int w, int h,
                                int scroll, int total, int view) {
        if (total <= view) return;
        gfx.fill(x, y, x + w, y + h, C_SCROLL_BG);
        int thumbH = Math.max(10, h * view / total);
        int thumbY = y + (h - thumbH) * scroll / Math.max(1, total - view);
        gfx.fill(x + 1, thumbY, x + w - 1, thumbY + thumbH, C_SCROLL_THUMB);
    }

    private void fillBorder(GuiGraphics gfx, int l, int t, int r, int b, int th, int c) {
        gfx.fill(l, t, r, t + th, c);  gfx.fill(l, b - th, r, b, c);
        gfx.fill(l, t, l + th, b, c);  gfx.fill(r - th, t, r, b, c);
    }

    private void drawDiamond(GuiGraphics gfx, int cx, int cy, int size, int color) {
        for (int row = 0; row < size * 2; row++) {
            int py   = cy - size + row;
            int half = (row < size) ? row : (size * 2 - 1 - row);
            gfx.fill(cx - half, py, cx + half + 1, py + 1, color);
        }
    }

    // ─── Обработка ввода ─────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Клик по вкладке
        List<EncyclopediaTab> tabs = EncyclopediaData.TABS;
        int tabY   = wTop + HEADER_H + 1;
        int tabH   = TABS_H - 2;
        int tabW   = (W - MARGIN * 2) / tabs.size();
        int startX = wLeft + MARGIN;

        if (my >= tabY && my < tabY + tabH) {
            for (int i = 0; i < tabs.size(); i++) {
                int tx = startX + i * tabW;
                if (mx >= tx && mx < tx + tabW) {
                    switchTab(i);
                    return true;
                }
            }
        }

        // Клик по скроллбару списка — начинаем перетаскивание
        int sbListX = listX + listW2 - SCROLL_W;
        if (button == 0 && mx >= sbListX && mx < sbListX + SCROLL_W
                && my >= listY && my < listY + listH
                && visible.size() * ROW_H > listH) {
            draggingList    = true;
            dragStartY      = my;
            dragStartScroll = listScroll;
            return true;
        }

        // Клик по скроллбару контента — начинаем перетаскивание
        int sbContX = contX + contW - SCROLL_W;
        if (button == 0 && mx >= sbContX && mx < sbContX + SCROLL_W
                && my >= contY && my < contY + contH
                && totalContentH > contH) {
            draggingContent = true;
            dragStartY      = my;
            dragStartScroll = contentScroll;
            return true;
        }

        // Клик по записи в списке (не попал на скроллбар)
        if (mx >= listX && mx < listX + listW2 && my >= listY && my < listY + listH) {
            int row = (int)((my - listY + listScroll) / ROW_H);
            if (row >= 0 && row < visible.size()) {
                selectEntry(row);
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingList) {
            int total  = visible.size() * ROW_H;
            if (total > listH) {
                int trackH = listH - 2;
                int thumbH = Math.max(10, trackH * listH / total);
                // Сколько скролла приходится на один пиксель трека
                double ratio = (double)(total - listH) / Math.max(1, trackH - thumbH);
                int delta2   = (int)((my - dragStartY) * ratio);
                int max      = Math.max(0, total - listH);
                listScroll   = Math.max(0, Math.min(dragStartScroll + delta2, max));
            }
            return true;
        }
        if (draggingContent) {
            if (totalContentH > contH) {
                int trackH = contH - 2;
                int thumbH = Math.max(10, trackH * contH / totalContentH);
                double ratio  = (double)(totalContentH - contH) / Math.max(1, trackH - thumbH);
                int delta2    = (int)((my - dragStartY) * ratio);
                int max       = Math.max(0, totalContentH - contH);
                contentScroll = Math.max(0, Math.min(dragStartScroll + delta2, max));
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingList    = false;
        draggingContent = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int amount = (int)(-delta * ROW_H);

        if (mx >= listX && mx < listX + listW2 && my >= listY && my < listY + listH) {
            int max = Math.max(0, visible.size() * ROW_H - listH);
            listScroll = Math.max(0, Math.min(listScroll + amount, max));
            return true;
        }
        if (mx >= contX && mx < contX + contW && my >= contY && my < contY + contH) {
            int max = Math.max(0, totalContentH - contH);
            contentScroll = Math.max(0, Math.min(contentScroll + amount, max));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─── Логика состояния ────────────────────────────────────────────────────

    private void switchTab(int idx) {
        if (idx == activeTab) return;
        activeTab     = idx;
        selectedEntry = 0;
        listScroll    = 0;
        contentScroll = 0;
        contentDirty  = true;
        updateFilter();
    }

    private void selectEntry(int idx) {
        if (idx < 0 || idx >= visible.size()) return;
        selectedEntry = idx;
        contentScroll = 0;
        contentDirty  = true;

        // Автопрокрутка: если выбранная строка вне видимой области
        int rowY = idx * ROW_H;
        if (rowY < listScroll)
            listScroll = rowY;
        if (rowY + ROW_H > listScroll + listH)
            listScroll = rowY + ROW_H - listH;
    }

    private void updateFilter() {
        String q = (searchBox != null) ? searchBox.getValue().trim().toLowerCase() : "";
        List<EncyclopediaEntry> all = EncyclopediaData.TABS.get(activeTab).entries();
        visible = q.isEmpty() ? new ArrayList<>(all)
                : all.stream().filter(e -> matches(e, q)).collect(Collectors.toList());
        contentDirty = true;
        if (selectedEntry >= visible.size()) selectedEntry = 0;
    }

    private boolean matches(EncyclopediaEntry e, String q) {
        if (e.title().toLowerCase().contains(q)) return true;
        for (String tag : e.tags()) if (tag.contains(q)) return true;
        return false;
    }

    /**
     * Строит кэш строк контента для текущей выбранной статьи.
     * Вызывается автоматически через флаг contentDirty в render().
     */
    private void buildContentCache() {
        contentCache.clear();
        contentDirty = false;
        totalContentH = 0;

        if (visible.isEmpty()) return;

        EncyclopediaEntry entry = visible.get(Math.min(selectedEntry, visible.size() - 1));
        int maxW = contW - SCROLL_W - 18;

        for (EncyclopediaEntry.Line line : entry.body()) {
            switch (line.type()) {

                case SEPARATOR -> {
                    contentCache.add(RenderLine.separator());
                    totalContentH += SEP_H;
                }
                case H1 -> {
                    FormattedCharSequence fcs = Component
                            .literal("◈  " + line.text() + "  ◈")
                            .getVisualOrderText();
                    contentCache.add(new RenderLine(LineKind.H1, fcs));
                    totalContentH += LINE_H;
                }
                case H2 -> {
                    FormattedCharSequence fcs = Component
                            .literal(line.text())
                            .getVisualOrderText();
                    contentCache.add(new RenderLine(LineKind.H2, fcs));
                    totalContentH += LINE_H;
                }
                case BODY -> {
                    List<FormattedCharSequence> wrapped =
                            font.split(Component.literal(line.text()), maxW);
                    for (FormattedCharSequence w : wrapped) {
                        contentCache.add(new RenderLine(LineKind.BODY, w));
                        totalContentH += LINE_H;
                    }
                }
                case FLAVOR -> {
                    List<FormattedCharSequence> wrapped = font.split(
                            Component.literal(line.text())
                                     .withStyle(Style.EMPTY.withItalic(true)),
                            maxW - 8);
                    for (FormattedCharSequence w : wrapped) {
                        contentCache.add(new RenderLine(LineKind.FLAVOR, w));
                        totalContentH += LINE_H;
                    }
                }
                case TIP -> {
                    List<FormattedCharSequence> wrapped = font.split(
                            Component.literal("✦ " + line.text()), maxW - 10);
                    for (FormattedCharSequence w : wrapped) {
                        contentCache.add(new RenderLine(LineKind.TIP, w));
                        totalContentH += LINE_H;
                    }
                }
            }
        }
        // Нижний отступ: без него последняя строка обрезается из-за начального y+5
        totalContentH += MARGIN + 5;
    }

    /**
     * Вычисляет смещение бегущей строки для длинного заголовка.
     * Анимация: пауза → прокрутка до конца → пауза → сброс.
     *
     * @param textW  реальная ширина текста в пикселях
     * @param maxW   доступная ширина
     * @return смещение в пикселях (0 — без сдвига)
     */
    private int getMarqueeOffset(int textW, int maxW) {
        if (textW <= maxW) return 0;
        int overflow  = textW - maxW + 4;   // сколько пикселей нужно прокрутить
        int speedPxS  = 40;                  // скорость прокрутки: 40 px/сек
        int pauseMs   = 1200;               // пауза в начале и конце
        int scrollMs  = overflow * 1000 / speedPxS;
        int periodMs  = pauseMs + scrollMs + pauseMs;
        int phase     = (int)(System.currentTimeMillis() % periodMs);

        if (phase < pauseMs)               return 0;
        if (phase < pauseMs + scrollMs)    return (phase - pauseMs) * speedPxS / 1000;
        return overflow;
    }

    /** @return индекс записи под курсором, или -1 */
    private int getEntryAt(double mx, double my) {
        if (mx < listX || mx >= listX + listW2 || my < listY || my >= listY + listH) return -1;
        int row = (int)((my - listY + listScroll) / ROW_H);
        return (row >= 0 && row < visible.size()) ? row : -1;
    }
}
