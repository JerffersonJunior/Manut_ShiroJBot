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

package com.kuuhaku.command.commands.information;

import com.kuuhaku.Main;
import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import com.kuuhaku.controller.postgresql.TicketDAO;
import com.kuuhaku.utils.Helper;
import com.kuuhaku.utils.I18n;
import com.kuuhaku.utils.ShiroInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class ReportBugCommand extends Command {

	public ReportBugCommand(String name, String description, Category category, boolean requiresMM) {
		super(name, description, category, requiresMM);
	}

	public ReportBugCommand(String name, String[] aliases, String description, Category category, boolean requiresMM) {
		super(name, aliases, description, category, requiresMM);
	}

	public ReportBugCommand(String name, String usage, String description, Category category, boolean requiresMM) {
		super(name, usage, description, category, requiresMM);
	}

	public ReportBugCommand(@NonNls String name, @NonNls String[] aliases, String usage, String description, Category category, boolean requiresMM) {
		super(name, aliases, usage, description, category, requiresMM);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, String prefix) {

		if (args.length == 0) {
			channel.sendMessage(":x: | Você precisa definir uma mensagem.").queue();
			return;
		}

		String mensagem = String.join(" ", args).trim();
		int number = TicketDAO.openTicket(mensagem, author);

		EmbedBuilder eb = new EmbedBuilder();

		eb.setTitle("Relatório de bug (Ticket Nº " + number + ")");
		eb.addField("Enviador por:", author.getAsTag() + " (" + guild.getName() + " | " + channel.getName() + ")", true);
		eb.addField("Enviado em:", Helper.dateformat.format(message.getTimeCreated().atZoneSameInstant(ZoneId.of("GMT-3"))), true);
		eb.addField("Relatório:", "```" + mensagem + "```", false);
		eb.setFooter(author.getId());
		eb.setColor(Color.yellow);

		Map<String, String> ids = new HashMap<>();

		Stream.of(Main.getInfo().getDevelopers(), Main.getInfo().getSupports()).flatMap(ArrayList::stream).forEach(dev -> Main.getInfo().getUserByID(dev).openPrivateChannel()
				.flatMap(m -> m.sendMessage(eb.build()))
				.flatMap(m -> {
					ids.put(dev, m.getId());
					return m.pin();
				})
				.complete()
		);

		TicketDAO.setIds(number, ids);
		channel.sendMessage(ShiroInfo.getLocale(I18n.PT).getString("str_successfully-reported-bug")).queue();
	}
}
