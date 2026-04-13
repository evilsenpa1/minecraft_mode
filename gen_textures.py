"""
Генерирует PNG-текстуры для мода:

  Снаряд «Искра»:
    entity/spark_core.png      — пылающее ядро (белый центр → оранжевый → красный)
    entity/spark_shard.png     — огненный осколок-ромб

  HUD маны — Кристальный Столп:
    gui/mana_bar_bg.png    — тёмный фон пустого бара (20×185)
    gui/mana_bar_fill.png  — светящийся портальный слой заливки (20×185)
    gui/mana_bar_frame.png — золотая рамка + кристальный декор (32×256)

Запускать из корня мода:  python gen_textures.py
"""
import struct, zlib, os, math, random

# ─── PNG helpers ─────────────────────────────────────────────────────────────

def make_png(pixels, width, height):
    """Собирает PNG из списка (r,g,b,a) пикселей."""
    def chunk(name: bytes, data: bytes) -> bytes:
        c = name + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xFFFFFFFF)

    raw = bytearray()
    for y in range(height):
        raw += b'\x00'
        for x in range(width):
            r, g, b, a = pixels[y * width + x]
            raw += bytes([r, g, b, a])

    sig  = b'\x89PNG\r\n\x1a\n'
    ihdr = chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    idat = chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    iend = chunk(b'IEND', b'')
    return sig + ihdr + idat + iend


def make_canvas(w, h, fill=(0, 0, 0, 0)):
    return [fill] * (w * h)


def fp(canvas, w, x, y, r, g, b, a=255):
    """Установить пиксель (перезаписывает)."""
    h = len(canvas) // w
    if 0 <= x < w and 0 <= y < h:
        canvas[y * w + x] = (r, g, b, a)


def fp_blend(canvas, w, x, y, r, g, b, a):
    """Наложить пиксель поверх существующего (alpha-blend over)."""
    h = len(canvas) // w
    if 0 <= x < w and 0 <= y < h:
        br, bg, bb, ba = canvas[y * w + x]
        if ba == 0:
            canvas[y * w + x] = (r, g, b, a)
        else:
            fa = a / 255.0
            nr = int(r * fa + br * (1 - fa))
            ng = int(g * fa + bg * (1 - fa))
            nb = int(b * fa + bb * (1 - fa))
            na = min(255, ba + int(a * (1 - ba / 255.0)))
            canvas[y * w + x] = (nr, ng, nb, na)


def fp_max(canvas, w, x, y, r, g, b, a=255):
    """Установить пиксель только если новое значение альфа больше текущего."""
    h = len(canvas) // w
    if 0 <= x < w and 0 <= y < h:
        if canvas[y * w + x][3] < a:
            canvas[y * w + x] = (r, g, b, a)


def lerp(a, b, t):
    return a + (b - a) * t


def lerpi(a, b, t):
    return int(lerp(a, b, t))


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, v))


# ─── Цветовые константы ───────────────────────────────────────────────────────

# Золото — 6 уровней яркости
GOLD_OUTERMOST = (0x3A, 0x25, 0x04)  # самая тёмная внешняя обводка
GOLD_OUTER  = (0x5A, 0x3D, 0x08)     # внешний тёмный обвод
GOLD_DARK   = (0x7A, 0x52, 0x10)     # тёмная тень
GOLD_MID    = (0xCC, 0x98, 0x20)     # основное золото
GOLD_BRIGHT = (0xD4, 0xA8, 0x30)     # чуть светлее
GOLD_SHINE  = (0xFF, 0xE0, 0x60)     # верхний блик
GOLD_ACCENT = (0xFF, 0xD7, 0x00)     # угловой акцент
GOLD_WHITE  = (0xFF, 0xF0, 0xAA)     # почти белое золото (пиковый блик)

# Голубой кристалл (центральный)
CYAN_BODY   = (0x33, 0x88, 0xFF)
CYAN_MID    = (0x55, 0xAA, 0xFF)
CYAN_SHINE  = (0xAA, 0xDD, 0xFF)
CYAN_EL     = (0x88, 0xCC, 0xFF)    # левый блик грани
CYAN_ER     = (0x0A, 0x33, 0xAA)    # правый (тёмная грань)
CYAN_TIP    = (0xDD, 0xF0, 0xFF)    # кончик острия

