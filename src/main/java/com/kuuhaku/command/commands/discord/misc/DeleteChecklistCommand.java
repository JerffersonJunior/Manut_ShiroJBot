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

package com.kuuhaku.command.commands.discord.misc;

import com.kuuhaku.command.Category;
import com.kuuhaku.command.Executable;
import com.kuuhaku.controller.postgresql.ChecklistDAO;
import com.kuuhaku.model.annotations.Command;
import com.kuuhaku.model.persistent.Checklist;
import net.dv8tion.jda.api.entities.*;

@Command(
		name = "removerbloco",
		aliases = {"removelist"},
		usage = "req_index",
		category = Category.MISC
)
public class DeleteChecklistCommand implements Executable {

	@Override
	public void execute(User author, Member member, String argsAsText, String[] args, Message message, TextChannel channel, Guild guild, String prefix) {
		if (args.length < 1) {
			channel.sendMessage("❌ | Você precisa informar o índice do bloco de notas.").queue();
			return;
		}

		try {
			Checklist c = ChecklistDAO.getChecklist(author.getId(), Integer.parseInt(args[0]));
			if (c == null) {
				channel.sendMessage("❌ | Bloco de notas inexistente.").queue();
				return;
			}

			ChecklistDAO.removeChecklist(c);
			channel.sendMessage("✅ | Bloco de notas removida com sucesso.").queue();
		} catch (NumberFormatException e) {
			channel.sendMessage("❌ | Índice inválido.").queue();
		}
	}
}
