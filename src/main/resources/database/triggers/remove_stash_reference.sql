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

CREATE OR REPLACE FUNCTION t_remove_stash_reference()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    UPDATE kawaipon_card kc
    SET stash_entry = NULL
    WHERE kc.stash_entry = OLD.id;

    SELECT de.deck_id
         , de.senshi_card_id
         , de.index
    FROM deck_senshi de
             INNER JOIN deck d ON de.deck_id = d.id
             LEFT JOIN stashed_card sc ON de.deck_id = sc.deck_id AND de.senshi_card_id = sc.card_id
    WHERE d.account_uid = OLD.kawaipon_uid
      AND sc.id IS NULL;

    SELECT de.deck_id
         , de.evogear_card_id
         , de.index
    FROM deck_evogear de
             INNER JOIN deck d ON de.deck_id = d.id
             LEFT JOIN stashed_card sc ON de.deck_id = sc.deck_id AND de.evogear_card_id = sc.card_id
    WHERE d.account_uid = OLD.kawaipon_uid
      AND sc.id IS NULL;

    SELECT de.deck_id
         , de.field_card_id
         , de.index
    FROM deck_field de
             INNER JOIN deck d ON de.deck_id = d.id
             LEFT JOIN stashed_card sc ON de.deck_id = sc.deck_id AND de.field_card_id = sc.card_id
    WHERE d.account_uid = OLD.kawaipon_uid
      AND sc.id IS NULL;

    RETURN OLD;
END;
$$;

DROP TRIGGER IF EXISTS remove_stash_reference ON stashed_card;
CREATE TRIGGER remove_stash_reference
    BEFORE DELETE
    ON stashed_card
    FOR EACH ROW
EXECUTE PROCEDURE t_remove_stash_reference();
