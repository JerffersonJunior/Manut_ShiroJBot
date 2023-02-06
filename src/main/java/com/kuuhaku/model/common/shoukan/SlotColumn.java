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

import com.kuuhaku.game.Shoukan;
import com.kuuhaku.model.enums.shoukan.Flag;
import com.kuuhaku.model.enums.shoukan.Race;
import com.kuuhaku.model.enums.shoukan.Side;
import com.kuuhaku.model.enums.shoukan.Trigger;
import com.kuuhaku.model.persistent.shoukan.Senshi;
import com.kuuhaku.util.Bit;
import com.kuuhaku.util.Utils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SlotColumn {
	private final Shoukan game;
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

	public SlotColumn(Shoukan game, Side side, int index) {
		this.game = game;
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
		if (top != null && (isLocked() || !equals(top.getSlot()))) {
			top.executeAssert(Trigger.ON_REMOVE);

			if (isLocked() || !equals(top.getSlot())) {
				top = null;
			}
		}

		return top;
	}

	public boolean hasTop() {
		return getTop() != null;
	}

	public void setTop(Senshi top) {
		Senshi current = getTop();
		if (Objects.equals(top, current)) return;
		else if (top != null && top.getSide() == side && top.popFlag(Flag.NO_CONVERT)) {
			return;
		}

		if (current != null) {
			current.executeAssert(Trigger.ON_REMOVE);
			current.setSlot(null);
		}

		this.top = top;
		if (top != null) {
			Hand h = game.getHands().get(side);

			top.setSlot(this);
			top.setHand(h);
			top.executeAssert(Trigger.ON_INITIALIZE);

			if (!top.isFlipped()) {
				h.getGame().trigger(Trigger.ON_SUMMON, top.asSource(Trigger.ON_SUMMON));
			}

			if (h.getOrigin().synergy() == Race.DEMIGOD && top.isFusion()) {
				h.modMP(1);
			}
		}
	}

	public Senshi getBottom() {
		if (bottom != null && (isLocked() || !equals(bottom.getSlot()))) {
			bottom.executeAssert(Trigger.ON_REMOVE);

			if (isLocked() || !equals(bottom.getSlot())) {
				bottom = null;
			}
		}

		return bottom;
	}

	public boolean hasBottom() {
		return getBottom() != null;
	}

	public void setBottom(Senshi bottom) {
		Senshi current = getBottom();
		if (Objects.equals(bottom, current)) return;
		else if (bottom != null && bottom.getSide() == side && bottom.popFlag(Flag.NO_CONVERT)) {
			return;
		}

		if (current != null) {
			current.executeAssert(Trigger.ON_REMOVE);
			current.setSlot(null);
		}

		this.bottom = bottom;
		if (bottom != null) {
			Hand h = game.getHands().get(side);

			bottom.setSlot(this);
			bottom.setHand(h);
			bottom.getEquipments().clear();
			bottom.executeAssert(Trigger.ON_INITIALIZE);

			if (!bottom.isFlipped()) {
				h.getGame().trigger(Trigger.ON_SUMMON, bottom.asSource(Trigger.ON_SUMMON));
			}

			if (h.getOrigin().synergy() == Race.DEMIGOD && bottom.isFusion()) {
				h.modMP(1);
			}
		}
	}

	public List<Senshi> getCards() {
		return Arrays.asList(getTop(), getBottom());
	}

	public Senshi getAtRole(boolean support) {
		return support ? bottom : top;
	}

	public Senshi getUnblocked() {
		return Utils.getOr(top, bottom);
	}

	public void replace(Senshi self, Senshi with) {
		if (Objects.equals(self, getTop())) {
			setTop(with);
		} else if (Objects.equals(self, getBottom())) {
			setBottom(with);
		}
	}

	public void swap() {
		Senshi aux = bottom;
		bottom = top;
		top = aux;
	}

	public void swap(Senshi self, Senshi other) {
		if (self == null || other == null) return;

		boolean sup = other.isSupporting();
		SlotColumn sc = other.getSlot();

		other.setSlot(this);
		if (self.isSupporting()) {
			bottom = other;
		} else {
			top = other;
		}

		self.setSlot(sc);
		if (sup) {
			sc.bottom = self;
		} else {
			sc.top = self;
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

	public void reduceLock(int time) {
		int curr = Bit.get(state, 1, 4);
		state = (byte) Bit.set(state, 1, Math.max(0, curr - time), 4);
	}

	public SlotColumn getLeft() {
		if (index > 0) {
			return game.getArena().getSlots(side).get(index - 1);
		}

		return null;
	}

	public SlotColumn getRight() {
		List<SlotColumn> slts = game.getArena().getSlots(side);
		if (index < slts.size() - 1) {
			return slts.get(index + 1);
		}

		return null;
	}

	public byte getState() {
		return state;
	}

	public void setState(byte state) {
		this.state = state;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SlotColumn that = (SlotColumn) o;
		return side == that.side && index == that.index;
	}

	@Override
	public int hashCode() {
		return Objects.hash(side, index);
	}
}
