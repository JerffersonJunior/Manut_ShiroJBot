/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2020  Yago Gimenez (KuuHaKu)
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

package com.kuuhaku.command.commands.discord.information;

import com.kuuhaku.Main;
import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import com.kuuhaku.controller.postgresql.CardDAO;
import com.kuuhaku.controller.postgresql.KawaiponDAO;
import com.kuuhaku.model.common.ColorlessEmbedBuilder;
import com.kuuhaku.model.common.KawaiponBook;
import com.kuuhaku.model.enums.AnimeName;
import com.kuuhaku.model.enums.I18n;
import com.kuuhaku.model.enums.KawaiponRarity;
import com.kuuhaku.model.persistent.Kawaipon;
import com.kuuhaku.model.persistent.KawaiponCard;
import com.kuuhaku.utils.Helper;
import com.kuuhaku.utils.ShiroInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import org.jetbrains.annotations.NonNls;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class KawaiponsCommand extends Command {

	public KawaiponsCommand(String name, String description, Category category, boolean requiresMM) {
		super(name, description, category, requiresMM);
	}

	public KawaiponsCommand(@NonNls String name, @NonNls String[] aliases, String description, Category category, boolean requiresMM) {
		super(name, aliases, description, category, requiresMM);
	}

	public KawaiponsCommand(String name, String usage, String description, Category category, boolean requiresMM) {
		super(name, usage, description, category, requiresMM);
	}

	public KawaiponsCommand(@NonNls String name, @NonNls String[] aliases, String usage, String description, Category category, boolean requiresMM) {
		super(name, aliases, usage, description, category, requiresMM);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, String prefix) {
		channel.sendMessage(ShiroInfo.getLocale(I18n.PT).getString("str_generating-collection")).queue(m -> {
			try {
				Kawaipon kp = KawaiponDAO.getKawaipon(author.getId());

				if (kp.getCards().size() == 0) {
					m.editMessage("❌ | Você ainda não coletou nenhum Kawaipon.").queue();
					return;
				} else if (args.length == 0) {
					Set<KawaiponCard> collection = new HashSet<>();
					for (AnimeName anime : AnimeName.values()) {
						if (CardDAO.totalCards(anime) == kp.getCards().stream().filter(k -> k.getCard().getAnime().equals(anime) && !k.isFoil()).count())
							collection.add(new KawaiponCard(CardDAO.getUltimate(anime), false));
					}

					KawaiponBook kb = new KawaiponBook(collection);
					BufferedImage cards = kb.view(CardDAO.getCardsByRarity(KawaiponRarity.ULTIMATE), "Coleção Kawaipon", false);

					EmbedBuilder eb = new ColorlessEmbedBuilder();
					int count = collection.size();
					int foil = (int) kp.getCards().stream().filter(KawaiponCard::isFoil).count();
					int common = kp.getCards().size() - foil;

					eb.setTitle("\uD83C\uDFB4 | Kawaipons de " + author.getName());
					eb.addField(":books: | Coleções completas:", count + " de " + AnimeName.values().length + " (" + Helper.prcntToInt(count, AnimeName.values().length) + "%)", true);
					eb.addField(":red_envelope: | Total de cartas normais:", common + " de " + CardDAO.totalCards() + " (" + Helper.prcntToInt(common, CardDAO.totalCards()) + "%)", true);
					eb.addField(":star2: | Total de cartas cromadas:", foil + " de " + CardDAO.totalCards() + " (" + Helper.prcntToInt(foil, CardDAO.totalCards()) + "%)", true);
					eb.setImage("attachment://cards.png");
					eb.setFooter("Total coletado (normais + cromadas): " + Helper.prcntToInt(kp.getCards().size(), CardDAO.totalCards() * 2) + "%");

					m.delete().queue();
					channel.sendMessage(eb.build()).addFile(Helper.getBytes(cards, "png"), "cards.png").queue();
					return;
				} else if (args.length < 2 || !Helper.equalsAny(args[1], "N", "C")) {
					m.editMessage("❌ | Você precisa especificar o tipo da coleção (`N` = normal, `C` = cromada).").queue();
					return;
				}

				KawaiponRarity rr = KawaiponRarity.getByName(args[0]);

				if (rr == null) {
					if (args[0].equalsIgnoreCase("total")) {
						Set<KawaiponCard> collection = kp.getCards();
						Set<KawaiponCard> toRender = collection.stream().filter(k -> k.isFoil() == args[1].equalsIgnoreCase("C")).collect(Collectors.toSet());

						KawaiponBook kb = new KawaiponBook(toRender);
						BufferedImage cards = kb.view(CardDAO.getCards(), "Todas as cartas", args[1].equalsIgnoreCase("C"));

						send(author, channel, m, collection, cards, "Todas as cartas", CardDAO.totalCards());
						return;
					} else if (Arrays.stream(AnimeName.values()).noneMatch(a -> a.name().equals(args[0].toUpperCase()))) {
						channel.sendMessage("❌ | Anime inválido ou ainda não adicionado, você não quis dizer `" + Helper.didYouMean(args[0], Arrays.stream(AnimeName.values()).map(AnimeName::name).toArray(String[]::new)) + "`? (colocar `_` no lugar de espaços)").queue();
						return;
					}

					AnimeName anime = AnimeName.valueOf(args[0].toUpperCase());
					Set<KawaiponCard> collection = kp.getCards().stream().filter(k -> k.getCard().getAnime().equals(anime)).collect(Collectors.toSet());
					Set<KawaiponCard> toRender = collection.stream().filter(k -> k.isFoil() == args[1].equalsIgnoreCase("C")).collect(Collectors.toSet());

					KawaiponBook kb = new KawaiponBook(toRender);
					BufferedImage cards = kb.view(CardDAO.getCardsByAnime(anime), anime.toString(), args[1].equalsIgnoreCase("C"));

					send(author, channel, m, collection, cards, anime.toString(), CardDAO.totalCards(anime));
				} else {
					Set<KawaiponCard> collection = kp.getCards().stream().filter(k -> k.getCard().getRarity().equals(rr)).collect(Collectors.toSet());
					Set<KawaiponCard> toRender = collection.stream().filter(k -> k.isFoil() == args[1].equalsIgnoreCase("C")).collect(Collectors.toSet());

					KawaiponBook kb = new KawaiponBook(toRender);
					BufferedImage cards = kb.view(CardDAO.getCardsByRarity(rr), rr.toString(), args[1].equalsIgnoreCase("C"));


					send(author, channel, m, collection, cards, rr.toString(), CardDAO.totalCards(rr));
				}
			} catch (IOException | InterruptedException e) {
				m.editMessage(ShiroInfo.getLocale(I18n.PT).getString("err_collection-generation-error")).queue();
				Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
			}
		});
	}

	private void send(User author, MessageChannel channel, Message m, Set<KawaiponCard> collection, BufferedImage cards, String s, long l) throws IOException {
		String hash = Helper.hash((author.getId() + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8), "SHA-1");
		File f = new File(Main.getInfo().getCollectionsFolder(), hash + ".jpg");
		Helper.keepMaximumNFiles(Main.getInfo().getCollectionsFolder(), 10);
		byte[] bytes = Helper.getBytes(Helper.removeAlpha(cards), "jpg", 0.5f);
		//byte[] bytes = Helper.getBytes(Helper.removeAlpha(cards), "jpg");
		try (FileOutputStream fos = new FileOutputStream(f)) {
			fos.write(bytes);
		}

		EmbedBuilder eb = new ColorlessEmbedBuilder();
		int foil = (int) collection.stream().filter(KawaiponCard::isFoil).count();
		int common = collection.size() - foil;

		eb.setTitle("\uD83C\uDFB4 | Kawaipons de " + author.getName() + " (" + s + ")");
		eb.addField(":red_envelope: | Cartas normais:", common + " de " + l + " (" + Helper.prcntToInt(common, l) + "%)", true);
		eb.addField(":star2: | Cartas cromadas:", foil + " de " + l + " (" + Helper.prcntToInt(foil, l) + "%)", true);
		eb.setFooter("Total coletado (normais + cromadas): " + Helper.prcntToInt(collection.size(), l * 2) + "%");
		eb.setImage("https://api." + System.getenv("SERVER_URL") + "/card?id=" + hash);
		m.delete().queue();

		channel.sendMessage(eb.build()).queue();
	}
}