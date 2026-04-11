package com.example.mymod.client.screen.encyclopedia;

import java.util.List;

/**
 * Одна статья энциклопедии.
 *
 * Структура тела статьи (body) задаётся списком Line-объектов.
 * Каждый Line имеет тип, который определяет визуальное оформление.
 *
 * КАК ДОБАВИТЬ СТАТЬЮ — см. EncyclopediaData.
 */
public record EncyclopediaEntry(

        /** Название статьи (отображается в списке и как заголовок) */
        String title,

        /**
         * Слова для поиска (строчные).
         * Поиск совпадает с title или любым тегом.
         */
        String[] tags,

        /** Тело статьи — список отформатированных строк */
        List<Line> body

) {

    /**
     * Строка внутри статьи.
     * Используй статические фабричные методы для удобного создания:
     *   h1("ЗАГОЛОВОК") — крупный золотой заголовок
     *   h2("Раздел")    — малый заголовок
     *   body("текст")   — обычный текст (автоматически переносится по словам)
     *   flavor("текст") — курсивный лор-текст
     *   separator()     — горизонтальный разделитель
     *   tip("совет")    — подсказка в зелёной рамке
     */
    public record Line(Type type, String text) {

        public enum Type {
            H1,         // Крупный золотой заголовок секции
            H2,         // Малый заголовок подсекции
            BODY,       // Обычный текст с переносом слов
            FLAVOR,     // Лоровый курсив (приглушённый цвет)
            SEPARATOR,  // Горизонтальная разделительная линия
            TIP         // Совет / механика (зелёная рамка)
        }

        // ── Фабричные методы для удобного написания контента ──────────────────

        public static Line h1(String text)     { return new Line(Type.H1, text); }
        public static Line h2(String text)     { return new Line(Type.H2, text); }
        public static Line body(String text)   { return new Line(Type.BODY, text); }
        public static Line flavor(String text) { return new Line(Type.FLAVOR, text); }
        public static Line separator()         { return new Line(Type.SEPARATOR, ""); }
        public static Line tip(String text)    { return new Line(Type.TIP, text); }
    }
}
