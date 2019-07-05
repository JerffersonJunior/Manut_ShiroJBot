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

package com.kuuhaku.command.commands.information;

import com.kuuhaku.Main;
import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import com.kuuhaku.controller.SQLite;
import com.kuuhaku.model.Profile;
import com.kuuhaku.utils.Helper;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.Event;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class URankCommand extends Command {

	public URankCommand() {
		super("rank", new String[]{"ranking", "top10"}, "<global>", "Mostra o ranking de usuários do servidor ou global.", Category.MISC);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, Event event, String prefix) {
		List<com.kuuhaku.model.Member> mbs;

		if (args.length == 0) {
			mbs = SQLite.getMemberRank(guild.getId(), false);
			if (mbs.size() > 7) mbs.subList(7, mbs.size()).clear();
		} else if (args[0].equalsIgnoreCase("global")) {
			mbs = SQLite.getMemberRank(guild.getId(), true);
			if (mbs.size() > 7) mbs.subList(7, mbs.size()).clear();
		} else {
			channel.sendMessage(":x: | O único parâmetro permitido após o comando é `global`.").queue();
			return;
		}

		channel.sendMessage(":hourglass: Buscando dados...").queue(m -> {
			EmbedBuilder eb = new EmbedBuilder();
			StringBuilder sb = new StringBuilder();

			eb.setTitle(":bar_chart: TOP 10 Usuários (" + (args[0].equalsIgnoreCase("global") ? "GLOBAL" : "SERVER") + ")");
			eb.setThumbnail(args[0].equalsIgnoreCase("global") ? "https://www.pngkey.com/png/full/21-217733_free-png-trophy-png-images-transparent-winner-trophy.png" : guild.getIconUrl());
			try {
				eb.setColor(Helper.colorThief(Main.getInfo().getUserByID(mbs.get(0).getMid()).getAvatarUrl()));
			} catch (IOException e) {
				eb.setColor(new Color(Helper.rng(255), Helper.rng(255), Helper.rng(255)));
			}
			for (int i = 1; i < mbs.size() && i < 10; i++) {
				sb.append(i + 1).append(" - ").append(Main.getInfo().getGuildByID(mbs.get(i).getId().substring(18)).getMemberById(mbs.get(i).getMid()).getEffectiveName()).append(" - ").append(mbs.get(i).getLevel()).append(" (").append(mbs.get(0).getXp()).append(")").append("\n");
			}
			eb.addField("1 - " + Main.getInfo().getGuildByID(mbs.get(0).getId().substring(18)).getMemberById(mbs.get(0).getMid()).getEffectiveName() + " - " + mbs.get(0).getLevel() + " (" + mbs.get(0).getXp() + ")", sb.toString(), false);

			m.delete().queue();
			channel.sendMessage(eb.build()).queue();
		});
	}
}
