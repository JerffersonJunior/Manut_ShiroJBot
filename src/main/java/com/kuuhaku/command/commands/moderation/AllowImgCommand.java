/*
 * This file is part of Shiro J Bot.
 *
 *     Shiro J Bot is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Shiro J Bot is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Shiro J Bot.  If not, see <https://www.gnu.org/licenses/>
 */

package com.kuuhaku.command.commands.moderation;

import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import com.kuuhaku.controller.SQLite;
import com.kuuhaku.model.guildConfig;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.Event;

public class AllowImgCommand extends Command {

	public AllowImgCommand() {
		super("allowimg", new String[]{"aimg"}, "Ativa ou desativa o recebimento de imagens do chat global.", Category.MODERACAO);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, Event event, String prefix) {
		guildConfig gc = SQLite.getGuildById(guild.getId());

		if (gc.isAllowImg()) {
			gc.setAllowImg(false);
			channel.sendMessage("Não virão mais imagens do chat global neste servidor.").queue();
		} else {
			gc.setAllowImg(true);
			channel.sendMessage("Agora imagens enviadas no chat global aparecerão neste servidor.").queue();
		}

		SQLite.updateGuildSettings(gc);
	}
}
