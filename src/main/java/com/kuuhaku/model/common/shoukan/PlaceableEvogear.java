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
import com.kuuhaku.model.enums.shoukan.Race;
import com.kuuhaku.model.persistent.shoukan.Evogear;
import com.kuuhaku.model.persistent.shoukan.Senshi;

import java.util.Objects;

public class PlaceableEvogear extends Senshi implements Proxy<Evogear> {
	private final Evogear original;

	public PlaceableEvogear(Evogear e) {
		super(e.getId(), e.getCard(), e.isSpell() ? Race.MYSTICAL : Race.MACHINE, e.getBase(), e.getStats(), e.getStashRef());

		original = e;
		setHand(e.getHand());
	}

	@Override
	public Evogear getOriginal() {
		return original;
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
		PlaceableEvogear placeableEvo = (PlaceableEvogear) o;
		return Objects.equals(original, placeableEvo.original);
	}

	@Override
	public int hashCode() {
		return original.hashCode();
	}
}
