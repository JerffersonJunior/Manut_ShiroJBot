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

package com.kuuhaku.command.info;

import com.github.ygimenez.method.Pages;
import com.github.ygimenez.model.InteractPage;
import com.github.ygimenez.model.Page;
import com.kuuhaku.Main;
import com.kuuhaku.interfaces.Executable;
import com.kuuhaku.interfaces.annotations.Command;
import com.kuuhaku.interfaces.annotations.Requires;
import com.kuuhaku.interfaces.annotations.Signature;
import com.kuuhaku.model.common.ColorlessEmbedBuilder;
import com.kuuhaku.model.enums.Category;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.records.EventData;
import com.kuuhaku.model.records.MessageData;
import com.kuuhaku.model.records.PreparedCommand;
import com.kuuhaku.util.SignatureParser;
import com.kuuhaku.util.Utils;
import com.kuuhaku.util.XStringBuilder;
import com.kuuhaku.util.json.JSONObject;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Emote;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Command(
		name = "help",
		category = Category.INFO
)
@Signature("<command:word>")
@Requires(Permission.MESSAGE_EMBED_LINKS)
public class HelpCommand implements Executable {
	@Override
	public void execute(JDA bot, I18N locale, EventData data, MessageData.Guild event, JSONObject args) {
		String cmd = args.getString("command");
		if (cmd.isBlank()) {
			showHomePage(bot, locale, event);
			return;
		}

		cmd = data.config().getSettings().getAliases().getString(cmd, cmd);
		PreparedCommand pc = Main.getCommandManager().getCommand(cmd);
		if (pc == null) {
			event.channel().sendMessage(locale.get("error/command_not_found")).queue();
			return;
		}

		EmbedBuilder eb = new ColorlessEmbedBuilder()
				.setTitle(pc.name());

		List<String> sigs = SignatureParser.extract(locale, pc.command());
		if (!sigs.isEmpty()) {
			eb.addField(
					locale.get("str/command_signatures"),
					"```css\n" + String.join("\n", sigs).formatted(data.config().getPrefix(), pc.name()) + "\n```",
					false
			);
		}

		Set<PreparedCommand> subCmds = pc.getSubCommands();
		if (!subCmds.isEmpty()) {
			XStringBuilder sb = new XStringBuilder(pc.name());

			int i = 0;
			for (PreparedCommand sub : subCmds) {
				String name = sub.name().split("\\.")[1];
				if (++i == subCmds.size()) {
					sb.appendNewLine("  └ `" + name + "`");
				} else {
					sb.appendNewLine("  ├ `" + name + "`");
				}
			}

			eb.addField(locale.get("str/subcommands"), sb.toString(), false);
		}

		event.channel().sendMessageEmbeds(eb.build()).queue();
	}

	private void showHomePage(JDA bot, I18N locale, MessageData.Guild event) {
		List<Category> categories = new ArrayList<>();
		for (Category cat : Category.values()) {
			if (cat.check(event.member())) {
				categories.add(cat);
			}
		}

		EmbedBuilder index = new ColorlessEmbedBuilder()
				.setTitle(locale.get("str/all_commands"))
				.appendDescription(locale.get("str/category_counter", categories.size()) + "\n")
				.appendDescription(locale.get("str/command_counter", categories.stream().map(Category::getCommands).mapToInt(Set::size).sum()));

		Map<Emoji, Page> pages = new LinkedHashMap<>();
		for (Category cat : categories) {
			Emote emt = cat.getEmote();
			if (emt == null) continue;

			index.addField(emt.getAsMention() + " " + cat.getName(locale), cat.getDescription(locale), true);
		}

		Emote home = bot.getEmoteById("674261700366827539");
		if (home != null) {
			index.setThumbnail(home.getImageUrl());
			pages.put(Utils.parseEmoji(home), new InteractPage(index.build()));
		}

		EmbedBuilder eb = new ColorlessEmbedBuilder();
		for (Category cat : categories) {
			Emote emt = cat.getEmote();
			if (emt == null) continue;

			eb.clear()
					.setTitle(cat.getName(locale))
					.setThumbnail(emt.getImageUrl())
					.appendDescription(cat.getDescription(locale) + "\n\n")
					.appendDescription(locale.get("str/command_counter", cat.getCommands().size()));

			pages.put(Utils.parseEmoji(emt), Utils.generatePage(eb, cat.getCommands(), 10, cmd -> {
				if (cmd.name().contains(".")) return null;

				int subs = cmd.getSubCommands().size();
				if (subs > 0) {
					return "`" + cmd.name() + "` **(+" + subs + ")**";
				} else {
					return "`" + cmd.name() + "`";
				}
			}));
		}

		event.channel().sendMessageEmbeds(index.build()).queue(s ->
				Pages.categorize(s, pages, true, 1, TimeUnit.MINUTES, u -> u.equals(event.user()))
		);
	}
}
