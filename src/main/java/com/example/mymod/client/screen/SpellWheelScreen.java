package com.example.mymod.client.screen;

import com.example.mymod.network.ModNetwork;
import com.example.mymod.network.SpellSlotPacket;
import com.example.mymod.skill.IPlayerSkills;
import com.example.mymod.skill.PlayerSkillsCapability;
import com.example.mymod.spell.MagicSchool;
import com.example.mymod.spell.Spell;
import com.example.mymod.spell.SpellRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Круговой селектор заклинаний — «Колесо Магии».
 *
 * Внешний вид вдохновлён системой выбора оружия из GTA 4/5,
 * адаптирован под тёмно-фэнтезийный стиль мода.
 *
 * Структура:
 *   ● Полупрозрачный тёмный оверлей на весь экран
 *   ● Большое центральное кольцо с 10 узлами по кругу
 *   ● Каждый узел — круг с иконкой/именем заклинания
 *   ● Наведение мышью подсвечивает сегмент
 *   ● Активный слот отмечен золотым ореолом
 *   ● Центральный круг: имя + стоимость активного заклинания
 *
 * Управление:
 *   ЛКМ по узлу — выбрать активный слот (клиентски мгновенно + сервер-sync)
 *   ESC / R — закрыть без изменений
 *
 * Позиция слотов (начиная сверху, по часовой стрелке):
 *   Слот 0 = 270° (верх), шаг 36° по часовой
 */
public class SpellWheelScreen extends Screen {

    // ─── Геометрия колеса ─────────────────────────────────────────────────────

    /** Радиус кольца до центров узлов */
    private static final float RING_RADIUS  = 92f;
    /** Радиус каждого узла-круга */
    private static final float NODE_RADIUS  = 28f;
    /** Радиус центрального круга с информацией */
    private static final float CENTER_RADIUS = 38f;
    /** Мёртвая зона: если мышь ближе этого — нет hover-выбора */
    private static final float DEAD_ZONE    = 20f;

    // ─── Цвета ───────────────────────────────────────────────────────────────

    private static final int COL_OVERLAY    = 0xBB050310;   // фон экрана
    private static final int COL_RING_BG    = 0xCC0A0820;   // фон внешнего кольца
    private static final int COL_NODE_BASE  = 0xDD0D0A22;   // фон узла
    private static final int COL_NODE_HOVER = 0xEE1A1535;   // фон узла при наведении
    private static final int COL_GOLD       = 0xFFC9A227;
    private static final int COL_GOLD_DIM   = 0xFF6B5010;
    private static final int COL_WHITE      = 0xFFFFFFFF;
    private static final int COL_GRAY       = 0xFF888888;
    private static final int COL_EMPTY      = 0xFF333044;
    private static final int COL_CENTER_BG  = 0xEE08061A;

    // ─── Состояние ───────────────────────────────────────────────────────────

    /** Центр колеса в экранных координатах */
    private int cx, cy;

    /** Слот, над которым находится мышь. -1 = нет */
    private int hoveredSlot = -1;

    /** Текущие данные из capability */
    private String[] spellSlots = new String[IPlayerSkills.SPELL_SLOTS];
    private int activeSlot = 0;

    /** Счётчик анимации */
    private float tick = 0f;

    // ─── Конструктор ──────────────────────────────────────────────────────────

    public SpellWheelScreen() {
        super(Component.literal("Колесо Магии"));
    }

    @Override
    protected void init() {
        super.init();
        cx = width / 2;
        cy = height / 2;
        refreshData();
    }

    // ─── Главный рендер ───────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        tick += 0.05f;
        refreshData();
        hoveredSlot = calcHoveredSlot(mouseX, mouseY);

        // 1. Затемнение экрана
        gfx.fill(0, 0, width, height, COL_OVERLAY);

        // 2. Внешнее декоративное кольцо
        renderOuterRing(gfx);

        // 3. Соединительные линии от центра к узлам
        renderSpokes(gfx);

        // 4. Узлы заклинаний
        for (int i = 0; i < IPlayerSkills.SPELL_SLOTS; i++) {
            renderSlotNode(gfx, i, mouseX, mouseY);
        }

        // 5. Центральный круг с информацией
        renderCenterInfo(gfx);

        // 6. Подсказка управления
        renderHint(gfx);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    // ─── 1/2. Внешнее декоративное кольцо ─────────────────────────────────────

