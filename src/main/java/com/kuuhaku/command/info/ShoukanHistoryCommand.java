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

package com.kuuhaku.command.info;

import com.github.ygimenez.model.Page;
import com.kuuhaku.Constants;
import com.kuuhaku.controller.DAO;
import com.kuuhaku.interfaces.Executable;
import com.kuuhaku.interfaces.annotations.Command;
import com.kuuhaku.interfaces.annotations.Requires;
import com.kuuhaku.model.common.ColorlessEmbedBuilder;
import com.kuuhaku.model.enums.Category;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.persistent.user.Account;
import com.kuuhaku.model.records.EventData;
import com.kuuhaku.model.records.FieldMimic;
import com.kuuhaku.model.records.MessageData;
import com.kuuhaku.model.records.shoukan.history.Match;
import com.kuuhaku.model.records.shoukan.history.Player;
import com.kuuhaku.util.Utils;
import com.ygimenez.json.JSONObject;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(
		name = "shoukan",
		path = "history",
		category = Category.INFO
)
@Requires(Permission.MESSAGE_EMBED_LINKS)
public class ShoukanHistoryCommand implements Executable {
	@Override
	public void execute(JDA bot, I18N locale, EventData data, MessageData.Guild event, JSONObject args) {
		Account acc = data.profile().getAccount();

		List<Match> matches = acc.getMatches();
		if (matches.isEmpty()) {
			event.channel().sendMessage(locale.get("error/no_matches")).queue();
			return;
		}

		EmbedBuilder eb = new ColorlessEmbedBuilder()
				.setAuthor(locale.get("str/history_title", acc.getName()))
				.setDescription(locale.get("str/history_body",
						acc.getShoukanRanking(),
						Utils.roundToString(acc.getWinrate(), 2) + "%",
						matches.size()
				));

		List<Page> pages = Utils.generatePages(eb, matches, 10, 5,
				m -> {
					String out = Stream.of(m.info().top(), m.info().bottom())
							.map(p -> {
								Account a = DAO.find(Account.class, p.uid());
								String icon;
								if (p.origin().isPure()) {
									icon = Utils.getEmoteString(p.origin().major().name());
								} else {
									icon = Utils.getEmoteString(p.origin().synergy().name());
								}

								if (icon.isBlank()) return a.getName();
								else return icon + " " + a.getName();
							})
							.collect(Collectors.joining(" _VS_ "));

					FieldMimic fm = new FieldMimic(out, "");
					Player winner = m.info().winnerPlayer();
					if (winner == null) {
						fm.appendLine(locale.get("str/draw"));
					} else if (winner.uid().equals(acc.getUid())) {
						fm.appendLine(locale.get("str/win"));
					} else {
						fm.appendLine(locale.get("str/lose"));
					}

					fm.append(" | " + locale.get("str/turns_inline", m.turns().size()));
					fm.appendLine(Constants.TIMESTAMP_R.formatted(m.info().timestamp()));
					return fm.toString();
				},
				(p, t) -> eb.setFooter(locale.get("str/page", p + 1, t))
		);

		Utils.paginate(pages, 1, true, event.channel(), event.user());
	}
}
