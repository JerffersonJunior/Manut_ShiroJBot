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

package com.kuuhaku.command.commands.moderation;

import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import com.kuuhaku.controller.sqlite.GuildDAO;
import com.kuuhaku.model.GuildConfig;
import com.kuuhaku.utils.Settings;
import net.dv8tion.jda.api.entities.*;

public class SettingsCommand extends Command {

	public SettingsCommand() {
		super("settings", new String[]{"setting", "definições", "definiçoes", "definicões", "parametros", "parâmetros"}, "[<parâmetro> <novo valor do parâmetro>]", "Muda as configurações da Shiro no seu servidor.", Category.MODERACAO);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, String prefix) {
		GuildConfig gc = GuildDAO.getGuildById(guild.getId());

		if (args.length == 0) {
			Settings.embedConfig(message);
			return;
		}

		final String msg = String.join(" ", args).replace(args[0], "").replace(args[1], "").trim();

		switch (args[0].toLowerCase()) {
			case "prefix":
			case "prefixo":
				if (msg.length() > 5) {
					channel.sendMessage(":x: | Prefixo muito longo (Max. 5)").queue();
					return;
				}
				Settings.updatePrefix(args, message, gc);
				break;
			case "cbv":
			case "canalbv":
				Settings.updateCanalBV(args, message, gc);
				break;
			case "mensagembemvindo":
			case "mensagembv":
			case "msgbv":
				if (msg.length() > 2000) {
					channel.sendMessage(":x: | Mensagem muito longa (Max. 2000 caractéres)").queue();
					return;
				}
				Settings.updateMsgBV(args, message, gc);
				break;
			case "cadeus":
			case "canaladeus":
				Settings.updateCanalAdeus(args, message, gc);
				break;
			case "mensagemadeus":
			case "mensagema":
			case "msgadeus":
				if (msg.length() > 2000) {
					channel.sendMessage(":x: | Mensagem muito longa (Max. 2000 caractéres)").queue();
					return;
				}
				Settings.updateMsgAdeus(args, message, gc);
				break;
			case "twarn":
			case "tempowarn":
			case "tpun":
				Settings.updateWarnTime(args, message, gc);
				break;
			case "tpoll":
			case "tempopoll":
				Settings.updatePollTime(args, message, gc);
				break;
			case "csug":
			case "canalsug":
				Settings.updateCanalSUG(args, message, gc);
				break;
			case "rwarn":
			case "rolewarn":
				Settings.updateCargoWarn(args, message, gc);
				break;
			case "ln":
			case "levelnotif":
				Settings.updateLevelNotif(args, message, gc);
				break;
			case "canallevelup":
			case "canallvlup":
			case "clvlup":
				Settings.updateCanalLevelUp(args, message, gc);
				break;
			case "canalrelay":
			case "canalrly":
			case "crelay":
				Settings.updateCanalRelay(args, message, gc);
				break;
			case "clvl":
			case "cargolevel":
			case "cargolvl":
				Settings.updateCargoLvl(args, message, gc);
				break;
			case "mod":
			case "module":
			case "categoria":
			case "cat:":
				Settings.updateModules(args, message, gc);
				break;
			default:
				Settings.embedConfig(message);
		}
	}
}
