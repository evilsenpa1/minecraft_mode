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
 * Управление:
 *   ПКМ + drag — перемещение камеры
 *   Колесо мыши — зум
 *   ЛКМ по ноде — выбор ноды
 *   ЛКМ по кнопке «Изучить» — отправить LearnSkillPacket
 *   ESC — закрыть
 */
public class SkillTreeScreen extends Screen {

    // ─── Константы UI ─────────────────────────────────────────────────────────

    private static final int INFO_PANEL_WIDTH = 180;
    private static final int MARGIN           = 6;

    // Радиусы нод (мировые единицы)
    private static final int R_CENTRAL = 24;
    private static final int R_GATEWAY = 16;
    private static final int R_PASSIVE =  9;
    private static final int R_SPELL   = 16;

    // ─── Цветовая палитра ─────────────────────────────────────────────────────

    private static final int COL_BG          = 0xFF060410;  // глубокий космос
    private static final int COL_PANEL_BG    = 0xEE080618;
    private static final int COL_PANEL_BORD  = 0xFF1E1040;
    private static final int COL_GOLD        = 0xFFC9A227;
    private static final int COL_GOLD_BRIGHT = 0xFFFFE060;
    private static final int COL_GOLD_DIM    = 0xFF6A5210;
    private static final int COL_NODE_DARK   = 0xFF060410;
    private static final int COL_LOCKED      = 0xFF2A2840;
    private static final int COL_WHITE       = 0xFFFFFFFF;
    private static final int COL_GRAY        = 0xFF888888;
    private static final int COL_GREEN       = 0xFF55FF55;
    private static final int COL_RED         = 0xFFFF4444;

    // ─── Состояние камеры ─────────────────────────────────────────────────────

    private float zoom = 0.58f;
    private float camX, camY;

    private boolean dragging = false;
    private double dragStartX, dragStartY;
    private float dragCamStartX, dragCamStartY;

    // ─── Состояние экрана ─────────────────────────────────────────────────────

    private SkillNode selectedNode = null;
    private Set<String> unlockedNodes = Set.of();
    private int skillPoints = 0;

    private int learnBtnX = -1, learnBtnY = -1, learnBtnW = 0, learnBtnH = 16;
    private int wheelBtnRowY = -1;
    private String selectedSpellId = null;
    private String[] spellSlots = new String[IPlayerSkills.SPELL_SLOTS];

    /** Счётчик анимации */
    private float tick = 0f;

    // ─── Конструктор ──────────────────────────────────────────────────────────

    public SkillTreeScreen() {
        super(Component.literal("Древо Познания"));
    }

    @Override
    protected void init() {
        super.init();
        camX = treeAreaWidth() / 2f;
        camY = height / 2f;
    }

    // ─── Вспомогательная геометрия ────────────────────────────────────────────

    private int treeAreaWidth() {
        return width - INFO_PANEL_WIDTH - MARGIN * 3;
    }

    private int tx(float wx) { return Math.round(camX + wx * zoom); }
    private int ty(float wy) { return Math.round(camY + wy * zoom); }
    private float wx(double sx) { return (float)((sx - camX) / zoom); }
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
        refreshSkillData();

