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

package com.kuuhaku.command.misc;

import com.kuuhaku.controller.DAO;
import com.kuuhaku.interfaces.Executable;
import com.kuuhaku.interfaces.annotations.Command;
import com.kuuhaku.interfaces.annotations.Seasonal;
import com.kuuhaku.interfaces.annotations.Syntax;
import com.kuuhaku.model.enums.Category;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.persistent.user.Account;
import com.kuuhaku.model.records.EventData;
import com.kuuhaku.model.records.MessageData;
import com.kuuhaku.util.Calc;
import com.ygimenez.json.JSONObject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;

import java.time.LocalDate;
import java.util.Calendar;

@Command(
		name = "steal",
		category = Category.MISC
)
@Seasonal(months = Calendar.OCTOBER)
@Syntax("<user:user:r>")
public class StealCommand implements Executable {
	@Override
	public void execute(JDA bot, I18N locale, EventData data, MessageData.Guild event, JSONObject args) {
		User target = event.users(0);
		if (target == null) {
			event.channel().sendMessage(locale.get("error/invalid_mention")).queue();
			return;
		} else if (target.equals(event.user())) {
			event.channel().sendMessage(locale.get("error/self_not_allowed")).queue();
			return;
		}

		Account acc = data.profile().getAccount();

		LocalDate now = LocalDate.now();
		LocalDate last = LocalDate.parse(acc.getDynValue("last_steal", LocalDate.ofEpochDay(0).toString()));
		if (!last.isBefore(now)) {
			event.channel().sendMessage(locale.get("error/stole_recent")).queue();
			return;
		}

		int current = acc.getItemCount("spooky_candy");
		if (Calc.chance(Math.min(Math.pow(current / 100d, 2), 70))) {
			acc.consumeItem("SPOOKY_CANDY", current, true);
			acc.setDynValue("last_steal", now.toString());

			event.channel().sendMessage(locale.get("str/steal_caught")).queue();
			return;
		}

		Account them = DAO.find(Account.class, target.getId());
		int total = them.getItemCount("spooky_candy");
		int stolen = Calc.rng(total / 5, total / 3);

		if (stolen > 0 && them.consumeItem("spooky_candy", stolen)) {
			acc.addItem("spooky_candy", stolen);
			acc.setDynValue("last_steal", now.toString());

			event.channel().sendMessage(locale.get("success/candy_stolen", stolen, target.getAsMention())).queue();
		} else {
			event.channel().sendMessage(locale.get("error/could_not_steal")).queue();
		}
	}
}