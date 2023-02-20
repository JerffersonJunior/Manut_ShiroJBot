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

package com.kuuhaku.command.deck;

import com.github.ygimenez.method.Pages;
import com.github.ygimenez.model.InteractPage;
import com.github.ygimenez.model.Page;
import com.kuuhaku.interfaces.Executable;
import com.kuuhaku.interfaces.annotations.Command;
import com.kuuhaku.interfaces.annotations.Requires;
import com.kuuhaku.model.common.ColorlessEmbedBuilder;
import com.kuuhaku.model.enums.Category;
import com.kuuhaku.model.enums.Currency;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.enums.shoukan.SlotSkin;
import com.kuuhaku.model.persistent.shoukan.Deck;
import com.kuuhaku.model.persistent.user.Account;
import com.kuuhaku.model.persistent.user.AccountTitle;
import com.kuuhaku.model.persistent.user.Title;
import com.kuuhaku.model.records.EventData;
import com.kuuhaku.model.records.MessageData;
import com.kuuhaku.util.Utils;
import com.kuuhaku.util.json.JSONObject;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Command(
		name = "deck",
		subname = "skin",
		category = Category.MISC
)
@Requires(Permission.MESSAGE_EMBED_LINKS)
public class DeckSkinCommand implements Executable {
	private static final String URL = "https://raw.githubusercontent.com/OtagamerZ/ShiroJBot/rewrite/src/main/resources/shoukan/side/%s_bottom.webp";

	@Override
	public void execute(JDA bot, I18N locale, EventData data, MessageData.Guild event, JSONObject args) {
		Account acc = data.profile().getAccount();
		Deck d = data.profile().getAccount().getCurrentDeck();
		if (d == null) {
			event.channel().sendMessage(locale.get("error/no_deck", data.config().getPrefix())).queue();
			return;
		}

		if (args.isEmpty()) {
			EmbedBuilder eb = new ColorlessEmbedBuilder()
					.setAuthor(locale.get("str/all_skins"));

			SlotSkin[] skins = SlotSkin.values();
			List<Page> pages = new ArrayList<>();
			for (int i = 0; i < skins.length; i++) {
				SlotSkin ss = skins[i];
				if (!ss.canUse(acc)) {
					boolean notFound = false;
					List<Title> titles = ss.getTitles();
					for (Title title : titles) {
						if (!acc.hasTitle(title.getId())) {
							notFound = true;
							break;
						}
					}

					if (notFound) {
						String req = Utils.properlyJoin(locale.get("str/and")).apply(
								titles.stream()
										.map(t -> "**`" + t.getInfo(locale).getName() + "`**")
										.toList()
						);

						eb.setThumbnail("https://i.imgur.com/PXNqRvA.png")
								.setImage(null)
								.setTitle(locale.get("str/skin_locked"))
								.setDescription(locale.get("str/requires_titles", req));
					} else {
						Title paid = ss.getPaidTitle();
						assert paid != null;

						eb.setThumbnail("https://i.imgur.com/PXNqRvA.png")
								.setImage(URL.formatted(ss.name().toLowerCase()))
								.setTitle(locale.get("str/skin_locked"))
								.setDescription(locale.get("str/requires_purchase", locale.get("currency/" + paid.getCurrency(), paid.getPrice())));
					}
				} else {
					eb.setImage(URL.formatted(ss.name().toLowerCase()))
							.setTitle(ss.getName(locale))
							.setDescription(ss.getDescription(locale));
				}
				eb.setFooter(locale.get("str/page", i + 1, skins.length));

				pages.add(new InteractPage(eb.build()));
			}

			AtomicBoolean confirm = new AtomicBoolean();
			AtomicInteger i = new AtomicInteger();
			event.channel().sendMessageEmbeds((MessageEmbed) pages.get(0).getContent()).queue(s ->
					Pages.buttonize(s, Utils.with(new LinkedHashMap<>(), m -> {
								m.put(Utils.parseEmoji("◀️"), w -> {
									if (i.get() > 1) {
										confirm.set(false);
										s.editMessageEmbeds((MessageEmbed) pages.get(i.decrementAndGet()).getContent()).queue();
									}
								});
								m.put(Utils.parseEmoji("▶️"), w -> {
									if (i.get() < skins.length - 1) {
										confirm.set(false);
										s.editMessageEmbeds((MessageEmbed) pages.get(i.incrementAndGet()).getContent()).queue();
									}
								});
								m.put(Utils.parseEmoji("✅"), w -> {
									SlotSkin skin = skins[i.get()];
									if (!skin.canUse(acc)) {
										Title paid = skin.getPaidTitle();
										if (paid != null) {
											if (!acc.hasEnough(paid.getPrice(), paid.getCurrency())) {
												event.channel().sendMessage(locale.get("error/insufficient_" + paid.getCurrency())).queue();
												return;
											} else if (!confirm.getAndSet(true)) {
												w.getHook().setEphemeral(true)
														.sendMessage(locale.get("str/press_again"))
														.queue();
												return;
											}

											if (paid.getCurrency() == Currency.CR) {
												acc.consumeCR(paid.getPrice(), "Skin " + skin);
											} else {
												acc.consumeGems(paid.getPrice(), "Skin " + skin);
											}

											new AccountTitle(acc, paid).save();
										} else {
											event.channel().sendMessage(locale.get("error/skin_locked")).queue();
											return;
										}
									}

									d.getStyling().setSkin(skin);
									d.save();
									event.channel().sendMessage(locale.get("success/skin_selected", d.getName()))
											.flatMap(ms -> s.delete())
											.queue();
								});
							}),
							true, true, 1, TimeUnit.MINUTES, event.user()::equals
					)
			);
		}
	}
}