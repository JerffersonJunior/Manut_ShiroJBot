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

import com.kuuhaku.controller.DAO;
import com.kuuhaku.interfaces.Executable;
import com.kuuhaku.interfaces.annotations.Command;
import com.kuuhaku.interfaces.annotations.Requires;
import com.kuuhaku.interfaces.annotations.Signature;
import com.kuuhaku.model.enums.Category;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.persistent.shiro.Anime;
import com.kuuhaku.model.persistent.shoukan.Deck;
import com.kuuhaku.model.records.EventData;
import com.kuuhaku.model.records.MessageData;
import com.kuuhaku.util.Utils;
import com.ygimenez.json.JSONObject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;

@Command(
		name = "deck",
		path = "cover",
		category = Category.MISC
)
@Signature("<anime:word>")
@Requires(Permission.MESSAGE_EMBED_LINKS)
public class DeckCoverCommand implements Executable {
	@Override
	public void execute(JDA bot, I18N locale, EventData data, MessageData.Guild event, JSONObject args) {
		Deck d = data.profile().getAccount().getCurrentDeck();
		if (d == null) {
			event.channel().sendMessage(locale.get("error/no_deck", data.config().getPrefix())).queue();
			return;
		}

		if (!args.has("anime")) {
			d.getStyling().setCover(null);
			event.channel().sendMessage(locale.get("success/deck_cover_remove")).queue();
		} else {
			Anime anime = DAO.find(Anime.class, args.getString("anime").toUpperCase());
			if (anime == null || !anime.isVisible()) {
				String sug = Utils.didYouMean(args.getString("anime"), "SELECT id AS value FROM anime WHERE visible");
				if (sug == null) {
					event.channel().sendMessage(locale.get("error/unknown_anime_none")).queue();
				} else {
					event.channel().sendMessage(locale.get("error/unknown_anime", sug)).queue();
				}
				return;
			}

			d.getStyling().setCover(anime.getCover());
			event.channel().sendMessage(locale.get("success/deck_cover", anime)).queue();
		}

		d.save();
	}
}