    /**
     * Два концентрических кольца: внешний ободок + внутренний контур.
     * Пульсирует по синусу для живости.
     */
    private void renderOuterRing(GuiGraphics gfx) {
        float pulse = (float)(Math.sin(tick * 1.5f) * 0.12f + 0.88f);
        int outerR = (int)((RING_RADIUS + NODE_RADIUS + 16) * pulse);

        // Мягкое круговое свечение
        for (int r = outerR; r > outerR - 30; r -= 4) {
            int alpha = 25 * (outerR - r) / 30;
            drawCircleOutline(gfx, cx, cy, r, (alpha << 24) | 0x6655CC, 1);
        }

        // Золотой контурный круг
        int goldAlpha = (int)(180 * pulse);
        drawCircleOutline(gfx, cx, cy, outerR - 4, (goldAlpha << 24) | (COL_GOLD & 0xFFFFFF), 2);

        // Второй, чуть тоньше, внутри
        drawCircleOutline(gfx, cx, cy, (int)(CENTER_RADIUS + 6), 0x99332255, 1);
    }

    // ─── 3. Спицы от центра к узлам ──────────────────────────────────────────

    private void renderSpokes(GuiGraphics gfx) {
        for (int i = 0; i < IPlayerSkills.SPELL_SLOTS; i++) {
            double angle = slotAngle(i);
            int nx = slotX(i);
            int ny = slotY(i);

            boolean active = (i == activeSlot);
            boolean hover  = (i == hoveredSlot);

            int lineCol;
            if (active)     lineCol = 0xAAC9A227;
            else if (hover) lineCol = 0x66AAAACC;
            else            lineCol = 0x33555577;

            // Рисуем от края центра до края узла
            int startDist = (int)CENTER_RADIUS + 4;
            int endDist   = (int)(RING_RADIUS - NODE_RADIUS) - 2;
            int sx = cx + (int)(Math.cos(angle) * startDist);
            int sy = cy + (int)(Math.sin(angle) * startDist);
            int ex = cx + (int)(Math.cos(angle) * endDist);
            int ey = cy + (int)(Math.sin(angle) * endDist);

            drawLine(gfx, sx, sy, ex, ey, lineCol, 1);
        }
    }

    // ─── 4. Узлы заклинаний ───────────────────────────────────────────────────

    private void renderSlotNode(GuiGraphics gfx, int slot, int mouseX, int mouseY) {
        int nx = slotX(slot);
        int ny = slotY(slot);
        int r  = (int)NODE_RADIUS;

        boolean active  = (slot == activeSlot);
        boolean hovered = (slot == hoveredSlot);

        // Слот пуст: null, пустая строка, или ID не найден в реестре (защита от рассинхрона)
        String slotId = spellSlots[slot];
        Spell spell = (slotId != null && !slotId.isEmpty()) ? SpellRegistry.get(slotId) : null;
        boolean isEmpty = (spell == null);

        MagicSchool school = (spell != null) ? spell.getSchool() : MagicSchool.NONE;
        int schoolRgb = school.color;

        // ── Ореол ────────────────────────────────────────────────────────────
        if (active) {
            float gPulse = (float)(Math.sin(tick * 3f) * 0.3f + 0.7f);
            renderGlow(gfx, nx, ny, r + 14, COL_GOLD & 0xFFFFFF, (int)(100 * gPulse));
        }
        if (hovered && !active) {
            renderGlow(gfx, nx, ny, r + 8, schoolRgb, 60);
        }

        // ── Фон узла ─────────────────────────────────────────────────────────
        int bgCol = hovered ? COL_NODE_HOVER : COL_NODE_BASE;
        drawCircle(gfx, nx, ny, r, bgCol);

        // Цветная заливка от школы
        int fillAlpha = isEmpty ? 0x18 : (hovered ? 0x55 : 0x33);
        drawCircle(gfx, nx, ny, r - 1, (fillAlpha << 24) | (schoolRgb & 0xFFFFFF));

        // ── Ободок ───────────────────────────────────────────────────────────
        int borderCol;
        int borderW;
        if (active) {
            borderCol = COL_GOLD;
            borderW   = 2;
        } else if (hovered) {
            borderCol = 0xFFCCCCDD;
            borderW   = 1;
        } else if (!isEmpty) {
            borderCol = 0xFF000000 | schoolRgb;
            borderW   = 1;
        } else {
            borderCol = COL_EMPTY;
            borderW   = 1;
        }
        drawCircleOutline(gfx, nx, ny, r, borderCol, borderW);

        // ── Номер слота (маленький, сверху-слева от узла) ────────────────────
        String numStr = String.valueOf(slot + 1);
        int numX = nx - r / 2 - font.width(numStr) / 2;
        int numY = ny - r - 2;
        int numCol = active ? COL_GOLD : 0xFF555566;
        gfx.drawString(font, numStr, numX, numY, numCol, false);

        // ── Содержимое узла ───────────────────────────────────────────────────
        if (isEmpty) {
            // Пустой слот — серый прочерк
            String dash = "—";
            gfx.drawCenteredString(font, dash,
                    nx, ny - font.lineHeight / 2, COL_EMPTY);
        } else {
            // Символ школы (иконка)
            String icon = getSchoolIcon(school);
            int iconCol = hovered ? (0xFF000000 | schoolRgb) : ((0xCC000000) | schoolRgb);
            gfx.drawCenteredString(font, icon, nx, ny - font.lineHeight - 1, iconCol);

            // Краткое имя заклинания (обрезаем до ширины узла)
            String name = spell.getDisplayName();
            // Обрезаем если не помещается в диаметр узла
            int maxW = (r * 2) - 6;
            while (name.length() > 3 && font.width(name) > maxW) {
                name = name.substring(0, name.length() - 1);
            }
            if (font.width(spell.getDisplayName()) > maxW) name += "…";

            int nameCol = active ? COL_GOLD :
                          hovered ? COL_WHITE : 0xFFCCCCCC;
            gfx.drawCenteredString(font, name, nx, ny + 2, nameCol);
        }
    }

