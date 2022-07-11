/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2019-2022  Yago Gimenez (KuuHaKu)
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

import com.kuuhaku.model.enums.shoukan.Side;
import com.kuuhaku.model.enums.shoukan.Trigger;
import com.kuuhaku.model.persistent.shoukan.Senshi;
import com.kuuhaku.util.Bit;

import java.util.Objects;

public class SlotColumn {
	private final Side side;
	private final int index;

	private Senshi top = null;
	private Senshi bottom = null;
	private byte state = 0;
	/*
	0xF F
      │ └ 0001
      │      └ permanent lock
      └─ (0 - 15) lock time
	 */

	public SlotColumn(Side side, int index) {
		this.side = side;
		this.index = index;
	}

	public Side getSide() {
		return side;
	}

	public int getIndex() {
		return index;
	}

	public Senshi getTop() {
		if (top != null && !equals(top.getSlot())) {
			top = null;
		}

		return top;
	}

	public boolean hasTop() {
		return getTop() != null;
	}

	public void setTop(Senshi top) {
		if (getTop() != null) {
			this.top.setSlot(null);
		}

		this.top = top;
		if (this.top != null) {
			this.top.setSlot(this);
			this.top.setSolid(true);

			if (!this.top.isFlipped()) {
				Hand h = this.top.getHand();
				h.getGame().trigger(Trigger.ON_SUMMON, this.top.asSource(Trigger.ON_SUMMON));
			}
		}
	}

	public Senshi getBottom() {
		if (bottom != null && !equals(bottom.getSlot())) {
			bottom = null;
		}

		return bottom;
	}

	public boolean hasBottom() {
		return getBottom() != null;
	}

	public void setBottom(Senshi bottom) {
		if (getBottom() != null) {
			this.bottom.setSlot(null);
		}

		this.bottom = bottom;
		if (this.bottom != null) {
			this.bottom.setSlot(this);
			this.bottom.setSolid(true);

			if (!this.bottom.isFlipped()) {
				Hand h = this.bottom.getHand();
				h.getGame().trigger(Trigger.ON_SUMMON, this.bottom.asSource(Trigger.ON_SUMMON));
			}
		}
	}

	public int getLock() {
		if (Bit.on(state, 0)) return -1;

		return Bit.get(state, 1, 4);
	}

	public boolean isLocked() {
		return Bit.on(state, 0) || Bit.on(state, 1, 4);
	}

	public void setLock(boolean value) {
		state = (byte) Bit.set(state, 0, value);
	}

	public void setLock(int time) {
		int curr = Bit.get(state, 1, 4);
		state = (byte) Bit.set(state, 1, Math.max(curr, time), 4);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SlotColumn that = (SlotColumn) o;
		return index == that.index && state == that.state && side == that.side && Objects.equals(top, that.top) && Objects.equals(bottom, that.bottom);
	}

	@Override
	public int hashCode() {
		return Objects.hash(side, index, top, bottom, state);
	}
}
