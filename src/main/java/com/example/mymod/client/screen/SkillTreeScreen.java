package com.example.mymod.client.screen;

import com.example.mymod.network.LearnSkillPacket;
import com.example.mymod.network.ModNetwork;
import com.example.mymod.network.SpellSlotPacket;
import com.example.mymod.skill.IPlayerSkills;
import com.example.mymod.skill.PlayerSkillsCapability;
import com.example.mymod.skill.SkillNode;
import com.example.mymod.skill.SkillTree;
import com.example.mymod.spell.MagicSchool;
import com.example.mymod.spell.Spell;
import com.example.mymod.spell.SpellRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Экран дерева прокачки — аналог Path of Exile passive skill tree.
 *
 * Занимает почти весь экран. Левая часть — дерево нод с анимацией.
 * Правая часть (INFO_PANEL_WIDTH) — панель информации о выбранной ноде.
 *
 * Управление:
 *   ПКМ + drag — перемещение камеры
 *   Колесо мыши — зум
 *   ЛКМ по ноде — выбор ноды
 *   ЛКМ по кнопке «Изучить» — отправить LearnSkillPacket
 *   ESC — закрыть
 */
public class SkillTreeScreen extends Screen {

    // ─── Константы UI ─────────────────────────────────────────────────────────

    private static final int INFO_PANEL_WIDTH = 175;
    private static final int MARGIN           = 6;

    // Радиусы нод в пикселях (в мировом пространстве дерева)
    private static final int R_CENTRAL = 22;
    private static final int R_GATEWAY = 14;
    private static final int R_PASSIVE =  8;
    private static final int R_SPELL   = 14;

    // ─── Цвета ───────────────────────────────────────────────────────────────

    private static final int COL_BG          = 0xFF0A0818;
    private static final int COL_PANEL_BG    = 0xEE100C24;
    private static final int COL_PANEL_BORD  = 0xFF2A1A5A;
    private static final int COL_GOLD        = 0xFFC9A227;
    private static final int COL_GOLD_DIM    = 0xFF7A5E10;
    private static final int COL_NODE_DARK   = 0xFF080614;
    private static final int COL_LOCKED      = 0xFF333044;
    private static final int COL_AVAIL_RING  = 0xFFAAAAAA;
    private static final int COL_WHITE       = 0xFFFFFFFF;
    private static final int COL_GRAY        = 0xFF888888;
    private static final int COL_GREEN       = 0xFF55FF55;
    private static final int COL_RED         = 0xFFFF4444;

    // ─── Состояние камеры ─────────────────────────────────────────────────────

    /** Масштаб: 1 единица дерева = zoom пикселей */
    private float zoom = 0.58f;
    /** Смещение: экранные координаты центра дерева (узла 0,0) */
    private float camX, camY;

    private boolean dragging = false;
    private double dragStartX, dragStartY;
    private float dragCamStartX, dragCamStartY;

    // ─── Состояние экрана ─────────────────────────────────────────────────────

    private SkillNode selectedNode = null;
    /** Данные из capability, обновляются каждый кадр */
    private Set<String> unlockedNodes = Set.of();
    private int skillPoints = 0;

    /** Позиция кнопки «Изучить» — обновляется во время рендера */
    private int learnBtnX = -1, learnBtnY = -1, learnBtnW = 0, learnBtnH = 16;

    /** Y-координата первого ряда кнопок «В колесо» (-1 = не показываем) */
    private int wheelBtnRowY = -1;
    /** Spell ID выбранной ноды-заклинания (для обработки кликов по слотам) */
    private String selectedSpellId = null;
    /** Текущие слоты колеса (синхронизируются из capability) */
    private String[] spellSlots = new String[IPlayerSkills.SPELL_SLOTS];

    /** Счётчик анимации, растёт каждый кадр */
    private float tick = 0f;

    // ─── Конструктор ──────────────────────────────────────────────────────────

    public SkillTreeScreen() {
        super(Component.literal("Древо Познания"));
    }

    @Override
    protected void init() {
        super.init();
        // Центр дерева — середина области дерева
        camX = treeAreaWidth() / 2f;
        camY = height / 2f;
    }

    // ─── Вспомогательная геометрия ────────────────────────────────────────────

