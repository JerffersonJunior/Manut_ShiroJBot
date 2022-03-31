/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2019-2021  Yago Gimenez (KuuHaKu)
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

package com.kuuhaku.command.commands.discord.misc;

import com.github.ygimenez.method.Pages;
import com.github.ygimenez.model.ButtonWrapper;
import com.github.ygimenez.model.ThrowingConsumer;
import com.kuuhaku.Main;
import com.kuuhaku.command.Category;
import com.kuuhaku.command.Executable;
import com.kuuhaku.controller.postgresql.KawaiponDAO;
import com.kuuhaku.handlers.games.tabletop.games.shoukan.Champion;
import com.kuuhaku.handlers.games.tabletop.games.shoukan.Evogear;
import com.kuuhaku.handlers.games.tabletop.games.shoukan.Field;
import com.kuuhaku.model.annotations.Command;
import com.kuuhaku.model.annotations.Requires;
import com.kuuhaku.model.common.ColorlessEmbedBuilder;
import com.kuuhaku.model.enums.CardType;
import com.kuuhaku.model.enums.KawaiponRarity;
import com.kuuhaku.model.persistent.*;
import com.kuuhaku.utils.Constants;
import com.kuuhaku.utils.helpers.CollectionHelper;
import com.kuuhaku.utils.helpers.LogicHelper;
import com.kuuhaku.utils.helpers.MathHelper;
import com.kuuhaku.utils.helpers.StringHelper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.*;
import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.math3.util.Pair;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(
		name = "sintetizar",
		aliases = {"synthesize", "synth"},
		usage = "req_cards-type",
		category = Category.MISC
)
@Requires({
		Permission.MESSAGE_EMBED_LINKS,
		Permission.MESSAGE_ADD_REACTION,
		Permission.MESSAGE_EXT_EMOJI
})
public class SynthesizeCardCommand implements Executable {