    // ─── 5. Центральный круг ─────────────────────────────────────────────────

    private void renderCenterInfo(GuiGraphics gfx) {
        int r = (int)CENTER_RADIUS;

        // Фон
        drawCircle(gfx, cx, cy, r, COL_CENTER_BG);
        drawCircleOutline(gfx, cx, cy, r, COL_GOLD_DIM, 1);

        // Заглавный символ (звезда мода)
        float pulse = (float)(Math.sin(tick * 2f) * 0.2f + 0.8f);
        int starAlpha = (int)(200 * pulse);
        gfx.drawCenteredString(font, "✦", cx, cy - font.lineHeight / 2,
                (starAlpha << 24) | (COL_GOLD & 0xFFFFFF));

        // Информация об активном или hovered заклинании
        // Защита: ограничиваем infoSlot допустимым диапазоном
        int infoSlot = (hoveredSlot >= 0) ? hoveredSlot : activeSlot;
        if (infoSlot < 0 || infoSlot >= IPlayerSkills.SPELL_SLOTS) infoSlot = 0;
        String sid = spellSlots[infoSlot];
        // Пустая строка тоже считается "нет заклинания" — защита от рассинхрона
        Spell spell = (sid != null && !sid.isEmpty()) ? SpellRegistry.get(sid) : null;

        if (spell != null) {
            // Ниже центрального круга: имя + мана
            int infoY = cy + r + 8;
            gfx.drawCenteredString(font, "§e" + spell.getDisplayName(), cx, infoY, COL_GOLD);
            infoY += 12;
            gfx.drawCenteredString(font, "§9◈ §b" + spell.getManaCost(), cx, infoY, 0xFF88AAFF);
            infoY += 10;
            String cdStr = spell.getCooldownSeconds() + "s";
            gfx.drawCenteredString(font, "§8⏱ §7" + cdStr, cx, infoY, COL_GRAY);
        } else {
            // Пустой слот — подсказка
            int infoY = cy + r + 10;
            gfx.drawCenteredString(font, "§8— Слот пуст —", cx, infoY, COL_EMPTY);
        }
    }

    // ─── 6. Подсказка управления ──────────────────────────────────────────────

    private void renderHint(GuiGraphics gfx) {
        int y = height - 20;
        gfx.drawCenteredString(font,
                "§7[ЛКМ] Выбрать  §8│  §7[ESC] Закрыть",
                width / 2, y, COL_GRAY);
    }

    // ─── Геометрия ────────────────────────────────────────────────────────────

    /**
     * Угол центра слота в радианах.
     * Слот 0 = 270° (вершина, 12 часов), далее по часовой.
     */
    private static double slotAngle(int slot) {
        return Math.toRadians(270.0 + slot * 36.0);
    }

    private int slotX(int slot) {
        return cx + (int)(Math.cos(slotAngle(slot)) * RING_RADIUS);
    }

    private int slotY(int slot) {
        return cy + (int)(Math.sin(slotAngle(slot)) * RING_RADIUS);
    }

