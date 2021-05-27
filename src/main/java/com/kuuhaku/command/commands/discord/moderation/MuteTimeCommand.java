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
import com.kuuhaku.command.Executable;
import com.kuuhaku.controller.postgresql.GuildDAO;
import com.kuuhaku.model.annotations.Command;
import com.kuuhaku.model.annotations.Requires;
import com.kuuhaku.model.enums.I18n;
import com.kuuhaku.model.persistent.guild.GuildConfig;
import com.kuuhaku.utils.Helper;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;

@Command(
		name = "tempomute",
		aliases = {"tempom", "mutetime", "mutet"},
		usage = "req_minutes",
		category = Category.MODERATION
)
@Requires({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY, Permission.MANAGE_ROLES})
public class MuteTimeCommand implements Executable {

	@Override
	public void execute(User author, Member member, String command, String argsAsText, String[] args, Message message, TextChannel channel, Guild guild, String prefix) {
		GuildConfig gc = GuildDAO.getGuildById(guild.getId());

		try {
			int time = Integer.parseInt(args[0]);
			if (!Helper.between(time, 1, 10080)) {
				channel.sendMessage("❌ | O tempo deve ser um valor entre 1 e 10080.").queue();
				return;
			}

			gc.setMuteTime(time);
			channel.sendMessage("✅ | Tempo de mute definido como " + time + " minutos com sucesso!").queue();
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			channel.sendMessage(I18n.getString("err_invalid-time")).queue();
		}

		GuildDAO.updateGuildSettings(gc);
	}
}
