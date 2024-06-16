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

import com.kuuhaku.interfaces.shoukan.EffectHolder;
import com.kuuhaku.model.enums.shoukan.Side;
import com.kuuhaku.model.enums.shoukan.Trigger;
import com.kuuhaku.model.records.shoukan.EffectParameters;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

public class TriggerBind {
	private final EffectHolder<?> holder;
	private final EnumMap<Side, EnumSet<Trigger>> binds;
	private final boolean permanent;

	public TriggerBind(EffectHolder<?> holder, EnumMap<Side, EnumSet<Trigger>> binds) {
		this(holder, binds, false);
	}

	public TriggerBind(EffectHolder<?> holder, EnumSet<Trigger> binds) {
		this(holder, new EnumMap<>(Map.of(Side.TOP, binds, Side.BOTTOM, binds)), false);
	}

	public TriggerBind(EffectHolder<?> holder, EnumSet<Trigger> binds, boolean permanent) {
		this.holder = holder;
		this.binds = new EnumMap<>(Map.of(Side.TOP, binds, Side.BOTTOM, binds));
		this.permanent = permanent;
	}

	public TriggerBind(EffectHolder<?> holder, EnumMap<Side, EnumSet<Trigger>> binds, boolean permanent) {
		this.holder = holder;
		this.binds = binds;
		this.permanent = permanent;
	}

	public EffectHolder<?> getHolder() {
		return holder;
	}

	public boolean isBound(EffectParameters ep) {
		return binds.containsKey(ep.side()) && binds.get(ep.side()).contains(ep.trigger());
	}

	public boolean isPermanent() {
		return permanent;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TriggerBind that = (TriggerBind) o;
		return Objects.equals(holder, that.holder);
	}

	@Override
	public int hashCode() {
		return holder.hashCode();
	}
}
