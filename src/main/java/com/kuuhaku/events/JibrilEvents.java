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

package com.kuuhaku.events;

import com.kuuhaku.Main;
import com.kuuhaku.controller.postgresql.BlacklistDAO;
import com.kuuhaku.controller.postgresql.GuildDAO;
import com.kuuhaku.controller.postgresql.MemberDAO;
import com.kuuhaku.controller.postgresql.TagDAO;
import com.kuuhaku.model.common.RelayBlockList;
import com.kuuhaku.model.persistent.Member;
import com.kuuhaku.utils.Helper;
import com.kuuhaku.utils.ShiroInfo;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.persistence.NoResultException;
import java.io.ByteArrayOutputStream;
import java.util.Objects;

public class JibrilEvents extends ListenerAdapter {

	@Override
	public void onGuildJoin(@NotNull GuildJoinEvent event) {
		boolean beta = TagDAO.getTagById(event.getGuild().getOwnerId()).isBeta() || ShiroInfo.getDevelopers().contains(event.getGuild().getOwnerId());
		try {
			if (beta)
				Helper.sendPM(Objects.requireNonNull(event.getGuild().getOwner()).getUser(), "Obrigada por me adicionar ao seu servidor, utilize `s!settings crelay #CANAL` para definir o canal que usarei para transmitir as mensagens globais!\n\nDúvidas? Pergunte-me diretamente e um de meus desenvolvedores responderá assim que possível!");
			else {
				Helper.sendPM(Objects.requireNonNull(event.getGuild().getOwner()).getUser(), "Eu só posso ser utilizada em servidores com acesso beta, por favor não insista!");
				event.getGuild().leave().queue();
			}
		} catch (Exception err) {
			TextChannel dch = event.getGuild().getDefaultChannel();
			if (dch != null) {
				if (dch.canTalk()) {
					dch.sendMessage("Obrigada por me adicionar ao seu servidor, utilize `s!settings crelay #CANAL` para definir o canal que usarei para transmitir as mensagens globais!\n\nDúvidas? Pergunte-me diretamente e um de meus desenvolvedores responderá assim que possível!").queue();
				}
			}
		}

		for (String d : ShiroInfo.getDevelopers()) {
			Main.getJibril().retrieveUserById(d).queue(u ->
					u.openPrivateChannel().queue(c -> {
						String msg = "Acabei de entrar no servidor \"" + event.getGuild().getName() + "\".";
						c.sendMessage(msg).queue();
					}));
		}
		Helper.logger(this.getClass()).info("Acabei de entrar no servidor \"" + event.getGuild().getName() + "\".");
	}

	@Override
	public void onGuildLeave(@NotNull GuildLeaveEvent event) {
		for (String d : ShiroInfo.getDevelopers()) {
			Main.getJibril().retrieveUserById(d).queue(u ->
					u.openPrivateChannel().queue(c -> {
						String msg = "Acabei de sair do servidor \"" + event.getGuild().getName() + "\".";
						c.sendMessage(msg).queue();
					}));
		}
		Helper.logger(this.getClass()).info("Acabei de sair do servidor \"" + event.getGuild().getName() + "\".");
	}

	@Override
	public void onPrivateMessageReceived(@NotNull PrivateMessageReceivedEvent event) {
		ShiroInfo.getShiroEvents().onPrivateMessageReceived(event);
	}

