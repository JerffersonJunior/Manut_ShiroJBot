/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2019-2022  Yago Gimenez (KuuHaKu)
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

CREATE TABLE IF NOT EXISTS aux.collection_counter
(
    uid      VARCHAR NOT NULL,
    anime_id VARCHAR NOT NULL,
    normal   INT     NOT NULL,
    chrome   INT     NOT NULL,
    PRIMARY KEY (uid, anime_id)
);

CREATE OR REPLACE VIEW aux.v_collection_counter AS
SELECT kc.kawaipon_uid
     , c.anime_id
     , COUNT(1) FILTER (WHERE NOT kc.chrome) AS normal
     , COUNT(1) FILTER (WHERE kc.chrome)     AS chrome
FROM kawaipon_card kc
         INNER JOIN card c ON c.id = kc.card_id
WHERE kc.stash_entry IS NULL
GROUP BY kc.kawaipon_uid, c.anime_id;

INSERT INTO aux.collection_counter (uid, anime_id, normal, chrome)
SELECT kawaipon_uid
     , anime_id
     , normal
     , chrome
FROM aux.v_collection_counter
ON CONFLICT DO NOTHING;
