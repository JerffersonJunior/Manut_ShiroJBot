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

CREATE OR REPLACE FUNCTION t_remove_stash_references()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    DELETE
    FROM stashed_card sc
    WHERE sc.id = OLD.stash_entry;

    RETURN OLD;
END;
$$;

DROP TRIGGER IF EXISTS remove_stash_references ON kawaipon_card;
CREATE TRIGGER remove_stash_references
    AFTER DELETE
    ON kawaipon_card
    FOR EACH ROW
EXECUTE PROCEDURE t_remove_stash_references();
