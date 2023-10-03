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

CREATE OR REPLACE FUNCTION user_winrate(VARCHAR)
    RETURNS NUMERIC
    IMMUTABLE
    LANGUAGE sql
AS
$$
SELECT round(sum(iif(x.uid = $1, 1.0, 0.0)) / count(1) * 100, 2)
FROM (
         SELECT h.info -> lower(h.info ->> 'winner') ->> 'uid' AS uid
         FROM match_history h
         WHERE h.info -> 'winner' IS NOT NULL
           AND $1 IN (h.info -> 'top' ->> 'uid', h.info -> 'bottom' ->> 'uid')
     ) x
$$;
