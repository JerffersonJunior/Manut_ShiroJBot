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

package com.kuuhaku.command.misc;

import com.github.ygimenez.method.Pages;
import com.github.ygimenez.model.InteractPage;
import com.github.ygimenez.model.Page;
import com.github.ygimenez.model.helper.ButtonizeHelper;
import com.kuuhaku.Constants;
import com.kuuhaku.controller.DAO;
import com.kuuhaku.exceptions.PendingConfirmationException;
import com.kuuhaku.interfaces.Executable;
import com.kuuhaku.interfaces.annotations.Command;
import com.kuuhaku.interfaces.annotations.Requires;
import com.kuuhaku.interfaces.annotations.Signature;
import com.kuuhaku.model.common.ColorlessEmbedBuilder;
import com.kuuhaku.model.common.RandomList;
import com.kuuhaku.model.enums.CardType;
import com.kuuhaku.model.enums.Category;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.enums.Rarity;
import com.kuuhaku.model.persistent.shiro.Anime;
import com.kuuhaku.model.persistent.shiro.Card;
import com.kuuhaku.model.persistent.shoukan.Evogear;
import com.kuuhaku.model.persistent.shoukan.Field;
import com.kuuhaku.model.persistent.user.*;
import com.kuuhaku.model.records.EventData;
import com.kuuhaku.model.records.MessageData;
import com.kuuhaku.model.records.SynthResult;
import com.kuuhaku.util.Calc;
import com.kuuhaku.util.IO;
import com.kuuhaku.util.Spawn;
import com.kuuhaku.util.Utils;
import com.ygimenez.json.JSONObject;
import jakarta.persistence.NoResultException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Command(
		name = "synth",
		category = Category.MISC
)
@Signature({
		"<cards:text:r>",
		"<anime:word:r>"
})
@Requires({
		Permission.MESSAGE_EMBED_LINKS,
		Permission.MESSAGE_ATTACH_FILES
})
public class SynthesizeCommand implements Executable {
	@Override
	public void execute(JDA bot, I18N locale, EventData data, MessageData.Guild event, JSONObject args) {
		Kawaipon kp = data.profile().getAccount().getKawaipon();

		String[] ids = args.getString("cards").split(" +");
		if (ids.length > 10) {
			event.channel().sendMessage(locale.get("error/too_many_items", 10)).queue();
			return;
		} else if (ids.length == 1) {
			Card c = DAO.find(Card.class, ids[0].toUpperCase());
			if (c != null && c.getRarity() == Rarity.ULTIMATE && kp.isCollectionComplete(c.getAnime())) {
				synthCollection(locale, event.channel(), kp.getAccount(), c.getAnime());
				return;
			}
		}

		List<StashedCard> cards = new ArrayList<>();
		List<StashedCard> stash = data.profile().getAccount().getKawaipon().getNotInUse();

		for (String id : ids) {
			Card c = DAO.find(Card.class, id.toUpperCase());
			if (c == null) {
				String sug = Utils.didYouMean(id.toUpperCase(), "SELECT id AS value FROM v_card_names");
				if (sug == null) {
					event.channel().sendMessage(locale.get("error/unknown_card_none")).queue();
				} else {
					event.channel().sendMessage(locale.get("error/unknown_card", sug)).queue();
				}
				return;
			}

			CompletableFuture<Boolean> success = new CompletableFuture<>();
			Utils.selectOption(locale, event.channel(), stash, c, event.user())
					.thenAccept(sc -> {
						if (sc == null) {
							event.channel().sendMessage(locale.get("error/invalid_value")).queue();
							success.complete(false);
							return;
						} else if (cards.contains(sc)) {
							event.channel().sendMessage(locale.get("error/twice_added")).queue();
							success.complete(false);
							return;
						}

						cards.add(sc);
						stash.remove(sc);
						success.complete(true);
					})
					.exceptionally(t -> {
						if (!(t.getCause() instanceof NoResultException)) {
							Constants.LOGGER.error(t, t);
						}

						event.channel().sendMessage(locale.get("error/not_owned")).queue();
						success.complete(false);
						return null;
					});

			try {
				if (!success.get()) return;
			} catch (InterruptedException | ExecutionException ignore) {
			}
		}

		if (cards.size() < 3) {
			event.channel().sendMessage(locale.get("error/invalid_synth_material")).queue();
			return;
		}

		try {
			double mult = getMult(cards);
			AtomicBoolean lucky = new AtomicBoolean();
			int field = (int) Math.round(
					cards.stream()
							.mapToDouble(sc -> {
								KawaiponCard kc = sc.getKawaiponCard();
								if (sc.getType() == CardType.FIELD || (kc != null && kc.isChrome())) {
									return 100 / 3d;
								}

								return 0;
							}).sum()
			);

			Account acc = data.profile().getAccount();
			EmbedBuilder eb = new ColorlessEmbedBuilder()
					.setDescription(locale.get("str/synthesis_info", Utils.roundToString(mult * (lucky.get() ? 1.5 : 1), 2) + "x", field));

			User usr = event.user();
			if (Utils.CONFIMATIONS.contains(usr.getId())) throw new PendingConfirmationException();

			Utils.lock(usr);

			AtomicBoolean lock = new AtomicBoolean(false);
			ButtonizeHelper helper = new ButtonizeHelper(true)
					.setTimeout(1, TimeUnit.MINUTES)
					.setCanInteract(event.user()::equals)
					.setOnFinalization(m -> Utils.unlock(usr))
					.addAction(Utils.parseEmoji("1103779997317087364"), w -> {
						Button btn = w.getButton();
						assert btn != null;

						String id = btn.getId();
						assert id != null;

						if (acc.getItemCount("CHROMATIC_ESSENCE") == 0) {
							event.channel().sendMessage(locale.get("error/no_chromatic")).queue();
							return;
						}

						lucky.set(true);
						Page p = InteractPage.of(new ColorlessEmbedBuilder()
								.setDescription(locale.get("str/synthesis_info",
										Utils.roundToString(mult, 2) + "x <:chromatic_essence:1103779997317087364>",
										field
								)).build()
						);

						Pages.modifyButtons(w.getMessage(), p, Map.of(
								btn.getId(), Button::asDisabled
						));
					})
					.addAction(Utils.parseEmoji(Constants.ACCEPT), w -> {
						if (!lock.get()) {
							Kawaipon k = kp.refresh();

							double totalQ = 1;
							Set<Rarity> rarities = EnumSet.noneOf(Rarity.class);
							for (StashedCard sc : cards) {
								if (sc.getType() == CardType.KAWAIPON) {
									KawaiponCard kc = sc.getKawaiponCard();
									if (kc != null) {
										kc.delete();
										rarities.add(kc.getCard().getRarity());
										totalQ += kc.getQuality();
									}
								} else {
									sc.delete();
								}
							}

							if (rarities.size() >= 5) {
								UserItem item = DAO.find(UserItem.class, "CHROMATIC_ESSENCE");
								if (item != null) {
									int gained = Calc.round(totalQ);
									acc.addItem(item, gained);
									event.channel().sendMessage(locale.get("str/received_item", gained, item.getName(locale))).queue();
								}
							}

							if (lucky.get()) {
								acc.consumeItem("CHROMATIC_ESSENCE");
							}

							if (Calc.chance(field)) {
								Field f = Utils.getRandomEntry(DAO.queryAll(Field.class, "SELECT f FROM Field f WHERE f.effect = FALSE"));
								new StashedCard(k, f).save();

								event.channel().sendMessage(locale.get("success/synth", f))
										.addFiles(FileUpload.fromData(IO.getBytes(f.render(locale, k.getAccount().getCurrentDeck()), "png"), "synth.png"))
										.queue();
							} else {
								Evogear e = rollSynthesis(event.user(), mult, lucky.get());
								new StashedCard(k, e).save();

								event.channel().sendMessage(locale.get("success/synth", e + " (" + StringUtils.repeat("★", e.getTier()) + ")"))
										.addFiles(FileUpload.fromData(IO.getBytes(e.render(locale, k.getAccount().getCurrentDeck()), "png"), "synth.png"))
										.queue();
							}

							lock.set(true);
							w.getMessage().delete().queue(null, Utils::doNothing);
							Utils.unlock(usr);
						}
					});

			helper.apply(event.channel().sendMessage(locale.get("question/synth")).setEmbeds(eb.build())).queue();
		} catch (PendingConfirmationException e) {
			event.channel().sendMessage(locale.get("error/pending_confirmation")).queue();
		}
	}