        renderBackground(gfx, mouseX, mouseY, partialTick);
        renderScreenBorder(gfx);
        renderTree(gfx, mouseX, mouseY);
        renderInfoPanel(gfx, mouseX, mouseY);
        renderHeader(gfx);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    // ─── 1. Фон ───────────────────────────────────────────────────────────────

    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(0, 0, width, height, COL_BG);
        renderStars(gfx);
        renderCenterAura(gfx);
        renderSchoolAmbience(gfx);
    }

    /** Звёздное небо с тремя уровнями яркости и мерцанием */
    private void renderStars(GuiGraphics gfx) {
        Random rng = new Random(98765L);
        int treeW = treeAreaWidth();

        for (int i = 0; i < 280; i++) {
            int sx = rng.nextInt(treeW + INFO_PANEL_WIDTH + MARGIN * 3);
            int sy = rng.nextInt(height);

            // Три типа звёзд: крошечные, малые, крупные
            int category = i % 9;
            float blink = (float)(Math.sin(tick * 1.6f + i * 0.47f) * 0.4f + 0.6f);

            if (category < 5) {
                // Крошечные — 1px, тусклые
                int a = (int)(blink * 100);
                int b = (int)(blink * 150);
                gfx.fill(sx, sy, sx + 1, sy + 1, (a << 24) | (b << 16) | (b << 8) | b);
            } else if (category < 8) {
                // Малые — 1px, яркие
                int a = (int)(blink * 180);
                int b = (int)(blink * 220);
                gfx.fill(sx, sy, sx + 1, sy + 1, (a << 24) | (b << 16) | (b << 8) | b);
            } else {
                // Крупные — 2px, очень яркие с лёгкой синевой
                int a = (int)(blink * 220);
                int r = (int)(blink * 200);
                int g = (int)(blink * 210);
                int b = (int)(blink * 255);
                gfx.fill(sx, sy, sx + 2, sy + 2, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    /** Слабое золотое сияние из центра дерева — как источник маны */
    private void renderCenterAura(GuiGraphics gfx) {
        int cx = tx(0), cy = ty(0);
        float pulse = (float)(Math.sin(tick * 1.5f) * 0.15f + 0.85f);
        // Очень мягкое радиальное золотое свечение
        renderGlowBlob(gfx, cx, cy, (int)(180 * pulse), 0x8B5A00, 35);
        renderGlowBlob(gfx, cx, cy, (int)(80 * pulse), 0xC9A227, 20);
    }

    /**
     * Цветное свечение вокруг зон школ.
     * Двухслойное: внешний широкий туман + внутреннее насыщенное ядро.
     */
    private void renderSchoolAmbience(GuiGraphics gfx) {
        float pulse = (float)(Math.sin(tick * 0.9f) * 0.12f + 0.88f);

        // Crimson — слева
        int cx = tx(-420f), cy = ty(0);
        renderGlowBlob(gfx, cx, cy, (int)(220 * pulse), 0xFF2200, 50);
        renderGlowBlob(gfx, cx, cy, (int)(100 * pulse), 0xFF5500, 35);
        renderSchoolSymbol(gfx, cx, cy, "⚔", 0xFF2200, 0x28);

        // Holy — сверху
        int hx = tx(0), hy = ty(-420f);
        renderGlowBlob(gfx, hx, hy, (int)(220 * pulse), 0xBB8800, 50);
        renderGlowBlob(gfx, hx, hy, (int)(100 * pulse), 0xFFD700, 35);
        renderSchoolSymbol(gfx, hx, hy, "✦", 0xFFD700, 0x28);

        // Cultist — справа
        int ux = tx(420f), uy = ty(0);
        renderGlowBlob(gfx, ux, uy, (int)(220 * pulse), 0x7010CC, 50);
        renderGlowBlob(gfx, ux, uy, (int)(100 * pulse), 0xA020F0, 35);
        renderSchoolSymbol(gfx, ux, uy, "☽", 0xA020F0, 0x28);

        // Puppet — снизу
        int px = tx(0), py = ty(420f);
        renderGlowBlob(gfx, px, py, (int)(220 * pulse), 0x009966, 50);
        renderGlowBlob(gfx, px, py, (int)(100 * pulse), 0x00CC88, 35);
        renderSchoolSymbol(gfx, px, py, "✿", 0x00CC88, 0x28);
    }

    /** Прямоугольное градиентное свечение (быстрое, для фона) */
    private void renderGlowBlob(GuiGraphics gfx, int cx, int cy, int radius, int rgb, int maxAlpha) {
        int steps = 10;
        int stepSize = Math.max(1, radius / steps);
        for (int r = radius; r > 0; r -= stepSize) {
            float t = 1.0f - (float) r / radius;
            int alpha = (int)(maxAlpha * t * t * 0.6f);
            gfx.fill(cx - r, cy - r, cx + r, cy + r, (alpha << 24) | (rgb & 0xFFFFFF));
        }
    }

    /** Большой декоративный символ школы (фоновый слой) */
    private void renderSchoolSymbol(GuiGraphics gfx, int cx, int cy, String symbol, int rgb, int alpha) {
        if (cx < -60 || cx > treeAreaWidth() + 60) return;
        if (cy < -60 || cy > height + 60) return;
        int col = (alpha << 24) | (rgb & 0xFFFFFF);
        gfx.pose().pushPose();
        gfx.pose().translate(cx - 14, cy - 9, 0);
        gfx.pose().scale(3.5f, 3.5f, 1f);
        gfx.drawString(font, symbol, 0, 0, col, false);
        gfx.pose().popPose();
    }

    // ─── 2. Граница экрана ────────────────────────────────────────────────────

    private void renderScreenBorder(GuiGraphics gfx) {
        // Внешняя рамка — двойная
        gfx.fill(0, 0, width, 2, COL_GOLD_DIM);
        gfx.fill(0, height - 2, width, height, COL_GOLD_DIM);
        gfx.fill(0, 0, 2, height, COL_GOLD_DIM);
        gfx.fill(width - 2, 0, width, height, COL_GOLD_DIM);

        // Внутренняя яркая рамка
        gfx.fill(3, 3, width - 3, 4, COL_GOLD_DIM);
        gfx.fill(3, height - 4, width - 3, height - 3, COL_GOLD_DIM);
        gfx.fill(3, 3, 4, height - 3, COL_GOLD_DIM);
        gfx.fill(width - 4, 3, width - 3, height - 3, COL_GOLD_DIM);

        // Угловые L-образные украшения
        drawCornerOrnament(gfx, 6, 6, 12, COL_GOLD_DIM);
        drawCornerOrnament(gfx, width - 6, 6, 12, COL_GOLD_DIM);
        drawCornerOrnament(gfx, 6, height - 6, 12, COL_GOLD_DIM);
        drawCornerOrnament(gfx, width - 6, height - 6, 12, COL_GOLD_DIM);

        // Разделитель между деревом и панелью
        int divX = treeAreaWidth() + MARGIN;
        gfx.fill(divX, 0, divX + 1, height, 0xFF1A0E3A);
        gfx.fill(divX + 1, 20, divX + 2, height - 20, 0xFF2A1A50);

        // Декоративные точки вдоль разделителя
        for (int y = 40; y < height - 40; y += 30) {
            float dp = (float)(Math.sin(tick * 2f + y * 0.05f) * 0.4f + 0.6f);
            int da = (int)(80 * dp);
            gfx.fill(divX, y - 1, divX + 2, y + 1, (da << 24) | 0xC9A227);
        }
    }

    /** Рисует угловое L-образное золотое украшение */
    private void drawCornerOrnament(GuiGraphics gfx, int x, int y, int size, int col) {
        boolean left  = (x < width / 2);
        boolean top   = (y < height / 2);
        int sx = left ? x : x - size;
        int sy = top  ? y : y - size;

        // Горизонтальная линия
        gfx.fill(sx, sy, sx + size, sy + 2, col);
        // Вертикальная линия
        gfx.fill(sx, sy, sx + 2, sy + size, col);
        // Конечная точка (противоположный угол)
        if (!left) { gfx.fill(sx + size - 2, sy, sx + size, sy + 2, col); }
        if (!top)  { gfx.fill(sx, sy + size - 2, sx + 2, sy + size, col); }
    }

    // ─── 3. Дерево нод ────────────────────────────────────────────────────────

    private void renderTree(GuiGraphics gfx, int mouseX, int mouseY) {
        // Сначала соединительные линии
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

    /**
     * Многослойная соединительная линия:
     * — открытая: 4 слоя (внешнее свечение, среднее, ядро, блик) + 3 текущих частицы
     * — полуоткрытая: 2 слоя тусклой линии
     * — закрытая: 1 тонкая тёмная линия
     */
    private void renderConnection(GuiGraphics gfx, SkillNode a, SkillNode b) {
        int x1 = tx(a.getX()), y1 = ty(a.getY());
        int x2 = tx(b.getX()), y2 = ty(b.getY());

        boolean aUnl = unlockedNodes.contains(a.getId());
        boolean bUnl = unlockedNodes.contains(b.getId());
        int schoolRgb = b.getSchool() != MagicSchool.NONE ? b.getSchool().color : a.getSchool().color;

        if (aUnl && bUnl) {
            // Слой 1: широкое мягкое свечение
            drawLine(gfx, x1, y1, x2, y2, 0x18000000 | (schoolRgb & 0xFFFFFF), 9);
            // Слой 2: среднее свечение
            drawLine(gfx, x1, y1, x2, y2, 0x38000000 | (schoolRgb & 0xFFFFFF), 5);
            // Слой 3: основная цветная линия
            drawLine(gfx, x1, y1, x2, y2, 0xCC000000 | (schoolRgb & 0xFFFFFF), 3);
            // Слой 4: яркий белый блик по центру
            drawLine(gfx, x1, y1, x2, y2, 0x88FFFFFF, 1);

            // Три текущих частицы, движущихся по линии со смещением фаз
            for (int p = 0; p < 3; p++) {
                float t = ((tick * 0.55f + p * 0.333f) % 1.0f);
                int spx = (int)(x1 + (x2 - x1) * t);
                int spy = (int)(y1 + (y2 - y1) * t);
                // Ореол частицы
                gfx.fill(spx - 3, spy - 3, spx + 3, spy + 3, 0x30000000 | (schoolRgb & 0xFFFFFF));
                // Тело частицы
                gfx.fill(spx - 1, spy - 1, spx + 2, spy + 2, 0xDD000000 | (schoolRgb & 0xFFFFFF));
                // Белый блик
                gfx.fill(spx, spy, spx + 1, spy + 1, 0xCCFFFFFF);
            }

        } else if (aUnl) {
            // Источник открыт — тусклая линия с намёком на цвет
            drawLine(gfx, x1, y1, x2, y2, 0x22000000 | (schoolRgb & 0xFFFFFF), 5);
            drawLine(gfx, x1, y1, x2, y2, 0x66000000 | (schoolRgb & 0xFFFFFF), 2);
        } else {
            // Обе закрыты — едва видная линия
            drawLine(gfx, x1, y1, x2, y2, 0x22334455, 2);
        }
    }

    /** Диспетчер рендера ноды по типу */
    private void renderNode(GuiGraphics gfx, SkillNode node, int mouseX, int mouseY) {
        int sx = tx(node.getX()), sy = ty(node.getY());
        int r  = nodeRadius(node);

        // Отсечение по экрану (с запасом для свечения)
        if (sx + r + 50 < 0 || sx - r - 50 > treeAreaWidth()) return;
        if (sy + r + 50 < 0 || sy - r - 50 > height)          return;

        boolean unlocked  = unlockedNodes.contains(node.getId());
        boolean available = isAvailable(node);
        boolean selected  = (node == selectedNode);
        boolean hovered   = isHovered(node, mouseX, mouseY);

        switch (node.getType()) {
            case CENTRAL -> renderCentralNode(gfx, sx, sy, r, unlocked, selected, hovered);
            case GATEWAY -> renderGatewayNode(gfx, sx, sy, r, node, unlocked, available, selected, hovered);
            case SPELL   -> renderSpellNode  (gfx, sx, sy, r, node, unlocked, available, selected, hovered);
            case PASSIVE -> renderPassiveNode(gfx, sx, sy, r, node, unlocked, available, selected, hovered);
        }

        // Название под нодой при достаточном зуме
        if (zoom > 0.72f) {
            String name = node.getName();
            int tw  = font.width(name);
            int textY = sy + r + 3;
            int textX = sx - tw / 2;
            int schoolRgb = node.getSchool().color;
            int textCol = unlocked
                    ? (0xFF000000 | schoolRgb)
                    : (available ? 0xFFAAAAAA : 0xFF444455);

            // Тень подписи
            gfx.drawString(font, name, textX + 1, textY + 1, 0x80000000, false);
            gfx.drawString(font, name, textX, textY, textCol, false);
        }
    }

    // ─── Рендер центральной ноды (золотое солнце) ─────────────────────────────

    private void renderCentralNode(GuiGraphics gfx, int sx, int sy, int r,
                                    boolean unlocked, boolean selected, boolean hovered) {
        float pulse = (float)(Math.sin(tick * 2.0f) * 0.25f + 0.75f);

        // Вращающиеся 8 лучей
        if (unlocked) {
            for (int i = 0; i < 8; i++) {
                double angle = tick * 0.35 + i * Math.PI / 4;
                int bx = sx + (int)(Math.cos(angle) * (r + 20));
                int by = sy + (int)(Math.sin(angle) * (r + 20));
                int beamA = (int)(50 * pulse);
                drawLine(gfx, sx, sy, bx, by, (beamA << 24) | 0xC9A227, 1);
            }
            // Контр-вращающиеся 4 луча
            for (int i = 0; i < 4; i++) {
                double angle = -tick * 0.2 + i * Math.PI / 2 + Math.PI / 8;
                int bx = sx + (int)(Math.cos(angle) * (r + 14));
                int by = sy + (int)(Math.sin(angle) * (r + 14));
                int beamA = (int)(30 * pulse);
                drawLine(gfx, sx, sy, bx, by, (beamA << 24) | 0xFFE070, 1);
            }
        }

        // Внешнее свечение
        renderGlow(gfx, sx, sy, r + 28, 0xC9A227, unlocked ? (int)(100 * pulse) : 22);
        if (unlocked) {
            // Дополнительный белый ореол
            renderGlow(gfx, sx, sy, r + 16, 0xFFFF88, (int)(40 * pulse));
        }

        // Внешнее пунктирное кольцо
        drawDashedCircle(gfx, sx, sy, r + 11, 0xC9A227, unlocked ? 0xBB : 0x22, 24);

        // Вращающийся многоугольник (8 сторон)
        float polyAngle = (float)(tick * (unlocked ? 0.25 : 0));
        drawPolygon(gfx, sx, sy, r + 7, 8, polyAngle,
                unlocked ? 0x66C9A227 : 0x11332211, 1);

        // Тёмная подложка + золотая заливка
        drawCircle(gfx, sx, sy, r + 1, 0xFF040310);
        int fillA = unlocked ? 0x99 : 0x33;
        drawCircle(gfx, sx, sy, r, (fillA << 24) | 0x7B4A00);

        // Внутренний яркий диск при открытии
        if (unlocked) {
            int innerA = (int)(70 * pulse);
            drawCircle(gfx, sx, sy, r / 3, (innerA << 24) | 0xFFD060);
        }

        // Двойное кольцо-бордюр
        drawCircleOutline(gfx, sx, sy, r + 1, unlocked ? 0xFFC9A227 : 0xFF1A1230, 2);
        drawCircleOutline(gfx, sx, sy, r - 3, unlocked ? 0x88C9A227 : 0x22100C20, 1);

        // Центральный символ ✦
        String sym = "✦";
        int symW = font.width(sym);
        int symX = sx - symW / 2;
        int symY = sy - font.lineHeight / 2;
        int symCol = unlocked ? 0xFFFFF080 : 0xFF443322;
        if (unlocked) {
            // Четыре тени для эффекта свечения
            int shadowCol = 0x66C9A227;
            gfx.drawString(font, sym, symX + 1, symY + 1, shadowCol, false);
            gfx.drawString(font, sym, symX - 1, symY - 1, shadowCol, false);
            gfx.drawString(font, sym, symX + 1, symY - 1, shadowCol, false);
            gfx.drawString(font, sym, symX - 1, symY + 1, shadowCol, false);
        }
        gfx.drawString(font, sym, symX, symY, symCol, false);

        // Пульсирующее кольцо при выборе/наведении
        if (selected || hovered) {
            float sp = (float)(Math.sin(tick * 9f) * 0.4f + 0.6f);
            drawCircleOutline(gfx, sx, sy, r + 5, (int)(200 * sp) << 24 | 0xFFE040, 1);
            drawCircleOutline(gfx, sx, sy, r + 8, (int)(80 * sp) << 24 | 0xC9A227, 1);
        }
    }

    // ─── Рендер ворот школы (шестигранный кристалл) ──────────────────────────

    private void renderGatewayNode(GuiGraphics gfx, int sx, int sy, int r,
                                    SkillNode node, boolean unlocked, boolean available,
                                    boolean selected, boolean hovered) {
        int schoolRgb = node.getSchool().color;
        float pulse = (float)(Math.sin(tick * 2.5f + node.getX() * 0.003f) * 0.3f + 0.7f);

        // Внешнее свечение
        int glowA = unlocked ? (int)(90 * pulse) : (available ? 28 : 10);
        renderGlow(gfx, sx, sy, r + 16, schoolRgb, glowA);

        // Вращающийся внешний шестиугольник
        float hexAngle = (float)(tick * (unlocked ? 0.45 : 0));
        drawPolygon(gfx, sx, sy, r + 7, 6, hexAngle,
                unlocked ? (0xBB000000 | schoolRgb) : (available ? 0x44666688 : 0x1A333344), 1);

        // Статичный внутренний шестиугольник (обратного направления)
        if (unlocked) {
            drawPolygon(gfx, sx, sy, r + 4, 6, hexAngle + (float)(Math.PI / 6),
                    0x44000000 | schoolRgb, 1);
        }

        // Пунктирное кольцо при открытии
        if (unlocked) {
            drawDashedCircle(gfx, sx, sy, r + 10, schoolRgb,
                    (int)(0x66 * pulse), 16);
        }

        // Тёмная подложка + школьная заливка
        drawCircle(gfx, sx, sy, r, 0xFF060410);
        int fillA = unlocked ? 0xAA : (available ? 0x44 : 0x20);
        drawCircle(gfx, sx, sy, r - 1, (fillA << 24) | (schoolRgb & 0xFFFFFF));

        // Внутреннее яркое ядро
        if (unlocked) {
            int coreA = (int)(80 * pulse);
            drawCircle(gfx, sx, sy, r / 3, (coreA << 24) | 0xFFFFFF);
        }

        // Бордюр (двойной при открытии)
        int borderA = unlocked ? 0xFF : (available ? 0xBB : 0x55);
        int borderC = unlocked
                ? (0xFF000000 | schoolRgb)
                : (available ? 0xFF9999BB : 0xFF2A2A3A);
        drawCircleOutline(gfx, sx, sy, r, borderC, unlocked ? 2 : 1);
        if (unlocked) {
            drawCircleOutline(gfx, sx, sy, r - 3, 0x44000000 | schoolRgb, 1);
        }

        // Символ ⚑ (флаг школы)
        String sym = "⚑";
        int symX = sx - font.width(sym) / 2;
        int symY = sy - font.lineHeight / 2;
        int symCol = unlocked
                ? (0xFF000000 | schoolRgb)
                : (available ? 0xFF8888AA : 0xFF3A3A4A);
        if (unlocked) {
            gfx.drawString(font, sym, symX + 1, symY + 1, 0x55000000 | (schoolRgb & 0xFFFFFF), false);
        }
        gfx.drawString(font, sym, symX, symY, symCol, false);

        // Искры на вершинах шестиугольника
        if (unlocked) {
            for (int i = 0; i < 6; i++) {
                double vAngle = hexAngle + i * Math.PI / 3;
                int vx = sx + (int)(Math.cos(vAngle) * (r + 7));
                int vy = sy + (int)(Math.sin(vAngle) * (r + 7));
                float vp = (float)(Math.sin(tick * 5f + i * 1.047f) * 0.5f + 0.5f);
                int va = (int)(230 * vp);
                gfx.fill(vx - 1, vy - 1, vx + 2, vy + 2, (va << 24) | (schoolRgb & 0xFFFFFF));
            }
        }

        // Кольцо выбора/наведения
        if (selected || hovered) {
            float sp = (float)(Math.sin(tick * 9f) * 0.4f + 0.6f);
            drawCircleOutline(gfx, sx, sy, r + 4, (int)(200 * sp) << 24 | (schoolRgb & 0xFFFFFF), 1);
        }
    }

    // ─── Рендер ноды заклинания (вращающийся бриллиант) ──────────────────────

    private void renderSpellNode(GuiGraphics gfx, int sx, int sy, int r,
                                  SkillNode node, boolean unlocked, boolean available,
                                  boolean selected, boolean hovered) {
        int schoolRgb = node.getSchool().color;
        float pulse = (float)(Math.sin(tick * 3.0f + node.getX() * 0.004f) * 0.4f + 0.6f);

        // Внешнее свечение
        int glowA = unlocked ? (int)(100 * pulse) : (available ? 22 : 8);
        renderGlow(gfx, sx, sy, r + 14, schoolRgb, glowA);

        // Вращающийся внешний бриллиант (4 стороны)
        float dia1 = (float)(tick * (unlocked ? 0.7f : 0) + Math.PI / 4);
        drawPolygon(gfx, sx, sy, r + 7, 4, dia1,
                unlocked ? (0xCC000000 | schoolRgb) : (available ? 0x33888899 : 0x12333344), 1);

        // Контр-вращающийся второй бриллиант
        if (unlocked) {
            float dia2 = (float)(-tick * 0.4f + Math.PI / 4);
            drawPolygon(gfx, sx, sy, r + 4, 4, dia2,
                    0x44000000 | schoolRgb, 1);
        }

        // Тёмная подложка + школьная заливка
        drawCircle(gfx, sx, sy, r, 0xFF060410);
        int fillA = unlocked ? 0xCC : (available ? 0x55 : 0x20);
        drawCircle(gfx, sx, sy, r - 1, (fillA << 24) | (schoolRgb & 0xFFFFFF));

        // Блик-самоцвет в центре
        if (unlocked) {
            int gemA = (int)(120 * pulse);
            drawCircle(gfx, sx, sy, r / 2, (gemA << 24) | 0xFFFFFF);
            int coreA = (int)(200 * pulse);
            drawCircle(gfx, sx, sy, r / 4, Math.min(255, coreA) << 24 | 0xFFFFDD);
        }

        // Бордюр + внутреннее кольцо
        drawCircleOutline(gfx, sx, sy, r,
                unlocked ? (0xFF000000 | schoolRgb) : (available ? 0xFF888899 : 0xFF2A2A3A), 2);
        if (unlocked) {
            drawCircleOutline(gfx, sx, sy, r - 3, 0x44000000 | schoolRgb, 1);
        }

        // Символ ✵
        String sym = "✵";
        int symX = sx - font.width(sym) / 2;
        int symY = sy - font.lineHeight / 2;
        int symCol = unlocked ? 0xFFFFFFCC : (available ? 0xFF888899 : 0xFF333344);
        if (unlocked) {
            // Свечение символа
            int shadowRgb = 0x44000000 | (schoolRgb & 0xFFFFFF);
            gfx.drawString(font, sym, symX + 1, symY + 1, shadowRgb, false);
            gfx.drawString(font, sym, symX - 1, symY - 1, shadowRgb, false);
            gfx.drawString(font, sym, symX + 1, symY - 1, shadowRgb, false);
            gfx.drawString(font, sym, symX - 1, symY + 1, shadowRgb, false);
        }
        gfx.drawString(font, sym, symX, symY, symCol, false);

        // Кольцо выбора — пунктирное
        if (selected || hovered) {
            float sp = (float)(Math.sin(tick * 9f) * 0.4f + 0.6f);
            drawDashedCircle(gfx, sx, sy, r + 5, schoolRgb, (int)(180 * sp), 12);
            drawCircleOutline(gfx, sx, sy, r + 3, (int)(100 * sp) << 24 | (schoolRgb & 0xFFFFFF), 1);
        }
    }

    // ─── Рендер пассивной ноды (орбитальный кристалл) ────────────────────────

    private void renderPassiveNode(GuiGraphics gfx, int sx, int sy, int r,
                                    SkillNode node, boolean unlocked, boolean available,
                                    boolean selected, boolean hovered) {
        int schoolRgb = node.getSchool().color;
        float pulse = (float)(Math.sin(tick * 2.0f + node.getY() * 0.005f) * 0.3f + 0.7f);

        // Внешнее свечение
        renderGlow(gfx, sx, sy, r + 10, schoolRgb, unlocked ? (int)(65 * pulse) : (available ? 18 : 6));

        // Орбитальные частицы при открытии
        if (unlocked) {
            for (int i = 0; i < 4; i++) {
                double orbitAngle = tick * 2.2 + i * Math.PI / 2;
                int ox = sx + (int)(Math.cos(orbitAngle) * (r + 5));
                int oy = sy + (int)(Math.sin(orbitAngle) * (r + 5));
                float op = (float)(Math.sin(tick * 4.5f + i * 1.571f) * 0.4f + 0.6f);
                int oa = (int)(200 * op);
                gfx.fill(ox, oy, ox + 2, oy + 2, (oa << 24) | (schoolRgb & 0xFFFFFF));
            }
        }

        // Тёмная подложка + заливка
        drawCircle(gfx, sx, sy, r, 0xFF060410);
        int fillA = unlocked ? 0x88 : (available ? 0x44 : 0x20);
        drawCircle(gfx, sx, sy, r - 1, (fillA << 24) | (schoolRgb & 0xFFFFFF));

        // Двойной бордюр при открытии
        int borderC = unlocked
                ? (0xFF000000 | schoolRgb)
                : (available ? 0xFFAAAAAA : 0xFF333344);
        drawCircleOutline(gfx, sx, sy, r, borderC, unlocked ? 2 : 1);
        if (unlocked) {
            drawCircleOutline(gfx, sx, sy, r + 2, 0x44000000 | schoolRgb, 1);
        }

        // Символ ◈
        String sym = "◈";
        int symX = sx - font.width(sym) / 2;
        int symY = sy - font.lineHeight / 2;
        int symCol = unlocked ? 0xFFCCEECC : (available ? 0xFF777788 : 0xFF333344);
        if (unlocked) {
            gfx.drawString(font, sym, symX + 1, symY + 1, 0x44000000 | (schoolRgb & 0xFFFFFF), false);
        }
        gfx.drawString(font, sym, symX, symY, symCol, false);

        // Кольцо выбора/наведения
        if (selected || hovered) {
            float sp = (float)(Math.sin(tick * 9f) * 0.4f + 0.6f);
            drawCircleOutline(gfx, sx, sy, r + 4, (int)(170 * sp) << 24 | (schoolRgb & 0xFFFFFF), 1);
        }
    }

    // ─── 4. Правая панель ─────────────────────────────────────────────────────

    private void renderInfoPanel(GuiGraphics gfx, int mouseX, int mouseY) {
        int px = treeAreaWidth() + MARGIN * 2;
        int py = MARGIN;
        int pw = INFO_PANEL_WIDTH;
        int ph = height - MARGIN * 2;

        // Основной фон
        gfx.fill(px, py, px + pw, py + ph, COL_PANEL_BG);
        // Тонкий светлый блик сверху
        gfx.fill(px + 3, py + 3, px + pw - 3, py + 4, 0x08FFFFFF);

        // Орнаментальная рамка — внешняя тёмная
        gfx.fill(px,      py,           px + pw,      py + 1,      COL_PANEL_BORD);
        gfx.fill(px,      py + ph - 1,  px + pw,      py + ph,     COL_PANEL_BORD);
        gfx.fill(px,      py,           px + 1,        py + ph,    COL_PANEL_BORD);
        gfx.fill(px + pw - 1, py,       px + pw,       py + ph,    COL_PANEL_BORD);

        // Внутренняя рамка чуть ярче
        gfx.fill(px + 3,      py + 3,       px + pw - 3,  py + 4,       0xFF100830);
        gfx.fill(px + 3,      py + ph - 4,  px + pw - 3,  py + ph - 3,  0xFF100830);
        gfx.fill(px + 3,      py + 3,       px + 4,        py + ph - 3, 0xFF100830);
        gfx.fill(px + pw - 4, py + 3,       px + pw - 3,   py + ph - 3, 0xFF100830);

        // Угловые золотые украшения
        drawPanelCorner(gfx, px + 4,       py + 4,       10, true,  true);
        drawPanelCorner(gfx, px + pw - 4,  py + 4,       10, false, true);
        drawPanelCorner(gfx, px + 4,       py + ph - 4,  10, true,  false);
        drawPanelCorner(gfx, px + pw - 4,  py + ph - 4,  10, false, false);

        int y = py + 14;

        // Заголовок очков
        gfx.drawCenteredString(font, "✦  ОЧКИ НАВЫКОВ  ✦", px + pw / 2, y, COL_GOLD);
        y += 13;

        // Число очков с цветом
        String pts = String.valueOf(skillPoints);
        int ptsCol = skillPoints > 0 ? COL_GREEN : COL_RED;
        gfx.drawString(font, pts, px + pw / 2 - font.width(pts) / 2 + 1, y + 1, 0x80000000, false);
        gfx.drawCenteredString(font, pts, px + pw / 2, y, ptsCol);
        y += 16;

        y = drawSeparator(gfx, px, y, pw, COL_PANEL_BORD);

        if (selectedNode != null) {
            y = renderNodeInfo(gfx, px, y, pw, selectedNode, mouseX, mouseY);
        } else {
            // Подсказка
            gfx.drawCenteredString(font, "§7Выберите ноду", px + pw / 2, y + 22, COL_GRAY);
            gfx.drawCenteredString(font, "§8для просмотра", px + pw / 2, y + 34, 0xFF555566);
            learnBtnX    = -1;
            wheelBtnRowY = -1;
            selectedSpellId = null;
        }
    }

    /** Угловое L-украшение для панели информации */
    private void drawPanelCorner(GuiGraphics gfx, int x, int y, int size, boolean left, boolean top) {
        int dx = left ? 1 : -1;
        int dy = top  ? 1 : -1;
        int ex = left ? x + size : x - size;
        int ey = top  ? y + size : y - size;
        // Горизонталь
        gfx.fill(Math.min(x, ex), y, Math.max(x, ex) + 1, y + 1, COL_GOLD_DIM);
        // Вертикаль
        gfx.fill(x, Math.min(y, ey), x + 1, Math.max(y, ey) + 1, COL_GOLD_DIM);
    }

    /** Детальная информация о выбранной ноде */
    private int renderNodeInfo(GuiGraphics gfx, int px, int startY, int pw,
                                SkillNode node, int mouseX, int mouseY) {
        learnBtnX    = -1;
        wheelBtnRowY = -1;
        selectedSpellId = null;

        int y = startY;
        boolean unlocked  = unlockedNodes.contains(node.getId());
        boolean available = isAvailable(node);
        int schoolRgb = 0xFF000000 | node.getSchool().color;

        // Название ноды
        gfx.drawString(font, node.getName(), px + pw / 2 - font.width(node.getName()) / 2 + 1, y + 1, 0x80000000, false);
        gfx.drawCenteredString(font, node.getName(), px + pw / 2, y, schoolRgb);
        y += 12;

        // Школа
        String schoolTxt = node.getSchool() == MagicSchool.NONE
                ? "§8Нейтральная"
                : "§8" + node.getSchool().displayName;
        gfx.drawCenteredString(font, schoolTxt, px + pw / 2, y, COL_GRAY);
        y += 14;

        y = drawSeparator(gfx, px, y, pw, COL_PANEL_BORD);

        // Описание с переносом
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
                    px + pw / 2, y, COL_GREEN);
            y += 14;
        }

        // Заклинание + кнопки слотов
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

                if (unlocked) {
                    y = renderWheelSlotButtons(gfx, px, y, pw, node.getSpellId(), mouseX, mouseY);
                }
            }
        }

        y += 6;
        y = drawSeparator(gfx, px, y, pw, COL_PANEL_BORD);
        y += 4;

        // Статус / кнопка
        if (unlocked) {
            gfx.drawCenteredString(font, "§a✦  ОТКРЫТО  ✦", px + pw / 2, y, COL_GREEN);
            learnBtnX = -1;
        } else if (available && skillPoints >= node.getCost()) {
            // Активная кнопка «Изучить»
            int bx = px + 8, bw = pw - 16, bh = 16;
            int by = y;
            boolean hover = mouseX >= bx && mouseX <= bx + bw
                         && mouseY >= by && mouseY <= by + bh;

            // Фон кнопки с эффектом свечения при наведении
            gfx.fill(bx, by, bx + bw, by + bh,
                    hover ? 0xFF1E4A12 : 0xFF102210);
            if (hover) {
                gfx.fill(bx, by, bx + bw, by + 1, 0xFF77CC44);
                gfx.fill(bx, by + bh - 1, bx + bw, by + bh, 0xFF77CC44);
                gfx.fill(bx, by, bx + 1, by + bh, 0xFF77CC44);
                gfx.fill(bx + bw - 1, by, bx + bw, by + bh, 0xFF77CC44);
            } else {
                gfx.fill(bx, by, bx + bw, by + 1, 0xFF3A7722);
                gfx.fill(bx, by + bh - 1, bx + bw, by + bh, 0xFF3A7722);
            }
            gfx.drawCenteredString(font,
                    "◆ ИЗУЧИТЬ  (" + node.getCost() + " очко)",
                    bx + bw / 2, by + 4,
                    hover ? COL_GOLD_BRIGHT : COL_GREEN);

            learnBtnX = bx; learnBtnY = by; learnBtnW = bw; learnBtnH = bh;
            y += bh + 4;
        } else if (available) {
            gfx.drawCenteredString(font, "§c✗  Мало очков", px + pw / 2, y, COL_RED);
            learnBtnX = -1;
            y += 14;
        } else {
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
        int treeW = treeAreaWidth() + MARGIN;

        // Полупрозрачный тёмный фон заголовка
        gfx.fill(0, 0, treeW, 22, 0xCC060410);

        // Нижняя линия заголовка (двойная)
        gfx.fill(0, 20, treeW, 21, COL_GOLD_DIM);
        gfx.fill(0, 21, treeW, 22, 0x44C9A227);

        // Боковые акценты
        gfx.fill(0, 0, 2, 22, COL_GOLD_DIM);
        gfx.fill(treeW - 2, 0, treeW, 22, COL_GOLD_DIM);

        // Заголовок с золотым свечением
        String title = "✦  ДРЕВО  ПОЗНАНИЯ  ✦";
        int cx = (treeW) / 2;
        // Тень
        gfx.drawCenteredString(font, title, cx + 1, 7, 0x80000000);
        // Основной текст
        gfx.drawCenteredString(font, title, cx, 6, COL_GOLD);

        // Число открытых нод справа от заголовка
        int opened = unlockedNodes.size();
        int total  = SkillTree.getAllNodes().size();
        String progress = opened + "/" + total;
        gfx.drawString(font, progress, treeW - font.width(progress) - 8, 7, COL_GOLD_DIM, false);
    }

    // ─── Вспомогательные методы рисования ────────────────────────────────────

    private int drawSeparator(GuiGraphics gfx, int px, int y, int pw, int col) {
        // Центральный акцент
        gfx.fill(px + 5, y, px + pw - 5, y + 1, col);
        // Маленькие точки на концах
        gfx.fill(px + 4, y - 1, px + 7, y + 2, 0x33C9A227);
        gfx.fill(px + pw - 7, y - 1, px + pw - 4, y + 2, 0x33C9A227);
        return y + 7;
    }

    /** Нарисовать заполненный круг */
    private void drawCircle(GuiGraphics gfx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int)Math.sqrt(Math.max(0.0, (double)r * r - (double)dy * dy));
            gfx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** Нарисовать контур круга */
    private void drawCircleOutline(GuiGraphics gfx, int cx, int cy, int r, int color, int thickness) {
        for (int t = 0; t < thickness; t++) {
            int ri = r - t;
            if (ri <= 0) break;
            for (int dy = -ri; dy <= ri; dy++) {
                int dxO = (int)Math.sqrt(Math.max(0.0, (double)ri * ri - (double)dy * dy));
                int dxI = (ri > 1)
                        ? (int)Math.sqrt(Math.max(0.0, (double)(ri - 1) * (ri - 1) - (double)dy * dy))
                        : 0;
                if (dxO > dxI) {
                    gfx.fill(cx - dxO, cy + dy, cx - dxI, cy + dy + 1, color);
                    gfx.fill(cx + dxI, cy + dy, cx + dxO + 1, cy + dy + 1, color);
                }
            }
        }
    }

    /** Мягкий круглый ореол — концентрические круги убывающей прозрачности */
    private void renderGlow(GuiGraphics gfx, int cx, int cy, int radius, int rgb, int maxAlpha) {
        int base = rgb & 0xFFFFFF;
        int step = Math.max(2, radius / 10);
        for (int r = radius; r > 0; r -= step) {
            float t = 1.0f - (float)r / radius;
            int alpha = (int)(maxAlpha * t * t);
            drawCircle(gfx, cx, cy, r, (alpha << 24) | base);
        }
    }

    /** Нарисовать правильный многоугольник */
    private void drawPolygon(GuiGraphics gfx, int cx, int cy, int r, int sides,
                              float angleOffset, int color, int lineWidth) {
        for (int i = 0; i < sides; i++) {
            double a1 = angleOffset + i * 2.0 * Math.PI / sides;
            double a2 = angleOffset + (i + 1) * 2.0 * Math.PI / sides;
            int x1 = cx + (int)(Math.cos(a1) * r);
            int y1 = cy + (int)(Math.sin(a1) * r);
            int x2 = cx + (int)(Math.cos(a2) * r);
            int y2 = cy + (int)(Math.sin(a2) * r);
            drawLine(gfx, x1, y1, x2, y2, color, lineWidth);
        }
    }

    /**
     * Пунктирная окружность — для декоративных колец.
     * @param dashes количество сегментов (чётное)
     */
    private void drawDashedCircle(GuiGraphics gfx, int cx, int cy, int r,
                                   int rgb, int alpha, int dashes) {
        int color = (alpha << 24) | (rgb & 0xFFFFFF);
        // Угловой шаг между точками окружности в радианах
        double totalAngle = 2.0 * Math.PI;
        double step = 0.04; // шаг по углу
        double dashLen = totalAngle / dashes;
        double gapFrac = 0.45; // доля длины пунктира занимаемая пробелом

        for (int d = 0; d < dashes; d++) {
            double startA = d * dashLen;
            double endA   = startA + dashLen * (1.0 - gapFrac);
            for (double a = startA; a < endA; a += step) {
                int x = cx + (int)(Math.cos(a) * r);
                int y = cy + (int)(Math.sin(a) * r);
                gfx.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    /** Нарисовать линию пикселями (алгоритм Брезенхема через fill) */
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

    // ─── Синхронизация с capability ───────────────────────────────────────────

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

            // Кнопки «В колесо»
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
                        String currentInSlot = (i < spellSlots.length) ? spellSlots[i] : null;
                        String newSpellId = selectedSpellId.equals(currentInSlot)
                                ? null : selectedSpellId;
                        ModNetwork.CHANNEL.sendToServer(new SpellSlotPacket(i, newSpellId));
                        return true;
                    }
                }
            }

            // Кнопка «Изучить»
            if (learnBtnX >= 0 && selectedNode != null
                    && mouseX >= learnBtnX && mouseX <= learnBtnX + learnBtnW
                    && mouseY >= learnBtnY && mouseY <= learnBtnY + learnBtnH) {
                learnNode(selectedNode);
                return true;
            }

            // Клик по ноде
            if (mouseX <= treeAreaWidth() + MARGIN) {
                for (SkillNode node : SkillTree.getAllNodes()) {
                    if (isHovered(node, (int)mouseX, (int)mouseY)) {
                        selectedNode = node;
                        return true;
                    }
                }
                selectedNode = null;
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
            zoom = Math.max(0.25f, Math.min(2.5f, zoom + (float)delta * 0.08f));
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

    private int renderWheelSlotButtons(GuiGraphics gfx, int px, int y, int pw,
                                        String spellId, int mouseX, int mouseY) {
        y = drawSeparator(gfx, px, y, pw, COL_PANEL_BORD);
        gfx.drawCenteredString(font, "§8В КОЛЕСО §7(повтор — убрать)§8:", px + pw / 2, y, COL_GRAY);
        y += 11;

        final int BTN_W = 17, BTN_H = 13, GAP = 2;
        int rowW   = 5 * BTN_W + 4 * GAP;
        int startX = px + (pw - rowW) / 2;

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
            boolean hoverAssigned = isAssigned && hovered;

            int bgCol  = isAssigned
                    ? (hoverAssigned ? 0xFF301616 : 0xFF122612)
                    : (hovered       ? 0xFF161628 : 0xFF0A0A1A);
            int rimCol = isAssigned
                    ? (hoverAssigned ? 0xFFAA3333 : 0xFF44AA22)
                    : (hovered       ? 0xFF5555AA : 0xFF222240);
            int txtCol = isAssigned
                    ? (hoverAssigned ? COL_RED  : COL_GREEN)
                    : (hovered       ? COL_WHITE : COL_GRAY);

            gfx.fill(bx, by, bx + BTN_W, by + BTN_H, bgCol);
            gfx.fill(bx, by, bx + BTN_W, by + 1, rimCol);
            gfx.fill(bx, by + BTN_H - 1, bx + BTN_W, by + BTN_H, rimCol);
            gfx.fill(bx, by, bx + 1, by + BTN_H, rimCol);
            gfx.fill(bx + BTN_W - 1, by, bx + BTN_W, by + BTN_H, rimCol);
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
