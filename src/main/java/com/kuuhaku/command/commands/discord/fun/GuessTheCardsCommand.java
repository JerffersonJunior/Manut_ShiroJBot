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

package com.kuuhaku.command.commands.discord.fun;

import com.kuuhaku.Main;
import com.kuuhaku.command.Category;
import com.kuuhaku.command.Executable;
import com.kuuhaku.controller.postgresql.CardDAO;
import com.kuuhaku.events.SimpleMessageListener;
import com.kuuhaku.model.annotations.Command;
import com.kuuhaku.model.annotations.Requires;
import com.kuuhaku.model.enums.I18n;
import com.kuuhaku.model.persistent.Account;
import com.kuuhaku.model.persistent.Card;
import com.kuuhaku.model.persistent.Leaderboards;
import com.kuuhaku.utils.helpers.*;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Command(
		name = "adivinheascartas",
		aliases = {"aac", "guessthecards", "gtc"},
		category = Category.FUN
)
@Requires({Permission.MESSAGE_ATTACH_FILES})
public class GuessTheCardsCommand implements Executable {

	@Override
	public void execute(User author, Member member, String argsAsText, String[] args, Message message, TextChannel channel, Guild guild, String prefix) {
		if (Main.getInfo().gameInProgress(author.getId())) {
			channel.sendMessage(I18n.getString("err_you-are-in-game")).queue();
			return;
		}

		try {
			File[] masks = new File(FileHelper.getResource(this.getClass(), "assets/masks").toURI()).listFiles();
			assert masks != null;

			BufferedImage mask = ImageHelper.toColorSpace(ImageIO.read(CollectionHelper.getRandomEntry(masks)), BufferedImage.TYPE_INT_ARGB);

			List<Card> c = CollectionHelper.getRandomN(CardDAO.getCards(), 3, 1);
			List<String> names = c.stream().map(Card::getId).collect(Collectors.toList());
			List<BufferedImage> imgs = c.stream()
					.map(Card::drawCardNoBorder)
					.map(bi -> ImageHelper.toColorSpace(bi, BufferedImage.TYPE_INT_ARGB))
					.toList();

			BufferedImage img = new BufferedImage(225, 350, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = img.createGraphics();
			for (int i = 0; i < imgs.size(); i++) {
				BufferedImage bi = imgs.get(i);
				ImageHelper.applyMask(bi, mask, i);
				g2d.drawImage(bi, 0, 0, null);
			}
			g2d.dispose();

			channel.sendMessage("Quais são as 3 cartas nesta imagem? Escreva os três nomes com `_` no lugar de espaços e separados por ponto-e-vírgula (`;`).")
					.addFile(ImageHelper.writeAndGet(img, "cards", "png"))
					.queue(ms -> Main.getEvents().addHandler(guild, new SimpleMessageListener(channel) {
						private final Consumer<Void> success = s -> {
							ms.delete().queue(null, MiscHelper::doNothing);
							close();
						};
						private Future<?> timeout = channel.sendMessage("Acabou o tempo, as cartas eram `%s`, `%s` e `%s`".formatted(
								names.get(0),
								names.get(1),
								names.get(2))
						).queueAfter(2, TimeUnit.MINUTES, msg -> success.accept(null));
						int chances = 2;

						{
							Main.getInfo().setGameInProgress(mutex, author);
						}

						@Override
						public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
							if (!event.getAuthor().getId().equals(author.getId())) return;

							String value = event.getMessage().getContentRaw();
							if (value.equalsIgnoreCase("desistir") || LogicHelper.equalsAny(value.toLowerCase(Locale.ROOT).split(" ")[0].replaceFirst(prefix, ""), GuessTheCardsCommand.class.getDeclaredAnnotation(Command.class).aliases())) {
								channel.sendMessage("Você desistiu, as cartas eram `%s`, `%s` e `%s`".formatted(
										names.get(0),
										names.get(1),
										names.get(2))
								).queue();
								success.accept(null);
								timeout.cancel(true);
								timeout = null;
								return;
							}

							String[] answers = value.split(";");

							if (answers.length != 3 && chances > 0) {
								channel.sendMessage("❌ | Você deve informar exatamente 3 nomes separados por ponto-e-vírgula.").queue();
								chances--;
								return;
							} else if (answers.length != 3) {
								channel.sendMessage("❌ | Você errou muitas vezes, o jogo foi encerrado.").queue();
								success.accept(null);
								timeout.cancel(true);
								timeout = null;
								return;
							}

							int points = 0;
							for (String s : answers)
								points += names.remove(s.toUpperCase(Locale.ROOT)) ? 1 : 0;

							int reward = (int) (50 * Math.pow(2 + MathHelper.rng(0.2, 0.5), points));

							Account acc = Account.find(Account.class, author.getId());
							acc.addCredit(reward, GuessTheCardsCommand.class);

							switch (points) {
								case 0 -> {
									channel.sendMessage(
											"Você não acertou nenhum dos 3 nomes, que eram `%s`, `%s` e `%s`."
													.formatted(
															names.get(0),
															names.get(1),
															names.get(2)
													)).queue();
									int lost = Leaderboards.queryNative(Number.class, "SELECT COALESCE(SUM(l.score), 0) FROM Leaderboards l WHERE l.uid = :uid AND l.minigame = :game LIMIT 10",
											author.getId(),
											getThis().getSimpleName()
									).intValue();
									if (lost > 0) {
										new Leaderboards(author, getThis(), -lost).save();
									}
								}
								case 1 -> {
									channel.sendMessage(
											"Você acertou 1 dos 3 nomes, os outro eram `%s` e `%s`. (Recebeu %s CR)."
													.formatted(
															names.get(0),
															names.get(1),
															StringHelper.separate(reward)
													)).queue();
									int lost = Leaderboards.queryNative(Number.class, "SELECT COALESCE(SUM(l.score), 0) FROM Leaderboards l WHERE l.uid = :uid AND l.minigame = :game LIMIT 10",
											author.getId(),
											getThis().getSimpleName()
									).intValue();
									new Leaderboards(author, getThis(), 1 - lost).save();
								}
								case 2 -> {
									channel.sendMessage(
											"Você acertou 2 dos 3 nomes, o outro era `%s`. (Recebeu %s CR)."
													.formatted(
															names.get(0),
															StringHelper.separate(reward)
													)).queue();
									int lost = Leaderboards.queryNative(Number.class, "SELECT COALESCE(SUM(l.score), 0) FROM Leaderboards l WHERE l.uid = :uid AND l.minigame = :game LIMIT 10",
											author.getId(),
											getThis().getSimpleName()
									).intValue();
									new Leaderboards(author, getThis(), 2 - lost).save();
								}
								case 3 -> {
									channel.sendMessage(
											"Você acertou todos os nomes, parabéns! (Recebeu %s CR)."
													.formatted(StringHelper.separate(reward))).queue();
									new Leaderboards(author, getThis(), 3).save();
								}
							}

							acc.save();
							success.accept(null);
							timeout.cancel(true);
							timeout = null;
						}
					}));
		} catch (IOException | URISyntaxException e) {
			MiscHelper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
		}
	}
}
