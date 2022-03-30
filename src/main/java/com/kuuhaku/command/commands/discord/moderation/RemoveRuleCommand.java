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
import com.kuuhaku.model.enums.I18n;
import com.kuuhaku.model.persistent.guild.GuildConfig;
import com.kuuhaku.utils.helpers.MathHelper;
import net.dv8tion.jda.api.entities.*;

import java.util.List;

@Command(
		name = "removerregra",
		aliases = {"delrule", "dr"},
		usage = "req_index",
		category = Category.MODERATION
)
public class RemoveRuleCommand implements Executable {

	@Override
	public void execute(User author, Member member, String argsAsText, String[] args, Message message, TextChannel channel, Guild guild, String prefix) {
		if (args.length < 1) {
			channel.sendMessage("❌ | É necessário digitar o índice da regra a ser removida.").queue();
			return;
		}

		GuildConfig gc = GuildDAO.getGuildById(guild.getId());
		List<String> rules = gc.getRules();

		try {
			int i = Integer.parseInt(args[0]);
			if (!MathHelper.between(i, 0, rules.size())) {
				channel.sendMessage("❌ | Regra inexistente.").queue();
				return;
			}

			gc.removeRule(i);
			channel.sendMessage("✅ | Regra removida com sucesso!").queue();

			GuildDAO.updateGuildSettings(gc);
		} catch (NumberFormatException e) {
			channel.sendMessage(I18n.getString("err_invalid-index")).queue();
		}
	}
}
