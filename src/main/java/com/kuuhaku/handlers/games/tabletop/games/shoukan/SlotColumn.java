/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2021  Yago Gimenez (KuuHaKu)
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

package com.kuuhaku.handlers.games.tabletop.games.shoukan;

public class SlotColumn<A, B> {
	private A top = null;
	private B bottom = null;
	private final int index;

	public SlotColumn(int index) {
		this.index = index;
	}

	public SlotColumn(A top, B bottom, int index) {
		this.top = top;
		this.bottom = bottom;
		this.index = index;
	}

	public A getTop() {
		return top;
	}

	public void setTop(A top) {
		this.top = top;
	}

	public B getBottom() {
		return bottom;
	}

	public void setBottom(B bottom) {
		this.bottom = bottom;
	}

	public int getIndex() {
		return index;
	}
}
