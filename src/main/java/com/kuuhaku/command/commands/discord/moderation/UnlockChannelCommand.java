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

package com.kuuhaku.command.commands.discord.moderation;

import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.managers.ChannelManager;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public class UnlockChannelCommand extends Command {

	public UnlockChannelCommand(String name, String description, Category category, boolean requiresMM) {
		super(name, description, category, requiresMM);
	}

	public UnlockChannelCommand(String name, String[] aliases, String description, Category category, boolean requiresMM) {
		super(name, aliases, description, category, requiresMM);
	}

	public UnlockChannelCommand(String name, String usage, String description, Category category, boolean requiresMM) {
		super(name, usage, description, category, requiresMM);
	}

	public UnlockChannelCommand(@NonNls String name, @NonNls String[] aliases, String usage, String description, Category category, boolean requiresMM) {
		super(name, aliases, usage, description, category, requiresMM);
	}

	@Override
	public void execute(User author, Member member, String command, String argsAsText, String[] args, Message message, MessageChannel channel, Guild guild, String prefix) {
		if (!member.hasPermission(Permission.MANAGE_CHANNEL)) {
			channel.sendMessage("❌ | Você não possui permissão para gerenciar canais.").queue();
			return;
		}

		try {
			TextChannel tc = message.getTextChannel();
			List<PermissionOverride> overrides = tc.getPermissionOverrides();

			ChannelManager mng = message.getTextChannel().getManager();

			mng = mng.removePermissionOverride(guild.getPublicRole());
			if (tc.getParent() == null)
				for (PermissionOverride override : overrides) {
					IPermissionHolder holder = override.getPermissionHolder();
					if (holder != null)
						mng = mng.removePermissionOverride(override.getPermissionHolder());
				}
			else
				mng = mng.sync();

			mng.complete();

			channel.sendMessage(":unlock: | Canal destrancado com sucesso!").queue();
		} catch (InsufficientPermissionException e) {
			channel.sendMessage("❌ | Não possuo a permissão para gerenciar canais.").queue();
		}
	}
}
