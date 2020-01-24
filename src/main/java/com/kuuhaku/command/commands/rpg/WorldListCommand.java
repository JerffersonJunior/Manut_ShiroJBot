/*
 * This file is part of Shiro J Bot.
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

package com.kuuhaku.command.commands.rpg;

import com.kuuhaku.Main;
import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import net.dv8tion.jda.api.entities.*;

public class WorldListCommand extends Command {

	public WorldListCommand() {
		super("rlista", new String[]{"rlist"}, "Mostra a lista de cadastros. Os tipos são **player**, **monster**, **item** ou **bau**.", Category.RPG);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, String prefix) {
		if (Main.getInfo().getGames().get(guild.getId()).getMaster() == author) {
			if (args.length == 0) {
				channel.sendMessage(":x: | É necessário especificar um tipo de lista (player, mob, item ou bau)").queue();
				return;
			}
			switch (args[0]) {
				case "p":
				case "player":
					Main.getInfo().getGames().get(guild.getId()).listPlayers(message.getTextChannel()).queue();
					break;
				case "m":
				case "mob":
				case "monster":
					Main.getInfo().getGames().get(guild.getId()).listMonsters(message.getTextChannel()).queue();
					break;
				case "i":
				case "item":
					Main.getInfo().getGames().get(guild.getId()).listItems(message.getTextChannel()).queue();
					break;
				case "b":
				case "bau":
					Main.getInfo().getGames().get(guild.getId()).listChests(message.getTextChannel()).queue();
					break;
				default:
					channel.sendMessage(":x: | É necessário especificar um tipo de lista (player, mob, item ou bau)").queue();
			}
		}
	}
}
