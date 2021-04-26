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

package com.kuuhaku.command.commands.discord.clan;

import com.github.ygimenez.method.Pages;
import com.kuuhaku.Main;
import com.kuuhaku.command.Category;
import com.kuuhaku.command.Executable;
import com.kuuhaku.controller.postgresql.AccountDAO;
import com.kuuhaku.controller.postgresql.CardDAO;
import com.kuuhaku.controller.postgresql.ClanDAO;
import com.kuuhaku.controller.postgresql.KawaiponDAO;
import com.kuuhaku.handlers.games.tabletop.games.shoukan.Champion;
import com.kuuhaku.handlers.games.tabletop.games.shoukan.Equipment;
import com.kuuhaku.handlers.games.tabletop.games.shoukan.Field;
import com.kuuhaku.handlers.games.tabletop.games.shoukan.interfaces.Drawable;
import com.kuuhaku.model.annotations.Command;
import com.kuuhaku.model.annotations.Requires;
import com.kuuhaku.model.common.ColorlessEmbedBuilder;
import com.kuuhaku.model.enums.CardType;
import com.kuuhaku.model.enums.ClanPermission;
import com.kuuhaku.model.persistent.Account;
import com.kuuhaku.model.persistent.Clan;
import com.kuuhaku.model.persistent.Kawaipon;
import com.kuuhaku.model.persistent.KawaiponCard;
import com.kuuhaku.utils.Helper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Command(
		name = "creverter",
		aliases = {"crevert"},
		usage = "req_card",
		category = Category.CLAN
)
@Requires({
		Permission.MESSAGE_ATTACH_FILES,
		Permission.MESSAGE_EMBED_LINKS,
		Permission.MESSAGE_MANAGE,
		Permission.MESSAGE_ADD_REACTION
})
public class ClanRevertCardCommand implements Executable {