	private static void synthShards(I18N locale, MessageChannel channel, Account acc, UserItem shard) {
		if (acc.getItemCount(shard.getId()) < 10) {
			channel.sendMessage(locale.get("error/not_enough_shards")).queue();
			return;
		}

		try {
			Rarity r = Rarity.valueOf(shard.getId().split("_")[0]);

			Utils.confirm(locale.get("question/synth_shards", shard.getName(locale), locale.get("rarity/" + r)), channel, w -> {
						Kawaipon kp = acc.getKawaipon();
						acc.consumeItem(shard, 10);

						List<Card> pool = DAO.queryAll(Card.class, "SELECT c FROM Card c WHERE c.anime.visible = TRUE AND c.rarity = ?1", r);
						KawaiponCard kc = new KawaiponCard(Utils.getRandomEntry(pool), false);
						kc.setKawaipon(kp);
						kc.save();

						new StashedCard(kp, kc).save();
						channel.sendMessage(locale.get("success/synth", kc))
								.addFiles(FileUpload.fromData(IO.getBytes(kc.render(), "png"), "synth.png"))
								.queue();

						return true;
					}, acc.getUser()
			);
		} catch (PendingConfirmationException e) {
			channel.sendMessage(locale.get("error/pending_confirmation")).queue();
		}
	}