	@Override
	public void execute(User author, Member member, String argsAsText, String[] args, Message message, TextChannel channel, Guild guild, String prefix) {
		if (args.length < 2) {
			channel.sendMessage("❌ | Você precisa informar 3 cartas para sintetizar (nomes separados por `;`) e o tipo da síntese (`n` = síntese normal, `c` = síntese cromada e `r` = resintetizar).").queue();
			return;
		} else if (!LogicHelper.equalsAny(args[1], "n", "c", "r")) {
			channel.sendMessage("❌ | Você precisa informar o tipo da síntese (`n` = síntese normal, `c` = síntese cromada e `r` = resintetizar).").queue();
			return;
		}

		String[] names = args[0].split(";");
		if (names.length > 3) {
			channel.sendMessage("❌ | Você não pode usar mais que 3 cartas na síntese.").queue();
			return;
		}

		CardType type = switch (args[1].toLowerCase(Locale.ROOT)) {
			case "c" -> CardType.FIELD;
			case "r" -> CardType.EVOGEAR;
			default -> CardType.KAWAIPON;
		};

		Kawaipon kp = Kawaipon.find(Kawaipon.class, author.getId());
		Deck dk = kp.getDeck();

		if (names.length < 3) {
			channel.sendMessage(switch (type) {
				case FIELD -> "❌ | Você precisa informar 3 cartas para sintetizar um campo.";
				case EVOGEAR -> "❌ | Você precisa informar 3 cartas para resintetizar um evogear.";
				default -> "❌ | Você precisa informar 3 cartas para sintetizar um evogear.";
			}).queue();
			return;
		}

		List<Card> tributes = new ArrayList<>();
		for (String name : names) {
			name = name.trim();
			Card c = Card.find(Card.class, name.toUpperCase(Locale.ROOT));

			if (c == null) {
				channel.sendMessage("❌ | A carta `" + name.toUpperCase(Locale.ROOT) + "` não existe, você não quis dizer `" + StringHelper.didYouMean(name, Stream.of(Champion.getChampions(), Evogear.getEvogears(), Field.getFields()).flatMap(Collection::stream).map(d -> d.getCard().getId()).toList()) + "`?").queue();
				return;
			} else if (switch (type) {
				case FIELD -> !kp.getCards().contains(new KawaiponCard(c, true));
				case EVOGEAR -> dk.getEquipment(c) == null;
				default -> !kp.getCards().contains(new KawaiponCard(c, false));
			}) {
				channel.sendMessage("❌ | Você só pode usar na síntese cartas que você possua.").queue();
				return;
			} else if (LogicHelper.equalsAny(c.getRarity(), KawaiponRarity.FIELD, KawaiponRarity.FUSION, KawaiponRarity.ULTIMATE)) {
				channel.sendMessage("❌ | Carta inválida para síntese.").queue();
				return;
			}

			tributes.add(c);
		}

		int score = switch (type) {
			case FIELD -> tributes.stream().mapToInt(c -> c.getRarity().getIndex()).sum() * 2;
			case EVOGEAR -> tributes.stream().mapToInt(c -> dk.getEquipment(c).getTier()).sum();
			default -> tributes.stream().mapToInt(c -> c.getRarity().getIndex()).sum();
		};

		DynamicParameter dp = DynamicParameter.find(DynamicParameter.class, "freeSynth_" + author.getId());
		int freeRolls = NumberUtils.toInt(dp.getValue());

		DynamicParameter bless = DynamicParameter.find(DynamicParameter.class, "blessing_" + author.getId());
		boolean blessed = !bless.getValue().isBlank();

		Main.getInfo().getConfirmationPending().put(author.getId(), true);
		switch (type) {
			case FIELD -> {
				List<Field> pool = Field.getFields(false);
				if (blessed) {
					pool = pool.subList(0, Math.min(10, pool.size()));
				}
				if (pool.isEmpty()) {
					channel.sendMessage("❌ | Não há nenhuma síntese válida usando os materiais informados.").queue();
					return;
				}

				Field f = CollectionHelper.getRandomEntry(pool);

				channel.sendMessage("Você está prester a sintetizar um campo usando essas cartas **CROMADAS** (" + (freeRolls > 0 ? "possui " + freeRolls + " sínteses gratúitas" : "elas serão destruídas no processo") + "). Deseja continuar?")
						.queue(s -> {
									Map<Emoji, ThrowingConsumer<ButtonWrapper>> buttons = new HashMap<>();
									buttons.put(StringHelper.parseEmoji(Constants.ACCEPT), wrapper -> {
										Main.getInfo().getConfirmationPending().remove(author.getId());
										s.delete().queue();

										if (freeRolls == 0) {
											for (Card t : tributes) {
												kp.removeCard(new KawaiponCard(t, true));
											}
										} else {
											if (freeRolls > 1) {
												dp.setValue(freeRolls - 1);
												dp.save();
											} else {
												dp.delete();
											}
										}

										if (dk.checkFieldError(f) > 0) {
											int change = (int) Math.round((350 + (score * 1400 / 15f)) * 2.5);

											Account acc = Account.find(Account.class, author.getId());
											acc.addCredit(change, this.getClass());
											acc.save();

											channel.sendMessage("❌ | Você já possui 3 campos, as cartas usadas cartas foram convertidas em " + StringHelper.separate(change) + " CR.").queue();
										} else {
											dk.addField(f);
											channel.sendMessage("✅ | Síntese realizada com sucesso, você obteve o campo **" + f.getCard().getName() + "**!").queue();
										}

										if (blessed) bless.delete();

										kp.save();
									});

									Pages.buttonize(s, buttons, Constants.USE_BUTTONS, true, 1, TimeUnit.MINUTES,
											u -> u.getId().equals(author.getId()),
											ms -> Main.getInfo().getConfirmationPending().remove(author.getId())
									);
								}
						);
			}
			case EVOGEAR -> {
				List<Evogear> pool = Evogear.getEvogears(false);
				if (blessed) {
					pool = pool.subList(0, Math.min(10, pool.size()));
				}
				if (pool.isEmpty()) {
					channel.sendMessage("❌ | Não há nenhuma síntese válida usando os materiais informados.").queue();
					return;
				}

				Bag<Integer> bag = tributes.stream()
						.map(c -> dk.getEquipment(c).getTier())
						.collect(Collectors.toCollection(HashBag::new));
				List<Evogear> chosenTier = MathHelper.getRandom(pool.stream()
						.collect(Collectors.groupingBy(Evogear::getTier))
						.entrySet()
						.stream()
						.map(e -> Pair.create(e.getValue(), bag.getCount(e.getKey()) / 3d))
						.toList()
				);

				Evogear e = CollectionHelper.getRandomEntry(chosenTier);

				EmbedBuilder eb = new ColorlessEmbedBuilder()
						.setTitle("Possíveis resultados")
						.addField(KawaiponRarity.COMMON.getEmote() + " | Evogear tier 1 (\uD83D\uDFCA)", "Chance de " + (MathHelper.round(bag.getCount(1) * 100 / 3d, 1)) + "%", false)
						.addField(KawaiponRarity.RARE.getEmote() + " | Evogear tier 2 (\uD83D\uDFCA\uD83D\uDFCA)", "Chance de " + (MathHelper.round(bag.getCount(2) * 100 / 3d, 1)) + "%", false)
						.addField(KawaiponRarity.ULTRA_RARE.getEmote() + " | Evogear tier 3 (\uD83D\uDFCA\uD83D\uDFCA\uD83D\uDFCA)", "Chance de " + (MathHelper.round(bag.getCount(3) * 100 / 3d, 1)) + "%", false)
						.addField(KawaiponRarity.LEGENDARY.getEmote() + " | Evogear tier 4 (\uD83D\uDFCA\uD83D\uDFCA\uD83D\uDFCA\uD83D\uDFCA)", "Chance de " + (MathHelper.round(bag.getCount(4) * 100 / 3d, 1)) + "%", false);

				Main.getInfo().getConfirmationPending().put(author.getId(), true);
				channel.sendMessage("Você está prester a resintetizar um evogear usando essas cartas (" + (freeRolls > 0 ? "possui " + freeRolls + " sínteses gratúitas" : "elas serão destruídas no processo") + "). Deseja continuar?")
						.setEmbeds(eb.build())
						.queue(s -> {
									Map<Emoji, ThrowingConsumer<ButtonWrapper>> buttons = new HashMap<>();
									buttons.put(StringHelper.parseEmoji(Constants.ACCEPT), wrapper -> {
										Main.getInfo().getConfirmationPending().remove(author.getId());
										s.delete().queue();

										String tier = StringUtils.repeat("\uD83D\uDFCA", e.getTier());

										if (freeRolls == 0) {
											for (Card t : tributes) {
												dk.removeEquipment(dk.getEquipment(t));
											}
										} else {
											if (freeRolls > 1) {
												dp.setValue(freeRolls - 1);
												dp.save();
											} else {
												dp.delete();
											}
										}

										if (dk.checkEquipmentError(e) != 0) {
											int change = (int) Math.round((350 + (score * 1400 / 15f)) * (e.getTier() == 4 ? 3.5 : 2.5));

											Account acc = Account.find(Account.class, author.getId());
											acc.addCredit(change, this.getClass());
											acc.save();

											channel.sendMessage(
													switch (dk.checkEquipmentError(e)) {
														case 1 -> "❌ | Você já possui 3 cópias de **" + e.getCard().getName() + "**! (" + tier + "), as cartas usadas foram convertidas em " + StringHelper.separate(change) + " CR.";
														case 2 -> "❌ | Você já possui 1 evogear tier 4, **" + e.getCard().getName() + "**! (" + tier + "), as cartas usadas foram convertidas em " + StringHelper.separate(change) + " CR.";
														case 3 -> "❌ | Você não possui mais espaços para evogears, as cartas usadas cartas foram convertidas em " + StringHelper.separate(change) + " CR.";
														default -> throw new IllegalStateException("Unexpected value: " + dk.checkEquipmentError(e));
													}
											).queue();
										} else {
											dk.addEquipment(e);
											channel.sendMessage("✅ | Síntese realizada com sucesso, você obteve o evogear **" + e.getCard().getName() + "**! (" + tier + ")").queue();
										}

										if (blessed) bless.delete();

										kp.save();
									});

									Pages.buttonize(s, buttons, Constants.USE_BUTTONS, true, 1, TimeUnit.MINUTES,
											u -> u.getId().equals(author.getId()),
											ms -> Main.getInfo().getConfirmationPending().remove(author.getId())
									);
								}
						);
			}
			default -> {
				int max = 15;
				double base = (max - score) / 0.75 / (max - 3);

				double t3 = Math.max(0, 0.65 - base);
				double t4 = Math.max(0, (t3 * 15) / 65 - 0.05);
				double t1 = Math.max(0, base - t4 * 10);
				double t2 = Math.max(0, 0.85 - Math.abs(0.105 - t1 / 3) * 5 - t3);
				double[] tiers = MathHelper.sumToOne(t1, t2, t3, t4);

				List<Evogear> pool = Evogear.getEvogears(false);
				if (blessed) {
					pool = pool.subList(0, Math.min(10, pool.size()));
				}

				List<Evogear> chosenTier = MathHelper.getRandom(pool.stream()
						.collect(Collectors.groupingBy(Evogear::getTier))
						.entrySet()
						.stream()
						.map(e -> Pair.create(e.getValue(), switch (e.getKey()) {
									case 1, 2, 3, 4 -> tiers[e.getKey() - 1];
									default -> 0d;
								})
						).toList()
				);

				Evogear e = CollectionHelper.getRandomEntry(chosenTier);

				EmbedBuilder eb = new ColorlessEmbedBuilder()
						.setTitle("Possíveis resultados")
						.addField(KawaiponRarity.COMMON.getEmote() + " | Evogear tier 1 (\uD83D\uDFCA)", "Chance de " + (MathHelper.round(tiers[0] * 100, 1)) + "%", false)
						.addField(KawaiponRarity.RARE.getEmote() + " | Evogear tier 2 (\uD83D\uDFCA\uD83D\uDFCA)", "Chance de " + (MathHelper.round(tiers[1] * 100, 1)) + "%", false)
						.addField(KawaiponRarity.ULTRA_RARE.getEmote() + " | Evogear tier 3 (\uD83D\uDFCA\uD83D\uDFCA\uD83D\uDFCA)", "Chance de " + (MathHelper.round(tiers[2] * 100, 1)) + "%", false)
						.addField(KawaiponRarity.LEGENDARY.getEmote() + " | Evogear tier 4 (\uD83D\uDFCA\uD83D\uDFCA\uD83D\uDFCA\uD83D\uDFCA)", "Chance de " + (MathHelper.round(tiers[3] * 100, 1)) + "%", false);

				Main.getInfo().getConfirmationPending().put(author.getId(), true);
				channel.sendMessage("Você está prester a sintetizar um evogear usando essas cartas (" + (freeRolls > 0 ? "possui " + freeRolls + " sínteses gratúitas" : "elas serão destruídas no processo") + "). Deseja continuar?")
						.setEmbeds(eb.build())
						.queue(s -> {
									Map<Emoji, ThrowingConsumer<ButtonWrapper>> buttons = new HashMap<>();
									buttons.put(StringHelper.parseEmoji(Constants.ACCEPT), wrapper -> {
										Main.getInfo().getConfirmationPending().remove(author.getId());
										s.delete().queue();

										String tier = StringUtils.repeat("\uD83D\uDFCA", e.getTier());

										if (freeRolls == 0) {
											for (Card t : tributes) {
												kp.removeCard(new KawaiponCard(t, false));
											}
										} else {
											if (freeRolls > 1) {
												dp.setValue(freeRolls - 1);
												dp.save();
											} else {
												dp.delete();
											}
										}

										if (dk.checkEquipmentError(e) != 0) {
											int change = (int) Math.round((350 + (score * 1400 / 15f)) * (e.getTier() == 4 ? 3.5 : 2.5));

											Account acc = Account.find(Account.class, author.getId());
											acc.addCredit(change, this.getClass());
											acc.save();

											channel.sendMessage(
													switch (dk.checkEquipmentError(e)) {
														case 1 -> "❌ | Você já possui 3 cópias de **" + e.getCard().getName() + "**! (" + tier + "), as cartas usadas foram convertidas em " + StringHelper.separate(change) + " CR.";
														case 2 -> "❌ | Você já possui 1 evogear tier 4, **" + e.getCard().getName() + "**! (" + tier + "), as cartas usadas foram convertidas em " + StringHelper.separate(change) + " CR.";
														case 3 -> "❌ | Você não possui mais espaços para evogears, as cartas usadas cartas foram convertidas em " + StringHelper.separate(change) + " CR.";
														default -> throw new IllegalStateException("Unexpected value: " + dk.checkEquipmentError(e));
													}
											).queue();
										} else {
											dk.addEquipment(e);
											channel.sendMessage("✅ | Síntese realizada com sucesso, você obteve o evogear **" + e.getCard().getName() + "**! (" + tier + ")").queue();
										}

										if (blessed) bless.delete();

										kp.save();
									});

									Pages.buttonize(s, buttons, Constants.USE_BUTTONS, true, 1, TimeUnit.MINUTES,
											u -> u.getId().equals(author.getId()),
											ms -> Main.getInfo().getConfirmationPending().remove(author.getId())
									);
								}
						);
			}
		}
	}
}
