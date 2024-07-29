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

package com.kuuhaku.model.common.shoukan;

import com.kuuhaku.interfaces.shoukan.Proxy;
import com.kuuhaku.model.common.BondedList;
import com.kuuhaku.model.enums.shoukan.TargetType;
import com.kuuhaku.model.persistent.shoukan.Evogear;
import com.kuuhaku.model.persistent.shoukan.Senshi;
import com.ygimenez.json.JSONArray;

import java.util.Objects;

public class EquippableSenshi extends Evogear implements Proxy<Senshi> {
	private final Senshi original;

	public EquippableSenshi(Senshi s) {
		super(s.getId(), s.getCard(), 0, false, TargetType.NONE, new JSONArray(), s.getBase(), s.getStats(), s.getStashRef());

		original = s;
		setHand(s.getHand());
	}

	@Override
	public Senshi getOriginal() {
		return original;
	}

	@Override
	public CardExtra getStats() {
		return original.getStats();
	}

	@Override
	public BondedList<?> getCurrentStack() {
		return original.getCurrentStack();
	}

	@Override
	public void setCurrentStack(BondedList<?> stack) {
		original.setCurrentStack(stack);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;
		EquippableSenshi equippableSenshi = (EquippableSenshi) o;
		return Objects.equals(original, equippableSenshi.original);
	}

	@Override
	public int hashCode() {
		return original.hashCode();
	}
}
