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

package com.kuuhaku.command.commands.discord.moderation;

import com.kuuhaku.command.Category;
import com.kuuhaku.command.Executable;
import com.kuuhaku.controller.postgresql.GuildDAO;
import com.kuuhaku.model.annotations.Command;
import com.kuuhaku.model.annotations.Requires;
import com.kuuhaku.model.persistent.guild.GuildConfig;
import com.kuuhaku.utils.Helper;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;

@Command(
		name = "tempoenquete",
		aliases = {"tempoe", "polltime", "pollt"},
		usage = "req_time",
		category = Category.MODERATION
)
@Requires({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY, Permission.MANAGE_ROLES})
public class PollTimeCommand implements Executable {

	@Override
	public void execute(User author, Member member, String argsAsText, String[] args, Message message, TextChannel channel, Guild guild) {
		GuildConfig gc = GuildDAO.getGuildById(guild.getId());

		long time = Helper.stringToDurationMillis(argsAsText);
		if (time < 60000) {
			channel.sendMessage("❌ | O tempo deve ser maior que 1 minuto.").queue();
			return;
		}

		gc.setPollTime(time);
		channel.sendMessage("✅ | Tempo de enquetes definido para " + Helper.toStringDuration(time) + " com sucesso!").queue();

		GuildDAO.updateGuildSettings(gc);
	}
}