    /**
     * Определить слот под курсором по углу от центра.
     * Возвращает -1 если мышь в мёртвой зоне.
     */
    private int calcHoveredSlot(double mouseX, double mouseY) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < DEAD_ZONE) return -1;

        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) angle += 360.0;

        // Слот 0 начинается с 270°, нормализуем
        double normalized = (angle - 270.0 + 360.0) % 360.0;
        int slot = (int)(normalized / 36.0) % IPlayerSkills.SPELL_SLOTS;
        return slot;
    }

    // ─── Ввод ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int slot = calcHoveredSlot(mouseX, mouseY);
            if (slot >= 0) {
                selectSlot(slot);
                onClose();
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC или повторное нажатие R — закрыть
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_R) {
            onClose();
            return true;
        }
        // Цифры 1-9 и 0 — быстрый выбор слота (0→слот9, 1→слот0 и т.д.)
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            selectSlot(keyCode - GLFW.GLFW_KEY_1);
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_0) {
            selectSlot(9);
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Выбрать слот активным.
     * Клиентски обновляет локальную capability, отправляет пакет
     * SetActiveSlotPacket (нет, используем просто SpellSlotPacket с "set active" семантикой).
     *
     * На самом деле: activeSlot меняется через SpellSlotPacket с особым индексом?
     * Нет — для смены activeSlot нужен отдельный механизм.
     * Упрощение: при клике сразу меняем activeSlot клиентски через capability,
     * и отправляем на сервер специальный SpellSlotPacket с index = -1 и spellId = "active:N".
     *
     * Ещё проще — добавляем "select" слой прямо в SpellSlotPacket:
     * если spellId начинается с "::select", меняем activeSlot.
     *
     * Самый чистый подход: просто изменить activeSlot клиентски (у него нет серверной логики)
     * и потом отправить on cast. Но это рассинхронит сервер и клиент.
     *
     * Правильное решение: отдельный маленький пакет SetActiveSlotPacket.
     * Для сохранения простоты пока используем SpellSlotPacket с spellId = "",
     * но slotIndex = index + SPELL_SLOTS (как сигнал "set active").
     * Сервер обработает это.
     *
     * Или ещё проще: CastSpellPacket уже берёт activeSlot с сервера.
     * Значит activeSlot нужен и на сервере. Нужен отдельный пакет.
     *
     * Реализуем через SpellSlotPacket(slot, "::active") — сервер распознаёт
     * специальный маркер и меняет только activeSlot.
     */
    private void selectSlot(int slot) {
        // Клиентски мгновенно обновляем для плавности UI
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getCapability(PlayerSkillsCapability.SKILLS)
                    .ifPresent(skills -> skills.setActiveSlot(slot));
        }
        // Синхронизируем с сервером: специальный маркер "::active" сообщает серверу
        // изменить только activeSlot, не трогая содержимое слота
        ModNetwork.CHANNEL.sendToServer(new SpellSlotPacket(slot, "::active"));
    }

    // ─── Получение иконки школы ───────────────────────────────────────────────

    private static String getSchoolIcon(MagicSchool school) {
        return switch (school) {
            case CRIMSON  -> "⚔";
            case HOLY     -> "✦";
            case CULTIST  -> "☽";
            case PUPPET   -> "✿";
            default       -> "✵";
        };
    }

    // ─── Обновление данных из capability ─────────────────────────────────────

    private void refreshData() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.getCapability(PlayerSkillsCapability.SKILLS).ifPresent(skills -> {
            for (int i = 0; i < IPlayerSkills.SPELL_SLOTS; i++) {
                spellSlots[i] = skills.getSpellInSlot(i);
            }
            // Ограничиваем activeSlot допустимым диапазоном на случай рассинхрона
            int slot = skills.getActiveSlot();
            activeSlot = (slot >= 0 && slot < IPlayerSkills.SPELL_SLOTS) ? slot : 0;
        });
    }

    // ─── Утилиты рисования (копия из SkillTreeScreen) ────────────────────────

    private void drawCircle(GuiGraphics gfx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int)Math.sqrt(Math.max(0.0, (double)r * r - (double)dy * dy));
            gfx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private void drawCircleOutline(GuiGraphics gfx, int cx, int cy, int r,
                                   int color, int thickness) {
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

    private void renderGlow(GuiGraphics gfx, int cx, int cy, int radius, int rgb, int maxAlpha) {
        int base = rgb & 0xFFFFFF;
        for (int r = radius; r > 0; r -= 3) {
            int alpha = maxAlpha * (radius - r) / radius;
            drawCircle(gfx, cx, cy, r, (alpha << 24) | base);
        }
    }

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

    // ─── Настройки экрана ─────────────────────────────────────────────────────

    /** Экран не ставит игру на паузу — мир продолжает тикать */
    @Override
    public boolean isPauseScreen() { return false; }
}
