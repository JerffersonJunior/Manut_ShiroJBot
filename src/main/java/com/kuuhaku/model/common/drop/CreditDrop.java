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

package com.kuuhaku.model.common.drop;

import com.kuuhaku.model.enums.DailyTask;
import com.kuuhaku.model.persistent.Account;
import com.kuuhaku.utils.helpers.MathHelper;
import com.kuuhaku.utils.helpers.StringHelper;
import net.dv8tion.jda.api.entities.User;

import java.util.Map;

public class CreditDrop extends Drop<Integer> {
	public CreditDrop() {
		super(MathHelper.rng(300, 800));
	}

	@Override
	public void award(User u) {
		Account acc = Account.find(Account.class, u.getId());
		acc.addCredit(getPrize(), this.getClass());

		if (acc.hasPendingQuest()) {
			Map<DailyTask, Integer> pg = acc.getDailyProgress();
			pg.merge(DailyTask.DROP_TASK, 1, Integer::sum);
			acc.setDailyProgress(pg);
		}

		acc.save();
	}

	@Override
	public String toString() {
		return StringHelper.separate(getPrize()) + " CR";
	}

	@Override
	public String toString(User u) {
		return toString();
	}
}
