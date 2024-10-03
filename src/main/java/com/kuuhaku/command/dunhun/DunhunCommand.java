/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2019-2023  Yago Gimenez (KuuHaKu)
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

package com.kuuhaku.command.dunhun;

import com.kuuhaku.Constants;
import com.kuuhaku.exceptions.PendingConfirmationException;
import com.kuuhaku.game.Dunhun;
import com.kuuhaku.game.engine.GameInstance;
import com.kuuhaku.game.engine.GameReport;
import com.kuuhaku.interfaces.Executable;
import com.kuuhaku.interfaces.annotations.Command;
import com.kuuhaku.interfaces.annotations.Requires;
import com.kuuhaku.interfaces.annotations.Syntax;
import com.kuuhaku.model.enums.Category;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.persistent.dunhun.Dungeon;
import com.kuuhaku.model.records.EventData;
import com.kuuhaku.model.records.MessageData;
import com.kuuhaku.util.Utils;
import com.ygimenez.json.JSONObject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;

@Command(
		name = "dunhun",
		category = Category.FUN
)
@Syntax(value = {
		"<user:user:r> <dungeon:word:r>",
		"<dungeon:word:r>"
})
@Requires(Permission.MESSAGE_ATTACH_FILES)
public class DunhunCommand implements Executable {
	@Override
	public void execute(JDA bot, I18N locale, EventData data, MessageData.Guild event, JSONObject args) {
		if (GameInstance.PLAYERS.contains(event.user().getId())) {
			event.channel().sendMessage(locale.get("error/in_game_self")).queue();
			return;
		}

		User other;
		if (args.has("user")) {
			other = event.users(0);
			if (other == null) {
				event.channel().sendMessage(locale.get("error/invalid_mention")).queue();
				return;
			}
		} else {
			other = event.user();
		}

		if (GameInstance.PLAYERS.contains(other.getId())) {
			event.channel().sendMessage(locale.get("error/in_game_target", other.getEffectiveName())).queue();
			return;
		}

		try {
			if (other.equals(event.user())) {
				Dunhun dun = new Dunhun(locale, new Dungeon(), event.user());
				dun.start(event.guild(), event.channel())
						.whenComplete((v, e) -> {
							if (e instanceof GameReport rep && rep.getCode() == GameReport.INITIALIZATION_ERROR) {
								event.channel().sendMessage(locale.get("error/error", e)).queue();
								Constants.LOGGER.error(e, e);
							}
						});
				return;
			}

			Utils.confirm(locale.get("question/dunhun", other.getAsMention(), event.user().getAsMention()), event.channel(), w -> {
						Dunhun dun = new Dunhun(locale, new Dungeon(), event.user(), other);
						dun.start(event.guild(), event.channel())
								.whenComplete((v, e) -> {
									if (e instanceof GameReport rep && rep.getCode() == GameReport.INITIALIZATION_ERROR) {
										event.channel().sendMessage(locale.get("error/error", e)).queue();
										Constants.LOGGER.error(e, e);
									}
								});

						return true;
					}, other
			);
		} catch (PendingConfirmationException e) {
			event.channel().sendMessage(locale.get("error/pending_confirmation")).queue();
		}
	}
}
