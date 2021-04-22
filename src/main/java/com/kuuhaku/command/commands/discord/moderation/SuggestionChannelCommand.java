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
import com.kuuhaku.model.persistent.guild.GuildConfig;
import com.kuuhaku.utils.Helper;
import net.dv8tion.jda.api.entities.*;

@Command(
		name = "canalsugestao",
		aliases = {"canalsug", "sugchannel", "suggestionchannel"},
		usage = "req_channel-reset",
		category = Category.MODERATION
)
public class SuggestionChannelCommand implements Executable {

	@Override
	public void execute(User author, Member member, String command, String argsAsText, String[] args, Message message, TextChannel channel, Guild guild, String prefix) {
		GuildConfig gc = GuildDAO.getGuildById(guild.getId());
		if (message.getMentionedChannels().isEmpty() && args.length == 0) {
			TextChannel chn = gc.getSuggestionChannel();
			if (chn == null)
				channel.sendMessage("Ainda não foi definido um canal de sugestões.").queue();
			else
				channel.sendMessage("O canal de sugestões atual do servidor é " + chn + ".").queue();
			return;
		}

		if (Helper.equalsAny(args[0], "limpar", "reset")) {
			gc.setSuggestionChannel(null);
			channel.sendMessage("✅ | Canal de sugestões limpo com sucesso.").queue();
		} else {
			gc.setSuggestionChannel(message.getMentionedChannels().get(0).getId());
			channel.sendMessage("✅ | Canal de sugestões definido com sucesso.").queue();
		}

		GuildDAO.updateGuildSettings(gc);
	}
}