# Фиолетовый кристалл (боковые)
PURP_BODY   = (0x77, 0x33, 0xBB)
PURP_MID    = (0x99, 0x55, 0xDD)
PURP_SHINE  = (0xBB, 0x88, 0xFF)
PURP_EL     = (0xAA, 0x66, 0xEE)
PURP_ER     = (0x33, 0x0A, 0x77)
PURP_TIP    = (0xCC, 0xAA, 0xFF)

RUNE_BLUE   = (0x66, 0xBB, 0xFF)    # свечение руны на основании


# ─── Функции рисования кристаллов ────────────────────────────────────────────

def draw_crystal_up(canvas, cw, ch, cx, tip_y, max_w, height,
                    body, mid, shine, el, er, tip, glow=None):
    """
    Рисует кристалл острием вверх с мягким свечением.

    cx     — центр X
    tip_y  — Y вершины (наименьший Y = верх)
    max_w  — ширина у основания
    height — высота кристалла
    glow   — цвет наружного свечения (если задан, рисуется вокруг кристалла)
    """
    # Свечение позади кристалла
    if glow is not None:
        for dy in range(height):
            y = tip_y + dy
            frac = dy / max(1.0, height - 1)
            base_w = max(1, round(1 + frac * (max_w - 1)))
            for gdx in range(-(base_w // 2 + 3), base_w // 2 + 4):
                gx = cx + gdx
                dist = abs(gdx) - base_w // 2
                if dist > 0:
                    ga = clamp(int(120 * (1 - dist / 4.0) * (1 - abs(dy - height * 0.5) / (height * 0.5 + 1))))
                    fp_blend(canvas, cw, gx, y, glow[0], glow[1], glow[2], ga)

    # Тело кристалла
    for dy in range(height):
        y = tip_y + dy
        if y < 0 or y >= ch:
            continue

        frac = dy / max(1.0, height - 1)
        w = max(1, round(1 + frac * (max_w - 1)))
        x0 = cx - w // 2
        x1 = x0 + w

        for x in range(x0, x1):
            if x < 0 or x >= cw:
                continue

            # Вертикальный градиент (ярче у основания)
            vt = 1.0 - frac * 0.35

            if w == 1:
                color = tip
            elif w == 2:
                color = el if x == x0 else er
            elif w == 3:
                if x == x0:        color = el
                elif x == x1 - 1:  color = er
                else:              color = mid
            else:
                if x == x0:        color = el
                elif x == x1 - 1:  color = er
                elif x == x0 + 1:  color = shine
                elif x == x1 - 2:  color = GOLD_DARK  # дополнительная тень
                else:              color = body

            # Слегка осветляем по вертикали
            cr = clamp(int(color[0] * vt + 20 * (1 - vt)))
            cg = clamp(int(color[1] * vt + 10 * (1 - vt)))
            cb = clamp(int(color[2] * vt))

            # Плавное появление у кончика
            a = 255 if dy >= 2 else (180 if dy == 1 else 120)
            fp(canvas, cw, x, y, cr, cg, cb, a)


def draw_crystal_down(canvas, cw, ch, cx, tip_y, max_w, height,
                      body, mid, shine, el, er, glow=None):
    """Кристалл острием вниз. tip_y — нижняя точка."""
    if glow is not None:
        for dy in range(height):
            y = tip_y - dy
            frac = dy / max(1.0, height - 1)
            base_w = max(1, round(1 + frac * (max_w - 1)))
            for gdx in range(-(base_w // 2 + 3), base_w // 2 + 4):
                gx = cx + gdx
                dist = abs(gdx) - base_w // 2
                if dist > 0:
                    ga = clamp(int(100 * (1 - dist / 4.0) * (1 - abs(dy - height * 0.5) / (height * 0.5 + 1))))
                    fp_blend(canvas, cw, gx, y, glow[0], glow[1], glow[2], ga)

    for dy in range(height):
        y = tip_y - dy
        if y < 0 or y >= ch:
            continue

        frac = dy / max(1.0, height - 1)
        w = max(1, round(1 + frac * (max_w - 1)))
        x0 = cx - w // 2
        x1 = x0 + w

        for x in range(x0, x1):
            if x < 0 or x >= cw:
                continue

            if w == 1:
                color = el
            elif w == 2:
                color = el if x == x0 else er
            elif w == 3:
                if x == x0:        color = el
                elif x == x1 - 1:  color = er
                else:              color = mid
            else:
                if x == x0:        color = el
                elif x == x1 - 1:  color = er
                elif x == x0 + 1:  color = shine
                else:              color = body

            a = 255 if dy >= 2 else (180 if dy == 1 else 120)
            fp(canvas, cw, x, y, color[0], color[1], color[2], a)


def draw_star(canvas, cw, ch, cx, cy, size, r, g, b, a=255):
    """Рисует 4-конечную звёздочку-искру."""
    for i in range(-size, size + 1):
        fp_blend(canvas, cw, cx + i, cy, r, g, b, max(0, a - abs(i) * 60))
        fp_blend(canvas, cw, cx, cy + i, r, g, b, max(0, a - abs(i) * 60))
    fp(canvas, cw, cx, cy, 255, 255, 255, a)


# =============================================================================
# СНАРЯДЫ «ИСКРА»
# =============================================================================

# ─── spark_core.png (16×16) ──────────────────────────────────────────────────
pixels_core = []
for y in range(16):
    for x in range(16):
        cx, cy = x - 7.5, y - 7.5
        dist = math.sqrt(cx * cx + cy * cy)
        if dist < 2.5:
            pixels_core.append((255, 240, 200, 255))
        elif dist < 5.0:
            t = (dist - 2.5) / 2.5
            r = 255
            g = int(200 - t * 120)
            b = int(50  - t * 50)
            pixels_core.append((r, max(0, g), max(0, b), 255))
        elif dist < 7.0:
            t = (dist - 5.0) / 2.0
            r = int(220 - t * 120)
            g = int(60  - t * 60)
            a_val = int(255 - t * 255)
            pixels_core.append((max(0, r), max(0, g), 0, max(0, a_val)))
        else:
            pixels_core.append((0, 0, 0, 0))

for i in range(16):
    if abs(i - 7.5) < 6.5:
        idx = 7 * 16 + i
        r2, g2, b2, a2 = pixels_core[idx]
        if a2 > 0:
            pixels_core[idx] = (min(255, r2+40), min(255, g2+30), min(255, b2+60), 255)
for i in range(16):
    if abs(i - 7.5) < 6.5:
        idx = i * 16 + 7
        r2, g2, b2, a2 = pixels_core[idx]
        if a2 > 0:
            pixels_core[idx] = (min(255, r2+40), min(255, g2+30), min(255, b2+60), 255)


# ─── spark_shard.png (16×16) ─────────────────────────────────────────────────
pixels_shard = []
for y in range(16):
    for x in range(16):
        cx, cy = x - 7.5, y - 7.5
        if abs(cx) / 6.5 + abs(cy) / 7.5 <= 1.0:
            t = abs(cx) / 6.5 + abs(cy) / 7.5
            r = int(255 - t * 30)
            g = int(180 - t * 140)
            b = 20
            a_val = int(255 - t * 80)
            pixels_shard.append((max(0, r), max(0, g), b, max(0, a_val)))
        else:
            pixels_shard.append((0, 0, 0, 0))


# =============================================================================
# HUD МАНЫ — КРИСТАЛЬНЫЙ СТОЛП
# =============================================================================
#
# Структура рендера (Java-код накладывает слоями):
#   1. mana_bar_bg.png    — тёмный фон пустого бара (20×185)
#   2. mana_bar_fill.png  — портальная заливка (20×185), обрезается снизу вверх
#   3. mana_bar_frame.png — золотая рамка + кристаллы (32×256), поверх всего
#
# Внутренняя зона в mana_bar_frame.png:
#   x = 3..22  (20px = BAR_W)
#   y = 43..227 (185px = BAR_H)
# => frameRenderX = barX - 3,  frameRenderY = barY - 43

W_FILL  = 20
H_FILL  = 185
W_FRAME = 32
H_FRAME = 256

CX = 12   # центр кластера кристаллов по X внутри frame-текстуры


# ─── mana_bar_bg.png (20×185) ────────────────────────────────────────────────
# Тёмный фон пустого бара: очень тёмный синий с лёгким виньетированием.

pixels_bg = []
for y in range(H_FILL):
    for x in range(W_FILL):
        # Расстояние от центра по X (0..1)
        dc = abs(x - (W_FILL - 1) * 0.5) / ((W_FILL - 1) * 0.5)
        vign = 1.0 - dc * 0.4  # слегка темнее у краёв
        r = int(1 * vign)
        g = int(2 * vign)
        b = int(18 * vign)
        pixels_bg.append((r, g, b, 255))


# ─── mana_bar_fill.png (20×185) ──────────────────────────────────────────────
# Светящийся «портальный» слой.
# Эффект: тёмно-синий край → голубой → белый центр.
# Бонусы: вертикальные энергетические прожилки, фиксированные звёздочки.

# Позиции фиксированных звёздочек (y, x)
SPARKLE_POS = [
    (15,  9), (32, 14), (50,  4), (65, 11), (82,  7),
    (100,16), (118, 3), (135, 9), (150,13), (167, 6),
    (25,  1), (55, 18), (90,  2), (130,17), (170,10),
]

# Позиции вертикальных молниеподобных прожилок (x_center, y_start, length)
LIGHTNING = [
    (9,  10, 40), (11,  60, 35), (10, 105, 28),
    (9, 140, 45), (10,  30, 22), (11,  80, 18),
]

pixels_fill = []
for fy in range(H_FILL):
    for fx in range(W_FILL):
        # Горизонтальная дистанция от центра (0=центр, 1=край)
        dc = abs(fx - (W_FILL - 1) * 0.5) / ((W_FILL - 1) * 0.5)

        # Квадратичное затухание яркости от центра к краям
        brightness = max(0.0, 1.0 - dc ** 1.4)

        # Небольшая вертикальная вариация
        vy = abs(fy - (H_FILL - 1) * 0.5) / ((H_FILL - 1) * 0.5)
        brightness *= (1.0 - vy * 0.15)

        # Цветовые зоны: белый → голубой → синий → тёмно-синий
        if brightness > 0.85:
            r, g, b = 220, 240, 255
        elif brightness > 0.65:
            t = (brightness - 0.65) / 0.20
            r = lerpi(65,  220, t)
            g = lerpi(135, 240, t)
            b = 255
        elif brightness > 0.38:
            t = (brightness - 0.38) / 0.27
            r = lerpi(10,  65,  t)
            g = lerpi(35,  135, t)
            b = 255
        elif brightness > 0.10:
            t = (brightness - 0.10) / 0.28
            r = lerpi(2,   10,  t)
            g = lerpi(8,   35,  t)
            b = lerpi(80,  255, t)
        else:
            r = int(brightness * 20)
            g = int(brightness * 60)
            b = int(50 + brightness * 150)

        # Вертикальные прожилки молний
        for lx, ly0, llen in LIGHTNING:
            ly1 = ly0 + llen
            if ly0 <= fy < ly1:
                ldx = abs(fx - lx)
                if ldx <= 1:
                    # синусоидальное мерцание прожилки
                    t_l = (fy - ly0) / max(1, llen - 1)
                    streak = math.sin(t_l * math.pi) * math.sin(t_l * math.pi * 3 + 0.5)
                    if streak > 0.15 and ldx == 0:
                        boost = int(streak * 45)
                        r = clamp(r + boost)
                        g = clamp(g + boost)
                        b = clamp(b + 15)

        # Фиксированные звёздочки
        for sy, sx in SPARKLE_POS:
            dist = abs(fy - sy) + abs(fx - sx)
            if dist == 0:
                r, g, b = 255, 255, 255
            elif dist == 1:
                r = clamp(r + 110)
                g = clamp(g + 110)
                b = clamp(b + 20)
            elif dist == 2:
                r = clamp(r + 45)
                g = clamp(g + 45)
                b = clamp(b + 10)

        # Альфа: плавное исчезание у крайних пикселей
        a = 255 if dc < 0.85 else int((1.0 - (dc - 0.85) / 0.15) * 240)
        pixels_fill.append((clamp(r), clamp(g), clamp(b), clamp(a)))


# ─── mana_bar_frame.png (32×256) ─────────────────────────────────────────────
# Компоновка по Y:
#   0..44    (45px) — верхний кластер кристаллов + свечение
#   45..47   (3px)  — верхняя золотая крышка
#   48..232  (185px)— боковые стенки рамки (x=0..2 gold | x=3..22 прозрачно | x=23..25 gold)
#   233..235 (3px)  — нижняя золотая крышка
#   236..255 (20px) — нижнее основание с руной и кристаллами

pf = make_canvas(W_FRAME, H_FRAME)

# ── Секция 1: верхний кластер кристаллов (y=0..44) ───────────────────────────

# Фоновое свечение позади кластера
for gy in range(0, 46):
    gfrac = 1.0 - (gy / 45.0) ** 0.7
    for gx in range(0, 26):
        dist_c = abs(gx - CX) / 13.0
        ga = int(gfrac * (1.0 - dist_c * 0.65) * 65)
        if ga > 8:
            fp_max(pf, W_FRAME, gx, gy, 0x33, 0x77, 0xFF, ga)

# Внешние маленькие фиолетовые кристаллы
draw_crystal_up(pf, W_FRAME, H_FRAME, CX - 12, 20, 4, 16,
                PURP_BODY, PURP_MID, PURP_SHINE, PURP_EL, PURP_ER, PURP_TIP,
                glow=(0x88, 0x44, 0xFF))
draw_crystal_up(pf, W_FRAME, H_FRAME, CX + 12, 19, 4, 17,
                PURP_BODY, PURP_MID, PURP_SHINE, PURP_EL, PURP_ER, PURP_TIP,
                glow=(0x88, 0x44, 0xFF))

# Средние фиолетовые кристаллы
draw_crystal_up(pf, W_FRAME, H_FRAME, CX - 6,  8,  5, 28,
                PURP_BODY, PURP_MID, PURP_SHINE, PURP_EL, PURP_ER, PURP_TIP,
                glow=(0x88, 0x44, 0xFF))
draw_crystal_up(pf, W_FRAME, H_FRAME, CX + 6,  7,  5, 29,
                PURP_BODY, PURP_MID, PURP_SHINE, PURP_EL, PURP_ER, PURP_TIP,
                glow=(0x88, 0x44, 0xFF))

# Центральный голубой кристалл (самый высокий)
draw_crystal_up(pf, W_FRAME, H_FRAME, CX,      0,  8, 38,
                CYAN_BODY, CYAN_MID, CYAN_SHINE, CYAN_EL, CYAN_ER, CYAN_TIP,
                glow=(0x44, 0xBB, 0xFF))

# Внутренний световой блик по центральной оси
draw_crystal_up(pf, W_FRAME, H_FRAME, CX - 1,  4,  2, 32,
                CYAN_SHINE, (0xFF, 0xFF, 0xFF), CYAN_SHINE, CYAN_MID,
                CYAN_MID, (0xFF, 0xFF, 0xFF), glow=None)

# Звёздочки-искры вокруг кристаллов
for sx, sy, sz, sr, sg, sb in [
    (CX - 14, 12, 2, *PURP_SHINE),
    (CX + 14, 13, 2, *PURP_SHINE),
    (CX - 7,   3, 2, *CYAN_SHINE),
    (CX + 7,   4, 2, *CYAN_SHINE),
    (CX,       0, 2, 0xFF, 0xFF, 0xFF),
]:
    draw_star(pf, W_FRAME, H_FRAME, sx, sy, sz, sr, sg, sb, 200)

# Золотое основание кластера сверху (y=36..44) — перекрывает нижние части кристаллов
for y in range(36, 45):
    for x in range(0, 26):
        if y == 36:
            color = GOLD_SHINE
        elif y <= 38:
            color = GOLD_WHITE
        elif y <= 40:
            color = GOLD_BRIGHT
        elif y <= 42:
            color = GOLD_MID
        else:
            color = GOLD_DARK
        fp_max(pf, W_FRAME, x, y, color[0], color[1], color[2])

# Угловые золотые акценты
for y in range(36, 45):
    for x in [0, 1, 24, 25]:
        fp(pf, W_FRAME, x, y, *GOLD_ACCENT)

# ── Секция 2: верхняя крышка рамки (y=45..47) ────────────────────────────────
for y in range(45, 48):
    for x in range(0, 26):
        if y == 45:
            fp(pf, W_FRAME, x, y, *GOLD_WHITE)
        elif y == 46:
            fp(pf, W_FRAME, x, y, *GOLD_SHINE)
        else:
            fp(pf, W_FRAME, x, y, *GOLD_MID)

# Пиковые угловые бликовые пиксели на крышке
for y in range(45, 48):
    fp(pf, W_FRAME, 0,  y, *GOLD_ACCENT)
    fp(pf, W_FRAME, 25, y, *GOLD_ACCENT)

# ── Секция 3: боковые стенки рамки (y=48..232) ───────────────────────────────
# 3D-рамка:
#   x=0: внешний тёмный контур
#   x=1: основное золото
#   x=2: внутренний блик (граница с прозрачным центром)
#   x=3..22: ПРОЗРАЧНО — здесь видна fill-текстура
#   x=23: внутренний блик
#   x=24: основное золото
#   x=25: внешний тёмный контур
for y in range(48, 233):
    fp(pf, W_FRAME,  0, y, *GOLD_OUTERMOST)
    fp(pf, W_FRAME,  1, y, *GOLD_OUTER)
    fp(pf, W_FRAME,  2, y, *GOLD_MID)
    fp(pf, W_FRAME,  3, y, *GOLD_SHINE)   # тонкий внутренний блик
    fp(pf, W_FRAME, 22, y, *GOLD_SHINE)   # тонкий внутренний блик
    fp(pf, W_FRAME, 23, y, *GOLD_MID)
    fp(pf, W_FRAME, 24, y, *GOLD_OUTER)
    fp(pf, W_FRAME, 25, y, *GOLD_OUTERMOST)
    # x=4..21 — полностью прозрачно (fill-текстура видна насквозь)

# Периодические засечки-украшения по рамке (каждые 30px)
for y in range(63, 233, 30):
    # Левая стенка
    fp(pf, W_FRAME, 1, y,   *GOLD_SHINE)
    fp(pf, W_FRAME, 1, y+1, *GOLD_BRIGHT)
    # Правая стенка
    fp(pf, W_FRAME, 24, y,   *GOLD_SHINE)
    fp(pf, W_FRAME, 24, y+1, *GOLD_BRIGHT)

# ── Секция 4: нижняя крышка рамки (y=233..235) ───────────────────────────────
for y in range(233, 236):
    for x in range(0, 26):
        if y == 233:
            fp(pf, W_FRAME, x, y, *GOLD_MID)
        elif y == 234:
            fp(pf, W_FRAME, x, y, *GOLD_OUTER)
        else:
            fp(pf, W_FRAME, x, y, *GOLD_DARK)

for y in range(233, 236):
    fp(pf, W_FRAME, 0,  y, *GOLD_ACCENT)
    fp(pf, W_FRAME, 25, y, *GOLD_ACCENT)

# ── Секция 5: нижнее основание (y=236..255) ──────────────────────────────────
# Многоступенчатый постамент

# Ступень A: y=236..240, ширина 26
for y in range(236, 241):
    for x in range(0, 26):
        if y == 236: color = GOLD_SHINE
        elif y == 237: color = GOLD_BRIGHT
        elif y <= 239: color = GOLD_MID
        else: color = GOLD_DARK
        fp(pf, W_FRAME, x, y, *color)
    # Боковые стойки (+2 с каждой стороны)
    for sx in [-1, -2, 26, 27]:
        if 0 <= sx < W_FRAME:
            c = GOLD_MID if y < 240 else GOLD_DARK
            fp(pf, W_FRAME, sx, y, *c)

# Ступень B: y=241..246, ширина 28
for y in range(241, 247):
    for x in range(-1, 27):
        if 0 <= x < W_FRAME:
            if y == 241: color = GOLD_SHINE
            elif y <= 244: color = GOLD_MID
            else: color = GOLD_DARK
            fp(pf, W_FRAME, x, y, *color)

# Ступень C (подошва): y=247..251, ширина 30
for y in range(247, 252):
    for x in range(-2, 28):
        if 0 <= x < W_FRAME:
            if y == 247: color = GOLD_SHINE
            elif y <= 249: color = GOLD_MID
            else: color = GOLD_DARK
            fp(pf, W_FRAME, x, y, *color)

# Руна-кольцо в центре подошвы (y≈249)
rune_cx, rune_cy = CX, 249
for i in range(64):
    angle = i / 64.0 * math.pi * 2
    for r_rad in [4, 5]:
        rx = int(round(rune_cx + r_rad * math.cos(angle)))
        ry = int(round(rune_cy + r_rad * math.sin(angle)))
        rune_a = 220 if r_rad == 5 else 100
        fp_max(pf, W_FRAME, rx, ry, *RUNE_BLUE, rune_a)

# Восемь засечек по кольцу руны
for i in range(8):
    angle = i / 8.0 * math.pi * 2
    for r_rad in [3, 6]:
        rx = int(round(rune_cx + r_rad * math.cos(angle)))
        ry = int(round(rune_cy + r_rad * math.sin(angle)))
        fp_max(pf, W_FRAME, rx, ry, *RUNE_BLUE, 190)

# Нижние кристаллы (острием вниз) — y=252..255
draw_crystal_down(pf, W_FRAME, H_FRAME, CX,       255, 5, 13,
                  CYAN_BODY,  CYAN_MID,  CYAN_SHINE, CYAN_EL,  CYAN_ER,
                  glow=(0x44, 0xBB, 0xFF))
draw_crystal_down(pf, W_FRAME, H_FRAME, CX - 7,   255, 4,  9,
                  PURP_BODY, PURP_MID,  PURP_SHINE, PURP_EL,  PURP_ER,
                  glow=(0x88, 0x44, 0xFF))
draw_crystal_down(pf, W_FRAME, H_FRAME, CX + 7,   255, 4,  9,
                  PURP_BODY, PURP_MID,  PURP_SHINE, PURP_EL,  PURP_ER,
                  glow=(0x88, 0x44, 0xFF))


# ─── Запись файлов ────────────────────────────────────────────────────────────

base = os.path.dirname(__file__)

out_entity = os.path.join(base, "src", "main", "resources", "assets", "mymod", "textures", "entity")
os.makedirs(out_entity, exist_ok=True)

for name, pixels in [("spark_core.png", pixels_core), ("spark_shard.png", pixels_shard)]:
    path = os.path.join(out_entity, name)
    with open(path, 'wb') as f:
        f.write(make_png(pixels, 16, 16))
    print(f"Записан: {path}")

out_gui = os.path.join(base, "src", "main", "resources", "assets", "mymod", "textures", "gui")
os.makedirs(out_gui, exist_ok=True)

for name, pixels, w, h in [
    ("mana_bar_bg.png",   pixels_bg,   W_FILL,  H_FILL),
    ("mana_bar_fill.png", pixels_fill, W_FILL,  H_FILL),
    ("mana_bar_frame.png", pf,         W_FRAME, H_FRAME),
]:
    path = os.path.join(out_gui, name)
    with open(path, 'wb') as f:
        f.write(make_png(pixels, w, h))
    print(f"Записан: {path}")

print("Готово.")