	@Override
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		boolean beta = TagDAO.getTagById(event.getGuild().getOwnerId()).isBeta() || ShiroInfo.getDevelopers().contains(event.getGuild().getOwnerId());
		try {
			User author = event.getAuthor();
			Guild guild = event.getGuild();
			TextChannel channel = event.getChannel();
			Message message = event.getMessage();
			String rawMessage = message.getContentRaw();

			if (BlacklistDAO.isBlacklisted(author) || author.isBot() || !beta) return;

			if (Helper.isPureMention(rawMessage) && Helper.isPinging(message, Main.getJibril().getSelfUser().getId())) {
				channel.sendMessage("Oi? Ah, você quer saber meus comandos né?\nBem, eu não sou uma bot de comandos, eu apenas gerencio o chat global, que pode ser definido pelos moderadores deste servidor usando `" + GuildDAO.getGuildById(guild.getId()).getPrefix() + "settings crelay #CANAL`!").queue(null, Helper::doNothing);
				return;
			}

			if (Main.getRelay().getRelayMap().containsValue(channel.getId())) {
				Member mb = com.kuuhaku.controller.sqlite.MemberDAO.getMember(author.getId(), guild.getId());

				if (!mb.isRulesSent())
					try {
						author.openPrivateChannel()
								.flatMap(c -> c.sendMessage(introMsg()))
								.flatMap(s -> s.getChannel().sendMessage(rulesMsg()))
								.flatMap(s -> s.getChannel().sendMessage(finalMsg()))
								.queue(s -> {
									mb.setRulesSent(true);
									com.kuuhaku.controller.sqlite.MemberDAO.updateMemberConfigs(mb);
									MemberDAO.saveMember(mb);
								}, Helper::doNothing);
					} catch (ErrorResponseException ignore) {
					}
				if (RelayBlockList.check(author.getId())) {
					if (!GuildDAO.getGuildById(guild.getId()).isLiteMode())
						message.delete().queue();
					author.openPrivateChannel().queue(c -> {
						try {
							String s = "❌ | Você não pode mandar mensagens no chat global (bloqueado).";
							c.getHistory().retrievePast(20).queue(h -> {
								if (h.stream().noneMatch(m -> m.getContentRaw().equalsIgnoreCase(s)))
									c.sendMessage(s).queue();
							});
						} catch (ErrorResponseException ignore) {
						}
					});
					return;
				}
				String[] msg = message.getContentRaw().split(" ");
				for (int i = 0; i < msg.length; i++) {
					try {
						if (Helper.findURL(msg[i]))
							msg[i] = "`LINK BLOQUEADO`";
						if (Helper.findMentions(msg[i]))
							msg[i] = "`EVERYONE/HERE BLOQUEADO`";
					} catch (NoResultException e) {
						if (Helper.findURL(msg[i])) msg[i] = "`LINK BLOQUEADO`";
					}
				}

				if (channel.getSlowmode() == 0) {
					channel.sendMessage("❌ | Não vou enviar mensagens se este canal estiver com o slowmode desligado.").queue();
				} else if (String.join(" ", msg).length() < 2000) {
					net.dv8tion.jda.api.entities.Member m = event.getMember();
					assert m != null;
					try {
						if (message.getAttachments().size() > 0) {
							try {
								ByteArrayOutputStream baos = new ByteArrayOutputStream();
								ImageIO.write(ImageIO.read(Helper.getImage(message.getAttachments().get(0).getUrl())), "png", baos);
								Main.getRelay().relayMessage(message, String.join(" ", msg), m, guild, baos);
							} catch (Exception e) {
								Main.getRelay().relayMessage(message, String.join(" ", msg), m, guild, null);
							}
							return;
						}
						Main.getRelay().relayMessage(message, String.join(" ", msg), m, guild, null);
					} catch (NoResultException e) {
						Main.getRelay().relayMessage(message, String.join(" ", msg), m, guild, null);
					}
				}
			}
		} catch (ErrorResponseException e) {
			Helper.logger(this.getClass()).error(e.getErrorCode() + ": " + e + " | " + e.getStackTrace()[0]);
		}
	}

	private static String introMsg() {
		return """
				__**Olá, sou Jibril, a administradora do chat global!**__
				Pera, o que? Você não sabe o que é o chat global?? Bem, vou te explicar!
				    
				O chat global (ou relay) é uma criação de meu mestre KuuHaKu, ele une todos os servidores em que estou em um único canal de texto.\s
				Assim, todos os servidores participantes terão um fluxo de mensagens a todo momento, quebrando aquele "gelo" que muitos servidores pequenos possuem.
								
				""";
	}

	private static String rulesMsg() {
		return """
				__**Mas existem regras, viu?**__
				Como todo chat, para mantermos um ambiente saudável e amigável são necessárias regras.
				    
				O chat global possue suas próprias regras, além daquelas do servidor atual, que são:
				1 - SPAM ou flood é proibido, pois além de ser desnecessário faz com que eu fique lenta;
				2 - Links e imagens são bloqueadas, você não será punido por elas pois elas não serão enviadas;
				3 - Avatares indecentes serão bloqueados 3 vezes antes de te causar um bloqueio no chat global;
				4 - Os bloqueios são temporários, todos serão desbloqueados quando eu e a Shiro formos reiniciadas. Porém, o terceiro bloqueio é permanente e você __**NÃO**__ será desbloqueado de um permanente.
				    
				""";
	}

	private static String finalMsg() {
		return """
				__**E é isso, seja bem-vindo(a) ao grande chat global!**__
				    
				Se tiver dúvidas, denúncias ou sugestões, basta me enviar uma mensagem neste canal privado, ou usar os comando `bug` (feedback) ou `report` (denúncia).
				""";
	}
}
