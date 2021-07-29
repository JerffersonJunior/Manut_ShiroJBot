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

package com.kuuhaku.events;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.github.philippheuer.events4j.simple.SimpleEventHandler;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.chat.events.channel.FollowEvent;
import com.github.twitch4j.common.events.domain.EventChannel;
import com.github.twitch4j.common.events.domain.EventUser;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import com.kuuhaku.Main;
import com.kuuhaku.command.TwitchCommand;
import com.kuuhaku.controller.postgresql.AccountDAO;
import com.kuuhaku.controller.postgresql.BlacklistDAO;
import com.kuuhaku.model.enums.I18n;
import com.kuuhaku.model.persistent.Account;
import com.kuuhaku.utils.Helper;
import com.kuuhaku.utils.ShiroInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.awt.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TwitchEvents {
	private final SimpleEventHandler handler;

	public TwitchEvents(TwitchClient client) {
		this.handler = client.getEventManager().getEventHandler(SimpleEventHandler.class);
		handler.onEvent(ChannelMessageEvent.class, this::onChannelMessageEvent);
		handler.onEvent(FollowEvent.class, this::onFollowEvent);
		handler.onEvent(ChannelGoLiveEvent.class, this::onChannelGoLiveEvent);
		handler.onEvent(ChannelGoOfflineEvent.class, this::onChannelGoOfflineEvent);
	}

	private void onChannelMessageEvent(ChannelMessageEvent message) {
		EventUser author = message.getUser();
		EventChannel channel = message.getChannel();
		TwitchClient client = Main.getTwitch();
		String rawMessage = message.getMessage().replaceAll("\s+", " ");

		boolean blacklisted = BlacklistDAO.isBlacklisted(author);
		if (blacklisted || message.getUser().getName().equalsIgnoreCase("shirojbot")) return;

		Account acc = AccountDAO.getAccountByTwitchId(author.getId());

		String prefix = ShiroInfo.getDefaultPrefix();
		String commandName = "";
		String rawMsgNoCommand = "";
		if (rawMessage.toLowerCase(Locale.ROOT).startsWith(prefix)) {
			String rawMsgNoPrefix = rawMessage.substring(prefix.length()).trim();
			commandName = rawMsgNoPrefix.split(" ")[0].trim();
			rawMsgNoCommand = rawMessage.substring(prefix.length() + commandName.length()).trim();
		}

		String[] args = Arrays.stream(rawMsgNoCommand.split(" "))
				.filter(s -> !s.isBlank())
				.toArray(String[]::new);

		TwitchCommand command = Main.getTwitchCommandManager().getCommand(commandName);
		if (command != null) {
			if (command.requiresBinding() && acc == null) {
				client.getChat().sendMessage(channel.getName(), I18n.getString("err_user-not-bound"));
				return;
			} else if (BlacklistDAO.isBlacklisted(author)) {
				client.getChat().sendMessage(channel.getName(), I18n.getString("err_user-blacklisted"));
				return;
			} else if (Main.getInfo().getRatelimit().containsKey(author.getId())) {
				client.getChat().sendMessage(channel.getName(), I18n.getString("err_user-ratelimited"));
				return;
			}

			Main.getInfo().getRatelimit().put(author.getId(), true, 2 + Helper.rng(3, false), TimeUnit.SECONDS);

			command.execute(author, acc, commandName, rawMsgNoCommand, args, message, channel, client.getChat(), message.getPermissions());

			String ad = Helper.getAd();
			if (ad != null) {
				client.getChat().sendMessage(channel.getName(), ad);
			}
		} else if (acc != null && Main.getInfo().isLive()) {
			try {
				User u = Main.getInfo().getUserByID(acc.getUid());
				TextChannel tc = Main.getInfo()
						.getGuildByID(ShiroInfo.getSupportServerID())
						.getTextChannelById(ShiroInfo.getTwitchChannelID());
				assert tc != null;
				Webhook wh = Helper.getOrCreateWebhook(tc, "Shiro");

				WebhookMessageBuilder wmb = new WebhookMessageBuilder();
				wmb.setContent(Helper.stripEmotesAndMentions(rawMessage));
				wmb.setUsername(u.getName());
				wmb.setAvatarUrl(u.getEffectiveAvatarUrl());

				assert wh != null;
				WebhookClient wc = new WebhookClientBuilder(wh.getUrl()).build();
				wc.send(wmb.build()).get();
			} catch (InsufficientPermissionException | InterruptedException | ExecutionException e) {
				Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
			} catch (NullPointerException ignore) {
			}
		}
	}

	private void onFollowEvent(FollowEvent evt) {
		Account acc = AccountDAO.getAccountByTwitchId(evt.getUser().getId());

		if (acc == null || acc.isFollower()) return;

		Main.getInfo().getUserByID(acc.getUid()).openPrivateChannel().queue(c -> {
			try {
				EmbedBuilder eb = new EmbedBuilder();

				eb.setThumbnail("https://i.imgur.com/A0jXqpe.png");
				eb.setTitle("Opa, obrigada por seguir o canal do meu nii-chan!");
				eb.setDescription("Como agradecimento, aqui estão 5.000 créditos para serem utilizados nos módulos que utilizam o sistema de dinheiro.");
				eb.setFooter("Seus créditos: " + Helper.separate(acc.getBalance() + 5000), "https://i.imgur.com/U0nPjLx.gif");
				eb.setColor(Color.cyan);

				acc.addCredit(5000, this.getClass());
				acc.setFollower(true);
				AccountDAO.saveAccount(acc);
				c.sendMessageEmbeds(eb.build()).queue(null, Helper::doNothing);
			} catch (RuntimeException e) {
				Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
			}
		}, Helper::doNothing);
	}

	private void onChannelGoLiveEvent(ChannelGoLiveEvent evt) {
		Main.getInfo().setLive(true);
		Main.getShiroShards().setActivity(Activity.streaming("Na conta do meu Nii-chan sem ele saber!", "https://twitch.tv/kuuhaku_otgmz"));
		Guild sup = Main.getInfo().getGuildByID(ShiroInfo.getSupportServerID());
		TextChannel tth = sup.getTextChannelById(ShiroInfo.getTwitchChannelID());

		assert tth != null;
		tth.getManager()
				.putPermissionOverride(sup.getPublicRole(), Set.of(Permission.MESSAGE_WRITE), null)
				.queue();
	}

	private void onChannelGoOfflineEvent(ChannelGoOfflineEvent evt) {
		Main.getInfo().setLive(false);
		for (JDA shard : Main.getShiroShards().getShards()) {
			shard.getPresence().setActivity(Main.getRandomActivity());
		}
		Guild sup = Main.getInfo().getGuildByID(ShiroInfo.getSupportServerID());
		TextChannel tth = sup.getTextChannelById(ShiroInfo.getTwitchChannelID());

		assert tth != null;
		tth.getManager()
				.putPermissionOverride(sup.getPublicRole(), null, Set.of(Permission.MESSAGE_WRITE))
				.queue();
	}

	public SimpleEventHandler getHandler() {
		return handler;
	}
}