	@Override
	public void execute(User author, Member member, String command, String argsAsText, String[] args, Message message, TextChannel channel, Guild guild, String prefix) {
		Clan cl = ClanDAO.getUserClan(author.getId());
		if (cl == null) {
			channel.sendMessage("❌ | Você não possui um clã.").queue();
			return;
		} else if (Main.getInfo().getConfirmationPending().get(author.getId()) != null) {
			channel.sendMessage("❌ | Você possui um comando com confirmação pendente, por favor resolva-o antes de usar este comando novamente.").queue();
			return;
		} else if (cl.isLocked(author.getId(), ClanPermission.DECK)) {
			channel.sendMessage("❌ | Você não tem permissão para alterar o deck do clã.").queue();
			return;
		}

		Account acc = AccountDAO.getAccount(author.getId());
		Kawaipon kp = KawaiponDAO.getKawaipon(author.getId());

		if (args.length == 0) {
			channel.sendMessage("❌ | Você precisa digitar o nome da carta senshi que quer converter para carta kawaipon.").queue();
			return;
		}

		CardType ct = CardDAO.identifyType(args[0]);
		Drawable tc = switch (ct) {
			case KAWAIPON -> CardDAO.getChampion(args[0]);
			case EVOGEAR -> CardDAO.getEquipment(args[0]);
			case FIELD -> CardDAO.getField(args[0]);
			case NONE -> null;
		};
		if (tc == null) {
			channel.sendMessage("❌ | Essa carta não existe, você não quis dizer `" + Helper.didYouMean(args[0], CardDAO.getAllCardNames().toArray(String[]::new)) + "`?").queue();
			return;
		}

		switch (ct) {
			case KAWAIPON -> {
				Champion c = (Champion) tc;
				KawaiponCard kc = new KawaiponCard(tc.getCard(), false);
				if (!cl.getDeck().getChampions().contains(c)) {
					channel.sendMessage("❌ | Seu clã não possui essa carta.").queue();
					return;
				}

				if (kp.checkChampion(c, channel)) return;

				c.setAcc(acc);
				EmbedBuilder eb = new ColorlessEmbedBuilder();
				eb.setTitle("Por favor confirme!");
				eb.setDescription("A carta senshi " + kc.getName() + " do seu clã será convertida para carta kawaipon e será adicionada à sua coleção, por favor clique no botão abaixo para confirmar a conversão.");
				eb.setImage("attachment://card.png");

				Main.getInfo().getConfirmationPending().put(author.getId(), true);
				channel.sendMessage(eb.build()).addFile(Helper.writeAndGet(kc.getCard().drawCard(false), "s_" + kc.getCard().getId(), "png"), "card.png")
						.queue(s ->
								Pages.buttonize(s, Map.of(Helper.ACCEPT, (ms, mb) -> {
											Main.getInfo().getConfirmationPending().remove(author.getId());
											kp.addCard(kc);
											cl.getDeck().removeChampion(c);
											KawaiponDAO.saveKawaipon(kp);
											cl.getTransactions().add(author.getAsTag() + " removeu a carta " + tc.getCard().getName() + " do deck");
											ClanDAO.saveClan(cl);
											s.delete().queue();
											channel.sendMessage("✅ | Conversão realizada com sucesso!").queue();
										}), true, 1, TimeUnit.MINUTES,
										u -> u.getId().equals(author.getId()),
										ms -> Main.getInfo().getConfirmationPending().remove(author.getId())
								)
						);
			}
			case EVOGEAR -> {
				Equipment e = (Equipment) tc;
				if (!cl.getDeck().getEquipments().contains(e)) {
					channel.sendMessage("❌ | Seu clã não possui esse equipamento.").queue();
					return;
				}

				if (kp.checkEquipment(e, channel)) return;

				e.setAcc(acc);
				EmbedBuilder eb = new ColorlessEmbedBuilder();
				eb.setTitle("Por favor confirme!");
				eb.setDescription("A carta evogear " + tc.getCard().getName() + " do seu clã será transferida ao seu deck, por favor clique no botão abaixo para confirmar.");
				eb.setImage("attachment://card.png");

				Main.getInfo().getConfirmationPending().put(author.getId(), true);
				channel.sendMessage(eb.build()).addFile(Helper.writeAndGet(e.drawCard(false), "kp_" + e.getCard().getId(), "png"), "card.png")
						.queue(s ->
								Pages.buttonize(s, Map.of(Helper.ACCEPT, (ms, mb) -> {
											Main.getInfo().getConfirmationPending().remove(author.getId());
											kp.addEquipment(e);
											cl.getDeck().removeEquipment(e);
											KawaiponDAO.saveKawaipon(kp);
											cl.getTransactions().add(author.getAsTag() + " removeu a carta " + tc.getCard().getName() + " do deck");
											ClanDAO.saveClan(cl);
											s.delete().queue();
											channel.sendMessage("✅ | Transferência realizada com sucesso!").queue();
										}), true, 1, TimeUnit.MINUTES,
										u -> u.getId().equals(author.getId()),
										ms -> Main.getInfo().getConfirmationPending().remove(author.getId())
								)
						);
			}
			case FIELD -> {
				Field f = (Field) tc;
				if (cl.getDeck().getField(tc.getCard()) == null) {
					channel.sendMessage("❌ | Você não possui esse campo.").queue();
					return;
				}

				if (kp.checkField(f, channel)) return;

				f.setAcc(acc);
				EmbedBuilder eb = new ColorlessEmbedBuilder();
				eb.setTitle("Por favor confirme!");
				eb.setDescription("A carta de campo " + tc.getCard().getName() + " do seu clã será transferida ao seu deck, por favor clique no botão abaixo para confirmar.");
				eb.setImage("attachment://card.png");

				Main.getInfo().getConfirmationPending().put(author.getId(), true);
				channel.sendMessage(eb.build()).addFile(Helper.writeAndGet(f.drawCard(false), f.getCard().getId(), "png"), "card.png")
						.queue(s ->
								Pages.buttonize(s, Map.of(Helper.ACCEPT, (ms, mb) -> {
											Main.getInfo().getConfirmationPending().remove(author.getId());
											kp.addField(f);
											cl.getDeck().removeField(f);
											KawaiponDAO.saveKawaipon(kp);
											cl.getTransactions().add(author.getAsTag() + " removeu a carta " + tc.getCard().getName() + " do deck");
											ClanDAO.saveClan(cl);
											s.delete().queue();
											channel.sendMessage("✅ | Transferência realizada com sucesso!").queue();
										}), true, 1, TimeUnit.MINUTES,
										u -> u.getId().equals(author.getId()),
										ms -> Main.getInfo().getConfirmationPending().remove(author.getId())
								)
						);
			}
		}
	}
}