    /** Ширина области дерева (без правой панели) */
    private int treeAreaWidth() {
        return width - INFO_PANEL_WIDTH - MARGIN * 3;
    }

    /** Дерево → экран X */
    private int tx(float wx) { return Math.round(camX + wx * zoom); }
    /** Дерево → экран Y */
    private int ty(float wy) { return Math.round(camY + wy * zoom); }
    /** Экран → дерево X */
    private float wx(double sx) { return (float)((sx - camX) / zoom); }
    /** Экран → дерево Y */
    private float wy(double sy) { return (float)((sy - camY) / zoom); }

    private int nodeRadius(SkillNode n) {
        return switch (n.getType()) {
            case CENTRAL -> R_CENTRAL;
            case GATEWAY -> R_GATEWAY;
            case PASSIVE -> R_PASSIVE;
            case SPELL   -> R_SPELL;
        };
    }

    // ─── Главный рендер ───────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        tick += 0.04f;

        // Обновляем данные из capability каждый кадр
        refreshSkillData();

        // 1. Фон
        renderBackground(gfx, mouseX, mouseY, partialTick);

        // 2. Декоративная граница всего экрана
        renderScreenBorder(gfx);

        // 3. Дерево (линии + ноды)
        renderTree(gfx, mouseX, mouseY);

        // 4. Правая панель информации
        renderInfoPanel(gfx, mouseX, mouseY);

