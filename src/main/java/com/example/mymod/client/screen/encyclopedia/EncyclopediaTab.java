package com.example.mymod.client.screen.encyclopedia;

import java.util.List;

/**
 * Вкладка энциклопедии — тематическая группа статей.
 *
 * @param id      Уникальный строковый идентификатор (используется для будущего сохранения позиции)
 * @param icon    Один символ-иконка, отображается на кнопке вкладки
 * @param name    Короткое отображаемое название вкладки
 * @param entries Список статей этой вкладки
 */
public record EncyclopediaTab(
        String id,
        String icon,
        String name,
        List<EncyclopediaEntry> entries
) {}
