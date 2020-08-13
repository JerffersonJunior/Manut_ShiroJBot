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

package com.kuuhaku.command.commands.twitch;

import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.common.enums.CommandPermission;
import com.github.twitch4j.common.events.domain.EventChannel;
import com.github.twitch4j.common.events.domain.EventUser;
import com.kuuhaku.command.TwitchCommand;
import com.kuuhaku.model.persistent.Account;

import java.util.Random;
import java.util.Set;

public class ChooseCommand extends TwitchCommand {

	public ChooseCommand(String name, String description, boolean requiresBinding) {
		super(name, description, requiresBinding);
	}

	public ChooseCommand(String name, String[] aliases, String description, boolean requiresBinding) {
		super(name, aliases, description, requiresBinding);
	}

	public ChooseCommand(String name, String usage, String description, boolean requiresBinding) {
		super(name, usage, description, requiresBinding);
	}

	public ChooseCommand(String name, String[] aliases, String usage, String description, boolean requiresBinding) {
		super(name, aliases, usage, description, requiresBinding);
	}

	@Override
	public void execute(EventUser author, Account account, String rawCmd, String[] args, ChannelMessageEvent message, EventChannel channel, TwitchChat chat, Set<CommandPermission> permissions) {
		if (args.length == 0) {
			chat.sendMessage(channel.getName(), "❌ | Você não me deu nenhuma opção.");
			return;
		} else if (!args[0].contains(";")) {
			chat.sendMessage(channel.getName(), "❌ | Você precisa me dar ao menos duas opções.");
			return;
		}

		String[] opts = args[0].split(";");
		long seed = 0;

		for (char c : args[0].toLowerCase().toCharArray()) {
			seed += (int) c;
		}

		int choice = new Random(seed).nextInt(opts.length);

		chat.sendMessage(channel.getName(), ":question: | Eu escolho a opção " + (choice + 1) + ": **" + opts[choice] + "**!");
	}
}