        // 5. Верхняя строка-заголовок
        renderHeader(gfx);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    // ─── 1. Фон ───────────────────────────────────────────────────────────────

    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Сплошной тёмный фон
        gfx.fill(0, 0, width, height, COL_BG);
        renderStars(gfx);
        renderSchoolAmbience(gfx);
    }

    /** Мерцающие звёзды на фоне */
    private void renderStars(GuiGraphics gfx) {
        Random rng = new Random(12345L);
        for (int i = 0; i < 150; i++) {
            int sx = rng.nextInt(width);
            int sy = rng.nextInt(height);
            float blink = (float)(Math.sin(tick * 1.8f + i * 0.53f) * 0.4f + 0.6f);
            int a = (int)(blink * 180);
            int b = (int)(blink * 230);
            int col = (a << 24) | (b << 16) | (b << 8) | b;
            if (i % 6 == 0) gfx.fill(sx, sy, sx + 2, sy + 2, col);
            else             gfx.fill(sx, sy, sx + 1, sy + 1, col);
        }
    }

    /**
     * Мягкое цветное свечение вокруг позиций школ — создаёт атмосферу.
     * Каждая школа имеет свою зону свечения на экране.
     */
    private void renderSchoolAmbience(GuiGraphics gfx) {
        float pulse = (float)(Math.sin(tick * 1.2f) * 0.15f + 0.85f);

        // Crimson — слева
        int cx = tx(-420f), cy = ty(0);
        renderGlowBlob(gfx, cx, cy, (int)(160 * pulse), 0xFF2200, 45);
        renderSchoolRunes(gfx, cx, cy, "⚔", 0xFFFF2200);

        // Holy — сверху
        int hx = tx(0), hy = ty(-420f);
        renderGlowBlob(gfx, hx, hy, (int)(160 * pulse), 0xFFD700, 45);
        renderSchoolRunes(gfx, hx, hy, "✦", 0xFFFFD700);

        // Cultist — справа
        int ux = tx(420f), uy = ty(0);
        renderGlowBlob(gfx, ux, uy, (int)(160 * pulse), 0x9900CC, 45);
        renderSchoolRunes(gfx, ux, uy, "☽", 0xFFA020F0);

        // Puppet — снизу
        int px = tx(0), py = ty(420f);
        renderGlowBlob(gfx, px, py, (int)(160 * pulse), 0x00CC88, 45);
        renderSchoolRunes(gfx, px, py, "✿", 0xFF00CC88);
    }

    /**
     * Нарисовать мягкое сферическое свечение (концентрические полупрозрачные прямоугольники).
     */
    private void renderGlowBlob(GuiGraphics gfx, int cx, int cy, int radius, int rgb, int maxAlpha) {
        for (int r = radius; r > 0; r -= 14) {
            int alpha = maxAlpha * (radius - r) / radius / 2;
            gfx.fill(cx - r, cy - r, cx + r, cy + r, (alpha << 24) | (rgb & 0xFFFFFF));
        }
    }

    /**
     * Нарисовать декоративный символ школы большого размера (3x scale) — как фоновый рисунок.
     */
    private void renderSchoolRunes(GuiGraphics gfx, int cx, int cy, String symbol, int color) {
        // Ограничиваем область дерева
        if (cx < -40 || cx > treeAreaWidth() + 40) return;
        if (cy < -40 || cy > height + 40) return;

        int alpha = 0x30; // очень прозрачно — фоновый элемент
        int col   = (alpha << 24) | (color & 0xFFFFFF);

        // Рисуем символ 3 раза со сдвигами для эффекта "большого" символа
        gfx.pose().pushPose();
        gfx.pose().translate(cx - 12, cy - 8, 0);
        gfx.pose().scale(3f, 3f, 1f);
        gfx.drawString(font, symbol, 0, 0, col, false);
        gfx.pose().popPose();
    }

    // ─── 2. Граница экрана ────────────────────────────────────────────────────

    private void renderScreenBorder(GuiGraphics gfx) {
        // Внешняя рамка — тонкая золотая линия
        gfx.fill(0, 0, width, 1, COL_GOLD_DIM);
        gfx.fill(0, height - 1, width, height, COL_GOLD_DIM);
        gfx.fill(0, 0, 1, height, COL_GOLD_DIM);
        gfx.fill(width - 1, 0, width, height, COL_GOLD_DIM);

        // Разделитель между деревом и панелью
        int divX = treeAreaWidth() + MARGIN;
        gfx.fill(divX, 0, divX + 1, height, COL_PANEL_BORD);
    }

    // ─── 3. Дерево нод ────────────────────────────────────────────────────────

    private void renderTree(GuiGraphics gfx, int mouseX, int mouseY) {
        // Линии соединения
        for (String[] conn : SkillTree.getConnections()) {
            SkillNode a = SkillTree.getById(conn[0]);
            SkillNode b = SkillTree.getById(conn[1]);
            if (a != null && b != null) renderConnection(gfx, a, b);
        }

        // Ноды поверх линий
        for (SkillNode node : SkillTree.getAllNodes()) {
            renderNode(gfx, node, mouseX, mouseY);
        }
    }

    /** Линия соединения двух нод */
    private void renderConnection(GuiGraphics gfx, SkillNode a, SkillNode b) {
        int x1 = tx(a.getX()), y1 = ty(a.getY());
        int x2 = tx(b.getX()), y2 = ty(b.getY());

        boolean aUnl = unlockedNodes.contains(a.getId());
        boolean bUnl = unlockedNodes.contains(b.getId());

        int lineColor;
        int lineW;
        if (aUnl && bUnl) {
            // Обе открыты — яркая линия цвета школы
            lineColor = b.getSchool().colorWithAlpha(0xFF);
            lineW = 3;
        } else if (aUnl) {
            // Источник открыт — тусклая
            lineColor = b.getSchool().colorWithAlpha(0x66);
            lineW = 2;
        } else {
            // Обе закрыты
            lineColor = 0x33555566;
            lineW = 1;
        }

        drawLine(gfx, x1, y1, x2, y2, lineColor, lineW);

        // Анимированная "искра" бежит по открытой линии
        if (aUnl && bUnl) {
            float t = (tick * 0.6f) % 1.0f;
            int sx = (int)(x1 + (x2 - x1) * t);
            int sy = (int)(y1 + (y2 - y1) * t);
            int sparkCol = b.getSchool().colorWithAlpha(0xCC);
            gfx.fill(sx - 2, sy - 2, sx + 2, sy + 2, sparkCol);
        }
    }

    /** Рендер одной ноды со всеми эффектами */
    private void renderNode(GuiGraphics gfx, SkillNode node, int mouseX, int mouseY) {
        int sx = tx(node.getX()), sy = ty(node.getY());
        int r  = nodeRadius(node);

        // Выход за экран — пропускаем
        if (sx + r < 0 || sx - r > treeAreaWidth()) return;
        if (sy + r < 0 || sy - r > height)           return;

        boolean unlocked   = unlockedNodes.contains(node.getId());
        boolean available  = isAvailable(node);
        boolean selected   = (node == selectedNode);
        boolean hovered    = isHovered(node, mouseX, mouseY);

        int schoolRgb = node.getSchool().color;

        // ── Внешнее свечение ─────────────────────────────────────────────────
        if (unlocked) {
            float glowPulse = (float)(Math.sin(tick * 3.0f + node.getX() * 0.005f) * 0.3f + 0.7f);
            renderGlow(gfx, sx, sy, r + 10, schoolRgb, (int)(glowPulse * 90));
        } else if (available) {
            float avPulse = (float)(Math.sin(tick * 4.5f) * 0.5f + 0.5f);
            renderGlow(gfx, sx, sy, r + 6, 0xCCCCCC, (int)(avPulse * 40));
        }

        // Дополнительный яркий ореол при выборе
        if (selected) {
            renderGlow(gfx, sx, sy, r + 8, schoolRgb, 80);
        }

        // ── Заполнение круга ─────────────────────────────────────────────────
        // Тёмная подложка
        drawCircle(gfx, sx, sy, r, COL_NODE_DARK);

        // Цветная заливка с прозрачностью
        int fillAlpha = unlocked ? 0x99 : (available ? 0x44 : 0x22);
        drawCircle(gfx, sx, sy, r - 1, (fillAlpha << 24) | (schoolRgb & 0xFFFFFF));

        // ── Ободок ───────────────────────────────────────────────────────────
        int borderCol;
        int borderW = selected ? 2 : 1;
        if (unlocked)        borderCol = 0xFF000000 | schoolRgb;
        else if (available)  borderCol = COL_AVAIL_RING;
        else                 borderCol = COL_LOCKED;
        drawCircleOutline(gfx, sx, sy, r, borderCol, borderW);

        // ── Символ внутри ────────────────────────────────────────────────────
        renderNodeSymbol(gfx, node, sx, sy, unlocked, available);

        // ── Название под нодой (только если достаточно большой зум) ──────────
        if (zoom > 0.75f) {
            String name = node.getName();
            int tw = font.width(name);
            int textCol = unlocked ? (0xFF000000 | schoolRgb) : (available ? 0xFFAAAAAA : 0xFF555555);
            gfx.drawString(font, name, sx - tw / 2, sy + r + 3, textCol, false);
        }
    }

    /** Символ (иконка) внутри ноды */
    private void renderNodeSymbol(GuiGraphics gfx, SkillNode node, int sx, int sy,
                                   boolean unlocked, boolean available) {
        String sym;
        int col;
        switch (node.getType()) {
            case CENTRAL -> { sym = "✦"; col = unlocked ? COL_GOLD        : 0xFF555555; }
            case GATEWAY -> { sym = "⚑"; col = unlocked ? (0xFF000000 | node.getSchool().color) : (available ? 0xFF888888 : 0xFF3A3A3A); }
            case PASSIVE -> { sym = "◈"; col = unlocked ? 0xFFDDDDDD     : (available ? 0xFF777777 : 0xFF333333); }
            case SPELL   -> { sym = "✵"; col = unlocked ? (0xFF000000 | node.getSchool().color) : (available ? 0xFF888888 : 0xFF3A3A3A); }
            default      -> { sym = "?"; col = COL_WHITE; }
        }
        int tx = sx - font.width(sym) / 2;
        int ty = sy - font.lineHeight / 2;
        if (unlocked) {
            // Лёгкое свечение текста — тень того же цвета
            gfx.drawString(font, sym, tx + 1, ty + 1, (col & 0xFFFFFF) | 0x55000000, false);
        }
        gfx.drawString(font, sym, tx, ty, col, false);
    }

    // ─── 4. Правая панель ─────────────────────────────────────────────────────

    private void renderInfoPanel(GuiGraphics gfx, int mouseX, int mouseY) {
        int px = treeAreaWidth() + MARGIN * 2;
        int py = MARGIN;
        int pw = INFO_PANEL_WIDTH;
        int ph = height - MARGIN * 2;

        // Фон
        gfx.fill(px, py, px + pw, py + ph, COL_PANEL_BG);

        // Внутренняя рамка
        gfx.fill(px,      py,      px + pw,      py + 1,  COL_PANEL_BORD);
        gfx.fill(px,      py + ph - 1, px + pw,  py + ph, COL_PANEL_BORD);
        gfx.fill(px,      py,      px + 1,      py + ph,  COL_PANEL_BORD);
        gfx.fill(px + pw - 1, py, px + pw, py + ph,       COL_PANEL_BORD);

        // Угловые украшения
        gfx.fill(px + 3, py + 3, px + 8, py + 4, COL_GOLD_DIM);
        gfx.fill(px + 3, py + 3, px + 4, py + 8, COL_GOLD_DIM);
        gfx.fill(px + pw - 8, py + 3, px + pw - 3, py + 4, COL_GOLD_DIM);
        gfx.fill(px + pw - 4, py + 3, px + pw - 3, py + 8, COL_GOLD_DIM);

        int y = py + 12;

        // Очки навыков
        gfx.drawCenteredString(font, "✦ ОЧКИ НАВЫКОВ ✦", px + pw / 2, y, COL_GOLD);
        y += 14;

        String pts = String.valueOf(skillPoints);
        gfx.drawCenteredString(font, pts, px + pw / 2, y, skillPoints > 0 ? COL_GREEN : COL_RED);
        y += 16;

        // Разделитель
        y = drawSeparator(gfx, px, y, pw, COL_PANEL_BORD);

        // Информация о выбранной ноде
        if (selectedNode != null) {
            y = renderNodeInfo(gfx, px, y, pw, selectedNode, mouseX, mouseY);
        } else {
            gfx.drawCenteredString(font, "§7Нажмите на ноду", px + pw / 2, y + 20, COL_GRAY);
            gfx.drawCenteredString(font, "§7для просмотра", px + pw / 2, y + 32, COL_GRAY);
            learnBtnX    = -1; // нет кнопки «Изучить»
            wheelBtnRowY = -1; // нет кнопок «В колесо»
            selectedSpellId = null;
        }
    }

    /**
     * Рендер детальной информации о выбранной ноде.
     * Возвращает Y-позицию после всего контента.
     */
    private int renderNodeInfo(GuiGraphics gfx, int px, int startY, int pw,
                                SkillNode node, int mouseX, int mouseY) {
        int y     = startY;
        boolean unlocked  = unlockedNodes.contains(node.getId());
        boolean available = isAvailable(node);

        int schoolCol = 0xFF000000 | node.getSchool().color;

        // Название ноды
        gfx.drawCenteredString(font, node.getName(), px + pw / 2, y, schoolCol);
        y += 12;

        // Школа
        String schoolTxt = node.getSchool() == MagicSchool.NONE
                ? "§8Нейтральная" : "§8" + node.getSchool().displayName;
        gfx.drawCenteredString(font, schoolTxt, px + pw / 2, y, COL_GRAY);
        y += 14;

        y = drawSeparator(gfx, px, y, pw, COL_PANEL_BORD);

        // Описание (с переносом)
        for (String line : node.getDescription().split("\n")) {
            List<FormattedCharSequence> wrapped = font.split(
                    Component.literal("§7" + line), pw - 14);
            for (FormattedCharSequence seq : wrapped) {
                gfx.drawString(font, seq, px + 7, y, 0xFFCCCCCC, false);
                y += 10;
            }
        }
        y += 4;

        // Бонус пассивной ноды
        if (node.getBonusDescription() != null) {
            y = drawSeparator(gfx, px, y, pw, COL_PANEL_BORD);
            gfx.drawCenteredString(font, "§a" + node.getBonusDescription(),
                    px + pw / 2, y, 0xFF55FF55);
            y += 14;
        }

        // Информация о заклинании + кнопки «В колесо»
        if (node.getSpellId() != null) {
            Spell spell = SpellRegistry.get(node.getSpellId());
            if (spell != null) {
                y = drawSeparator(gfx, px, y, pw, COL_PANEL_BORD);
                gfx.drawCenteredString(font, "§eЗаклинание:", px + pw / 2, y, 0xFFFFDD44);
                y += 11;
                gfx.drawCenteredString(font, spell.getDisplayName(), px + pw / 2, y, COL_WHITE);
                y += 11;
                gfx.drawCenteredString(font, "§9◈ Мана: §b" + spell.getManaCost(),
                        px + pw / 2, y, 0xFF88AAFF);
                y += 11;
                gfx.drawCenteredString(font, "§8⏱ Откат: §7" + spell.getCooldownSeconds() + " сек",
                        px + pw / 2, y, COL_GRAY);
                y += 14;

                // Кнопки «В колесо» — только для открытых нод-заклинаний
                if (unlocked) {
                    y = renderWheelSlotButtons(gfx, px, y, pw, node.getSpellId(), mouseX, mouseY);
                }
            }
        }

        y += 6;
        y = drawSeparator(gfx, px, y, pw, COL_PANEL_BORD);
        y += 4;

        // Статус / кнопка действия
        if (unlocked) {
            gfx.drawCenteredString(font, "§a✦ ОТКРЫТО ✦", px + pw / 2, y, COL_GREEN);
            learnBtnX = -1;
        } else if (available && skillPoints >= node.getCost()) {
            // Кнопка "Изучить" — активна
            int bx = px + 8, bw = pw - 16, bh = 16;
            int by = y;
            boolean hover = mouseX >= bx && mouseX <= bx + bw
                         && mouseY >= by && mouseY <= by + bh;

            gfx.fill(bx, by, bx + bw, by + bh,
                    hover ? 0xFF2A5A1A : 0xFF183010);
            gfx.fill(bx, by, bx + bw, by + 1, 0xFF55AA33);
            gfx.fill(bx, by + bh - 1, bx + bw, by + bh, 0xFF55AA33);
            gfx.drawCenteredString(font,
                    "◆ ИЗУЧИТЬ  (" + node.getCost() + " очко)",
                    bx + bw / 2, by + 4,
                    hover ? COL_WHITE : COL_GREEN);

            // Сохраняем позицию кнопки для клика
            learnBtnX = bx; learnBtnY = by; learnBtnW = bw; learnBtnH = bh;
            y += bh + 4;
        } else if (available) {
            gfx.drawCenteredString(font, "§cНе хватает очков",
                    px + pw / 2, y, COL_RED);
            learnBtnX = -1;
            y += 14;
        } else {
            // Нода заблокирована — показываем требования
            gfx.drawCenteredString(font, "§8🔒 Заблокировано", px + pw / 2, y, COL_LOCKED);
            y += 12;
            for (String req : node.getPrerequisites()) {
                SkillNode reqNode = SkillTree.getById(req);
                String rn = (reqNode != null) ? "• " + reqNode.getName() : "• " + req;
                gfx.drawCenteredString(font, "§8" + rn, px + pw / 2, y, COL_LOCKED);
                y += 10;
            }
            learnBtnX = -1;
        }

        return y;
    }

    // ─── 5. Заголовок ─────────────────────────────────────────────────────────

    private void renderHeader(GuiGraphics gfx) {
        // Полупрозрачная полоска заголовка
        gfx.fill(0, 0, treeAreaWidth() + MARGIN, 20, 0xCC080612);
        gfx.fill(0, 19, treeAreaWidth() + MARGIN, 20, COL_GOLD_DIM);

        String title = "✦  ДРЕВО  ПОЗНАНИЯ  ✦";
        gfx.drawCenteredString(font, title, (treeAreaWidth() + MARGIN) / 2, 6, COL_GOLD);
    }

    // ─── Вспомогательные методы рисования ────────────────────────────────────

    private int drawSeparator(GuiGraphics gfx, int px, int y, int pw, int col) {
        gfx.fill(px + 5, y, px + pw - 5, y + 1, col);
        return y + 7;
    }

    /** Нарисовать заполненный круг через горизонтальные отрезки */
    private void drawCircle(GuiGraphics gfx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(Math.max(0.0, (double) r * r - (double) dy * dy));
            gfx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** Нарисовать контур круга */
    private void drawCircleOutline(GuiGraphics gfx, int cx, int cy, int r, int color, int thickness) {
        for (int t = 0; t < thickness; t++) {
            int ri = r - t;
            if (ri <= 0) break;
            for (int dy = -ri; dy <= ri; dy++) {
                int dxO = (int) Math.sqrt(Math.max(0.0, (double) ri * ri - (double) dy * dy));
                int dxI = (ri > 1)
                        ? (int) Math.sqrt(Math.max(0.0, (double)(ri - 1) * (ri - 1) - (double) dy * dy))
                        : 0;
                if (dxO > dxI) {
                    gfx.fill(cx - dxO, cy + dy, cx - dxI, cy + dy + 1, color);
                    gfx.fill(cx + dxI, cy + dy, cx + dxO + 1, cy + dy + 1, color);
                }
            }
        }
    }

    /** Мягкий круглый ореол (концентрические круги убывающей прозрачности) */
    private void renderGlow(GuiGraphics gfx, int cx, int cy, int radius, int rgb, int maxAlpha) {
        int base = rgb & 0xFFFFFF;
        for (int r = radius; r > 0; r -= 3) {
            int alpha = maxAlpha * (radius - r) / radius;
            drawCircle(gfx, cx, cy, r, (alpha << 24) | base);
        }
    }

    /** Нарисовать линию из пикселей (Bresenham-style через fill) */
    private void drawLine(GuiGraphics gfx, int x1, int y1, int x2, int y2, int color, int w) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) return;
        int half = w / 2;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            gfx.fill(x - half, y - half, x + half + 1, y + half + 1, color);
        }
    }

    // ─── Состояние нод ────────────────────────────────────────────────────────

    private boolean isAvailable(SkillNode node) {
        if (unlockedNodes.contains(node.getId())) return false;
        for (String req : node.getPrerequisites()) {
            if (!unlockedNodes.contains(req)) return false;
        }
        return true;
    }

    private boolean isHovered(SkillNode node, int mouseX, int mouseY) {
        if (mouseX > treeAreaWidth() + MARGIN) return false;
        int sx = tx(node.getX()), sy = ty(node.getY());
        int r  = nodeRadius(node) + 4;
        return Math.abs(mouseX - sx) <= r && Math.abs(mouseY - sy) <= r;
    }

    // ─── Обновление данных из capability ─────────────────────────────────────

    private void refreshSkillData() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(skills -> {
            this.unlockedNodes = skills.getUnlockedNodes();
            this.skillPoints   = skills.getSkillPoints();
            for (int i = 0; i < IPlayerSkills.SPELL_SLOTS; i++) {
                this.spellSlots[i] = skills.getSpellInSlot(i);
            }
        });
    }

    // ─── Ввод ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {

            // Клик по кнопкам «В колесо» (назначение заклинания в слот)
            if (wheelBtnRowY >= 0 && selectedSpellId != null) {
                final int BTN_W = 17, BTN_H = 13, GAP = 2;
                int px2  = treeAreaWidth() + MARGIN * 2;
                int pw2  = INFO_PANEL_WIDTH;
                int rowW = 5 * BTN_W + 4 * GAP;
                int sx   = px2 + (pw2 - rowW) / 2;

                for (int i = 0; i < IPlayerSkills.SPELL_SLOTS; i++) {
                    int col = i % 5;
                    int row = i / 5;
                    int bx  = sx  + col * (BTN_W + GAP);
                    int by  = wheelBtnRowY + row * (BTN_H + GAP);

                    if (mouseX >= bx && mouseX <= bx + BTN_W
                            && mouseY >= by && mouseY <= by + BTN_H) {
                        // Если это заклинание уже назначено в данный слот — убираем его (toggle).
                        // Иначе назначаем.
                        String currentInSlot = (i < spellSlots.length) ? spellSlots[i] : null;
                        String newSpellId = selectedSpellId.equals(currentInSlot)
                                ? null : selectedSpellId;
                        ModNetwork.CHANNEL.sendToServer(
                                new SpellSlotPacket(i, newSpellId));
                        return true;
                    }
                }
            }

            // Клик по кнопке «Изучить»
            if (learnBtnX >= 0 && selectedNode != null
                    && mouseX >= learnBtnX && mouseX <= learnBtnX + learnBtnW
                    && mouseY >= learnBtnY && mouseY <= learnBtnY + learnBtnH) {
                learnNode(selectedNode);
                return true;
            }

            // Клик по ноде (только в области дерева)
            if (mouseX <= treeAreaWidth() + MARGIN) {
                for (SkillNode node : SkillTree.getAllNodes()) {
                    if (isHovered(node, (int) mouseX, (int) mouseY)) {
                        selectedNode = node;
                        return true;
                    }
                }
                selectedNode = null; // клик в пустоту
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && mouseX <= treeAreaWidth() + MARGIN) {
            dragging      = true;
            dragStartX    = mouseX;
            dragStartY    = mouseY;
            dragCamStartX = camX;
            dragCamStartY = camY;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            camX = dragCamStartX + (float)(mouseX - dragStartX);
            camY = dragCamStartY + (float)(mouseY - dragStartY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX <= treeAreaWidth() + MARGIN) {
            float oldZoom = zoom;
            zoom = Math.max(0.28f, Math.min(2.2f, zoom + (float) delta * 0.08f));
            // Зум относительно позиции курсора
            camX = (float)(mouseX - (mouseX - camX) * (zoom / oldZoom));
            camY = (float)(mouseY - (mouseY - camY) * (zoom / oldZoom));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ─── Кнопки назначения в слоты колеса ────────────────────────────────────

    /**
     * Рисует 10 маленьких кнопок «В колесо» (2 ряда по 5).
     * Кнопки подсвечиваются если данное заклинание уже назначено в слот.
     * Возвращает обновлённый Y.
     */
    private int renderWheelSlotButtons(GuiGraphics gfx, int px, int y, int pw,
                                        String spellId, int mouseX, int mouseY) {
        y = drawSeparator(gfx, px, y, pw, COL_PANEL_BORD);
        gfx.drawCenteredString(font, "§8В КОЛЕСО §7(повтор — убрать)§8:", px + pw / 2, y, COL_GRAY);
        y += 11;

        final int BTN_W = 17, BTN_H = 13, GAP = 2;
        // 5 кнопок в ряд, 2 ряда
        int rowW = 5 * BTN_W + 4 * GAP;
        int startX = px + (pw - rowW) / 2;

        // Запоминаем Y для обработки кликов
        wheelBtnRowY    = y;
        selectedSpellId = spellId;

        for (int i = 0; i < IPlayerSkills.SPELL_SLOTS; i++) {
            int col = i % 5;
            int row = i / 5;
            int bx  = startX + col * (BTN_W + GAP);
            int by  = y + row * (BTN_H + GAP);

            boolean isAssigned    = spellId.equals(spellSlots[i]);
            boolean hovered       = mouseX >= bx && mouseX <= bx + BTN_W
                                 && mouseY >= by && mouseY <= by + BTN_H;
            // При наведении на уже назначенный слот — показываем красный (подсказка: клик уберёт)
            boolean hoverAssigned = isAssigned && hovered;

            int bgCol  = isAssigned
                    ? (hoverAssigned ? 0xFF301616 : 0xFF163016)
                    : (hovered       ? 0xFF181828 : 0xFF0D0D1C);
            int rimCol = isAssigned
                    ? (hoverAssigned ? 0xFFAA3333 : 0xFF55AA33)
                    : (hovered       ? 0xFF6666AA : 0xFF2A2A44);
            int txtCol = isAssigned
                    ? (hoverAssigned ? COL_RED  : COL_GREEN)
                    : (hovered       ? COL_WHITE : COL_GRAY);

            // Фон кнопки
            gfx.fill(bx, by, bx + BTN_W, by + BTN_H, bgCol);
            // Рамка
            gfx.fill(bx, by, bx + BTN_W, by + 1, rimCol);
            gfx.fill(bx, by + BTN_H - 1, bx + BTN_W, by + BTN_H, rimCol);
            gfx.fill(bx, by, bx + 1, by + BTN_H, rimCol);
            gfx.fill(bx + BTN_W - 1, by, bx + BTN_W, by + BTN_H, rimCol);
            // Номер
            gfx.drawCenteredString(font, String.valueOf(i + 1),
                    bx + BTN_W / 2, by + 2, txtCol);
        }
        y += 2 * (BTN_H + GAP) + 4;
        return y;
    }

    /** Отправить запрос изучения ноды на сервер */
    private void learnNode(SkillNode node) {
        ModNetwork.CHANNEL.sendToServer(new LearnSkillPacket(node.getId()));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
