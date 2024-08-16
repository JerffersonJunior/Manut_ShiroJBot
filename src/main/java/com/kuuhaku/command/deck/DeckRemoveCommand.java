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

package com.kuuhaku.command.deck;

import com.kuuhaku.Constants;
import com.kuuhaku.controller.DAO;
import com.kuuhaku.interfaces.Executable;
import com.kuuhaku.interfaces.annotations.Command;
import com.kuuhaku.interfaces.annotations.Requires;
import com.kuuhaku.interfaces.annotations.Signature;
import com.kuuhaku.model.enums.Category;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.persistent.shiro.Card;
import com.kuuhaku.model.persistent.shoukan.DailyDeck;
import com.kuuhaku.model.persistent.shoukan.Deck;
import com.kuuhaku.model.persistent.user.StashedCard;
import com.kuuhaku.model.records.EventData;
import com.kuuhaku.model.records.MessageData;
import com.kuuhaku.util.Utils;
import com.ygimenez.json.JSONObject;
import jakarta.persistence.NoResultException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;

import java.util.List;

@Command(
		name = "deck",
		path = "remove",
		category = Category.MISC
)
@Signature({
		"<action:word:r>[all]",
		"<card:word:r> <amount:number>",
		"<card:word:r>"
})
@Requires(Permission.MESSAGE_EMBED_LINKS)
public class DeckRemoveCommand implements Executable {
	@Override
	public void execute(JDA bot, I18N locale, EventData data, MessageData.Guild event, JSONObject args) {
		Deck d = data.profile().getAccount().getDeck();
		if (d == null) {
			event.channel().sendMessage(locale.get("error/no_deck", data.config().getPrefix())).queue();
			return;
		} else if (d instanceof DailyDeck) {
			event.channel().sendMessage(locale.get("error/daily_deck")).queue();
			return;
		}

		if (args.has("action")) {
			DAO.applyNative(StashedCard.class, "UPDATE stashed_card SET deck_id = NULL WHERE kawaipon_uid = ?1 AND deck_id = ?2",
					event.user().getId(), d.getId()
			);

			event.channel().sendMessage(locale.get("success/deck_clear")).queue();
			return;
		}

		Card card = DAO.find(Card.class, args.getString("card").toUpperCase());
		if (card == null) {
			String sug = Utils.didYouMean(args.getString("card"), "SELECT id AS value FROM v_card_names");
			if (sug == null) {
				event.channel().sendMessage(locale.get("error/unknown_card_none")).queue();
			} else {
				event.channel().sendMessage(locale.get("error/unknown_card", sug)).queue();
			}
			return;
		}

		List<StashedCard> stash = DAO.queryAll(StashedCard.class,
				"SELECT s FROM StashedCard s WHERE s.kawaipon.uid = ?1 AND s.deck.id = ?2 AND s.card.id = ?3",
				event.user().getId(), d.getId(), card.getId()
		);
		if (stash.isEmpty()) {
			event.channel().sendMessage(locale.get("error/not_in_deck")).queue();
			return;
		}

		if (args.has("amount")) {
			int qtd = args.getInt("amount");
			if (qtd < 1) {
				event.channel().sendMessage(locale.get("error/invalid_value_low", 1)).queue();
				return;
			}

			for (int i = 0, j = 0; i < stash.size() && j < qtd; i++) {
				StashedCard sc = stash.get(i);
				if (sc.getCard().equals(card)) {
					sc.setDeck(null);
					sc.save();

					j++;
				}
			}

			event.channel().sendMessage(locale.get("success/deck_remove")).queue();
			return;
		}

		Utils.selectOption(locale, event.channel(), stash, card, event.user())
				.thenAccept(sc -> {
					if (sc == null) {
						event.channel().sendMessage(locale.get("error/invalid_value")).queue();
						return;
					}

					sc.setDeck(null);
					sc.save();

					event.channel().sendMessage(locale.get("success/deck_remove")).queue();
				})
				.exceptionally(t -> {
					if (!(t.getCause() instanceof NoResultException)) {
						Constants.LOGGER.error(t, t);
					}

					event.channel().sendMessage(locale.get("error/not_owned")).queue();
					return null;
				});
	}
}