	private static void synthCollection(I18N locale, MessageChannel channel, Account acc, Anime anime) {
		try {
			Kawaipon kp = acc.getKawaipon();
			Set<KawaiponCard> collection = kp.getCollection(anime, false);

			Utils.confirm(locale.get("question/synth_collection", anime.toString(), collection.size()), channel, w -> {
						UserItem item = DAO.find(UserItem.class, "MASTERY_TOKEN");
						acc.addItem(item, collection.size());

						for (KawaiponCard kc : collection) {
							kc.delete();
						}

						channel.sendMessage(locale.get("str/received_item", collection.size(), item.getName(locale))).queue();

						return true;
					}, acc.getUser()
			);
		} catch (PendingConfirmationException e) {
			channel.sendMessage(locale.get("error/pending_confirmation")).queue();
		}
	}

	public static Evogear rollSynthesis(User u, List<StashedCard> cards) {
		return rollSynthesis(u, getMult(cards), false);
	}

	public static Evogear rollSynthesis(User u, double mult, boolean lucky) {
		RandomList<SynthResult> pool = new RandomList<>(mult * (lucky ? 1.5 : 1));
		List<SynthResult> evos = DAO.queryAllUnmapped("SELECT card_id, get_weight(card_id, ?1) FROM evogear WHERE tier > 0", u.getId()).stream()
				.map(o -> Utils.map(SynthResult.class, o))
				.toList();

		for (SynthResult evo : evos) {
			pool.add(evo, evo.weight());
		}

		if (lucky) {
			Kawaipon kp = DAO.find(Kawaipon.class, u.getId());
			String fav = kp.getFavCardId();
			return Utils.luckyRoll(pool::get, (a, b) -> b.id().equals(fav) || b.weight() < a.weight()).evogear();
		} else {
			return pool.get().evogear();
		}
	}

	private static double getMult(Collection<StashedCard> cards) {
		double inc = 1;
		double more = 1 * (1 + (Spawn.getRarityMult() - 1) / 2);
		double fac = 150 + (cards.size() - 3) * 10;

		for (StashedCard sc : cards) {
			switch (sc.getType()) {
				case KAWAIPON -> {
					KawaiponCard kc = sc.getKawaiponCard();
					int rarity = sc.getCard().getRarity().getIndex();

					if (kc != null) {
						if (kc.isChrome()) {
							more *= 1 + rarity * (1 + kc.getQuality()) / fac;
						} else {
							inc += rarity * (1 + kc.getQuality()) / fac;
						}
					}
				}
				case EVOGEAR -> {
					Evogear ev = sc.getCard().asEvogear();
					inc += ev.getTier() / 6d * (150d / fac);
				}
				case FIELD -> more *= 1.25 * (150d / fac);
			}
		}

		return 1 * inc * more;
	}
}
