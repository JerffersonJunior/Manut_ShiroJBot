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

package com.kuuhaku.command.commands.information;

import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import com.kuuhaku.model.common.Profile;
import com.kuuhaku.utils.Helper;
import net.dv8tion.jda.api.entities.*;

import java.io.IOException;

public class ProfileCommand extends Command {

	public ProfileCommand() {
		super("perfil", new String[]{"xp", "profile", "pf"}, "Mostra dados sobre você neste servidor.", Category.INFO);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, String prefix) {
		channel.sendMessage("<a:Loading:598500653215645697> Gerando perfil...").queue(m -> {
			try {
				channel.sendMessage(":video_game: Perfil de " + author.getAsMention()).addFile(Profile.makeProfile(member, guild).toByteArray(), "perfil.png").queue(s -> m.delete().queue());
			} catch (IOException e) {
				m.editMessage(":x: | Epa, teve um errinho aqui enquanto eu gerava o perfil, meus criadores já foram notificados!").queue();
				Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
			}
		});
	}
}
