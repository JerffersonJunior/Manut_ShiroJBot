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

package com.kuuhaku.command.commands.information;

import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import com.kuuhaku.controller.postgresql.CardDAO;
import com.kuuhaku.controller.postgresql.KawaiponDAO;
import com.kuuhaku.model.common.KawaiponBook;
import com.kuuhaku.model.persistent.Kawaipon;
import com.kuuhaku.model.persistent.KawaiponCard;
import com.kuuhaku.utils.AnimeName;
import com.kuuhaku.utils.Helper;
import com.kuuhaku.utils.I18n;
import com.kuuhaku.utils.ShiroInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import org.jetbrains.annotations.NonNls;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
					m.editMessage(":x: | Você ainda não coletou nenhum Kawaipon.").queue();
					return;
				} else if (args.length == 0) {
					Set<KawaiponCard> collection = new HashSet<>();
					for (AnimeName anime : AnimeName.values()) {
						if (CardDAO.totalCards(anime) == kp.getCards().stream().filter(k -> k.getCard().getAnime().equals(anime) && !k.isFoil()).count())
							collection.add(new KawaiponCard(CardDAO.getUltimate(anime), false));
					}

					KawaiponBook kb = new KawaiponBook(collection);
					BufferedImage cards = kb.view(null, false);

					try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
						ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
						ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
						writer.setOutput(ios);

						ImageWriteParam param = writer.getDefaultWriteParam();
						if (param.canWriteCompressed()) {
							param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
							param.setCompressionQuality(0.4f);
						}

						writer.write(null, new IIOImage(cards, null, null), param);
						ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
						cards = ImageIO.read(bais);
						bais.close();
					}

					EmbedBuilder eb = new EmbedBuilder();
					int count = collection.size();
					int foil = (int) kp.getCards().stream().filter(KawaiponCard::isFoil).count();
					int common = kp.getCards().size() - foil;

					eb.setTitle("\uD83C\uDFB4 | Kawaipons de " + author.getName());
					eb.addField(":books: | Coleções completas:", count + " de " + AnimeName.values().length + " (" + Helper.prcntToInt(count, AnimeName.values().length) + "%)", true);
					eb.addField(":red_envelope: | Total de cartas comuns:", common + " de " + CardDAO.totalCards() + " (" + Helper.prcntToInt(common, CardDAO.totalCards()) + "%)", true);
					eb.addField(":star2: | Total de cartas cromadas:", foil + " de " + CardDAO.totalCards() + " (" + Helper.prcntToInt(foil, CardDAO.totalCards()) + "%)", true);
					eb.setImage("attachment://cards.jpg");
					eb.setFooter("Total coletado (normais + cromadas): " + Helper.prcntToInt(kp.getCards().size(), CardDAO.totalCards() * 2) + "%");

					m.delete().queue();
					channel.sendMessage(eb.build()).addFile(Helper.getBytes(cards), "cards.jpg").queue();
					return;
				} else if (args.length < 2 || !Helper.equalsAny(args[1], "N", "C")) {
					m.editMessage(":x: | Você precisa especificar o tipo da coleção (`N` = normal, `C` = cromada).").queue();
					return;
				} else if (Arrays.stream(AnimeName.values()).noneMatch(a -> a.name().equals(args[0].toUpperCase()))) {
					m.editMessage(":x: | Anime inválido ou ainda não adicionado (colocar `_` no lugar de espaços).").queue();
					return;
				}

				AnimeName anime = AnimeName.valueOf(args[0].toUpperCase());
				Set<KawaiponCard> collection = kp.getCards().stream().filter(k -> k.getCard().getAnime().equals(anime)).collect(Collectors.toSet());
				Set<KawaiponCard> toRender = collection.stream().filter(k -> k.isFoil() == args[1].equalsIgnoreCase("C")).collect(Collectors.toSet());

				KawaiponBook kb = new KawaiponBook(toRender);
				BufferedImage cards = kb.view(anime, args[1].equalsIgnoreCase("C"));

				try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
					ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
					ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
					writer.setOutput(ios);

					ImageWriteParam param = writer.getDefaultWriteParam();
					if (param.canWriteCompressed()) {
						param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
						param.setCompressionQuality(0.4f);
					}

					writer.write(null, new IIOImage(cards, null, null), param);
					ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
					cards = ImageIO.read(bais);
					bais.close();
				}

				EmbedBuilder eb = new EmbedBuilder();
				int foil = (int) collection.stream().filter(KawaiponCard::isFoil).count();
				int common = collection.size() - foil;

				eb.setTitle("\uD83C\uDFB4 | Kawaipons de " + author.getName() + " (" + anime.toString() + ")");
				eb.addField(":red_envelope: | Cartas comuns:", common + " de " + CardDAO.totalCards(anime) + " (" + Helper.prcntToInt(common, CardDAO.totalCards(anime)) + "%)", true);
				eb.addField(":star2: | Cartas cromadas:", foil + " de " + CardDAO.totalCards(anime) + " (" + Helper.prcntToInt(foil, CardDAO.totalCards(anime)) + "%)", true);
				eb.setImage("attachment://cards.jpg");
				eb.setFooter("Total coletado (normais + cromadas): " + Helper.prcntToInt(collection.size(), CardDAO.totalCards(anime) * 2) + "%");

				m.delete().queue();
				channel.sendMessage(eb.build()).addFile(Helper.getBytes(cards), "cards.jpg").queue();
			} catch (IOException | InterruptedException e) {
				m.editMessage(ShiroInfo.getLocale(I18n.PT).getString("err_collection-generation-error")).queue();
				Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
			}
		});
	}
}
