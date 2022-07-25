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

CREATE OR REPLACE FUNCTION t_check_gaps()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF ((SELECT x.sum - (SELECT sum(generate_series) FROM generate_series(0, x.max)) FROM (SELECT sum(index) , max(index) FROM deck_senshi WHERE deck_id = 5) x) <> 0) THEN
        CALL fix_deck_gaps((SELECT account_uid FROM deck WHERE id = OLD.deck_id));
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS check_gaps ON deck_senshi;
CREATE TRIGGER check_gaps
    AFTER DELETE
    ON deck_senshi
EXECUTE PROCEDURE t_check_gaps();

DROP TRIGGER IF EXISTS check_gaps ON deck_evogear;
CREATE TRIGGER check_gaps
    AFTER DELETE
    ON deck_evogear
EXECUTE PROCEDURE t_check_gaps();

DROP TRIGGER IF EXISTS check_gaps ON deck_field;
CREATE TRIGGER check_gaps
    AFTER DELETE
    ON deck_field
EXECUTE PROCEDURE t_check_gaps();
