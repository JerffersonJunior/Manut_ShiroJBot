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

package com.kuuhaku.model.enums;

import com.kuuhaku.controller.postgresql.AccountDAO;
import com.kuuhaku.controller.postgresql.CardDAO;
import com.kuuhaku.controller.postgresql.KawaiponDAO;
import com.kuuhaku.controller.postgresql.StashDAO;
import com.kuuhaku.handlers.games.tabletop.games.shoukan.Equipment;
import com.kuuhaku.handlers.games.tabletop.games.shoukan.Hero;
import com.kuuhaku.model.persistent.Account;
import com.kuuhaku.model.persistent.Stash;
import com.kuuhaku.utils.Helper;
import org.apache.commons.lang3.StringUtils;

import java.util.function.BiFunction;

public enum Reward {
	XP("XP", (h, v) -> {
		int r = Helper.rng(v);

		h.addXp(r);
		KawaiponDAO.saveHero(h);

		return r;
	}),
	CREDIT("Créditos", (h, v) -> {
		int r = Helper.rng(v);

		Account acc = AccountDAO.getAccount(h.getUid());
		acc.addCredit(Helper.rng(v), Reward.class);
		AccountDAO.saveAccount(acc);

		return r;
	}),
	GEM("Gemas", (h, v) -> {
		int r = Helper.rng(v);

		Account acc = AccountDAO.getAccount(h.getUid());
		acc.addGem(Helper.rng(v));
		AccountDAO.saveAccount(acc);

		return r;
	}),
	EQUIPMENT("Evogear", (h, v) -> {
		String r = "Nenhum";

		if (Helper.chance(v)) {
			Equipment e = CardDAO.getRandomEquipment();
			assert e != null;
			StashDAO.saveCard(new Stash(h.getUid(), e));

			r = e.getCard().getName() + " (" + StringUtils.repeat("\uD83D\uDFCA", e.getTier()) + ")";
		}

		return r;
	});

	private final String name;
	private final BiFunction<Hero, Integer, Object> evt;

	Reward(String name, BiFunction<Hero, Integer, Object> evt) {
		this.name = name;
		this.evt = evt;
	}

	public Object reward(Hero h, int value) {
		return evt.apply(h, value);
	}

	@Override
	public String toString() {
		return name;
	}
}
