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

package com.kuuhaku.command.stash;

import com.github.ygimenez.model.Page;
import com.kuuhaku.controller.DAO;
import com.kuuhaku.interfaces.Executable;
import com.kuuhaku.interfaces.annotations.Command;
import com.kuuhaku.interfaces.annotations.Requires;
import com.kuuhaku.interfaces.annotations.Signature;
import com.kuuhaku.model.common.ColorlessEmbedBuilder;
import com.kuuhaku.model.enums.CardType;
import com.kuuhaku.model.enums.Category;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.persistent.user.Kawaipon;
import com.kuuhaku.model.persistent.user.KawaiponCard;
import com.kuuhaku.model.persistent.user.StashedCard;
import com.kuuhaku.model.persistent.user.Trade;
import com.kuuhaku.model.records.EventData;
import com.kuuhaku.model.records.MessageData;
import com.kuuhaku.utils.Utils;
import com.kuuhaku.utils.XStringBuilder;
import com.kuuhaku.utils.json.JSONObject;
import kotlin.Pair;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;

@Command(
		name = "stash",
		category = Category.MISC
)
@Signature({
		"<action:word:r>[help]",
		"<params:text>"
})
@Requires(Permission.MESSAGE_EMBED_LINKS)
public class StashCommand implements Executable {
	@Override
	public void execute(JDA bot, I18N locale, EventData data, MessageData.Guild event, JSONObject args) {
		Kawaipon kp = DAO.find(Kawaipon.class, event.user().getId());
		if (kp.getCards().isEmpty()) {
			event.channel().sendMessage(locale.get("error/empty_stash")).queue();
			return;
		}

		List<String> names = DAO.queryAllNative(String.class, "SELECT id FROM card");

		Pair<String, Double> sug = Utils.didYouMean(args.getString("card").toUpperCase(Locale.ROOT), names);
		if (sug.getSecond() > 75) {
			event.channel().sendMessage(locale.get("error/unknown_card", sug.getFirst())).queue();
			return;
		}

		String[] content = event.message().getContentRaw().split("\\s+");
		content = ArrayUtils.remove(content, 0);
		Pair<CommandLine, Options> cli = Utils.getCardCLI(locale, content, false);
		if (args.containsKey("action")) {
			XStringBuilder sb = new XStringBuilder();
			for (Option opt : cli.getSecond().getOptions()) {
				sb.appendNewLine("**-%s --%s** - %s".formatted(
						opt.getOpt(),
						opt.getLongOpt(),
						opt.getDescription()
				));
			}

			EmbedBuilder eb = new ColorlessEmbedBuilder()
					.setAuthor(locale.get("str/search_params"))
					.setDescription(sb.toString());

			event.channel().sendMessageEmbeds(eb.build()).queue();
			return;
		}

		Map<String, String> filters = new LinkedHashMap<>() {{
			put("n", "AND c.card.id LIKE '%'||?2||'%'");
			put("r", "AND CAST(c.card.rarity AS STRING) LIKE '%'||?3||'%'");
			put("a", "AND c.card.anime.id LIKE '%'||?4||'%'");
			put("c", "AND c.foil = TRUE");
			put("k", "AND c.type = '%s'".formatted(CardType.KAWAIPON));
			put("e", "AND c.type = '%s'".formatted(CardType.EVOGEAR));
			put("f", "AND c.type = '%s'".formatted(CardType.FIELD));
		}};

		List<Object> params = new ArrayList<>() {{
			add(event.user().getId());
		}};
		XStringBuilder query = new XStringBuilder("SELECT c FROM StashedCard c WHERE c.kawaipon.uid = ?1");
		for (Map.Entry<String, String> entry : filters.entrySet()) {
			String opt = cli.getFirst().getOptionValue(entry.getKey());

			if (opt != null) {
				query.appendNewLine(entry.getValue());
				params.add(opt.toUpperCase(Locale.ROOT));
			}
		}

		query.appendNewLine("ORDER BY c.card.rarity, c.card.id");

		int total = DAO.queryNative(Integer.class, "SELECT COUNT(1) FROM stashed_card c WHERE c.kawaipon_uid = ?1", event.user().getId());
		List<StashedCard> results = DAO.queryAll(StashedCard.class, query.toString(), params.toArray());
		EmbedBuilder eb = new ColorlessEmbedBuilder()
				.setAuthor(locale.get("str/search_result", results.size(), total));

		List<Page> pages = Utils.generatePages(eb, results, 10, sc -> {
			switch (sc.getType()) {
				case KAWAIPON -> {
					Trade t = Trade.getPending().get(event.user().getId());
					KawaiponCard kc = sc.getKawaiponCard();

					String location = null;
					if (sc.getDeck() != null) {
						location = " (" + locale.get("str/deck", sc.getDeck().getId()) + ")";
					}

					return new MessageEmbed.Field(
							sc.toString() + location,
							"%s%s (%s | %s)%s".formatted(
									sc.getCard().getRarity().getEmote(),
									locale.get("type/" + sc.getType()),
									locale.get("rarity/" + sc.getCard().getRarity()),
									sc.getCard().getAnime(),
									kc != null ? ("\n" + locale.get("str/quality", kc.getQuality())) : ""
							),
							false
					);
				}
				case EVOGEAR -> {
					//TODO
				}
				case FIELD -> {
					//TODO
				}
			}

			return null;
		});

		if (pages.isEmpty()) {
			event.channel().sendMessage(locale.get("error/no_result")).queue();
			return;
		}

		Utils.paginate(pages, 5, false, event.channel(), event.user());
	}
}
