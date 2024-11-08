/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2019-2023  Yago Gimenez (KuuHaKu)
 *
 * Shiro J Bot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Shiro J Bot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Shiro J Bot.  If not, see <https://www.gnu.org/licenses/>
 */

-- DROP VIEW IF EXISTS v_basetype_i18n;
CREATE OR REPLACE VIEW v_basetype_i18n AS
SELECT coalesce(pt.id, en.id) AS id
     , pt.name AS name_pt
     , en.name AS name_en
FROM basetype_info pt
         FULL JOIN basetype_info en ON en.id = pt.id AND en.locale = 'EN'
WHERE pt.locale = 'PT'
ORDER BY id
