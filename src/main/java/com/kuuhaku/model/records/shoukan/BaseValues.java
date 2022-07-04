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

package com.kuuhaku.model.records.shoukan;

import kotlin.Triple;

import java.util.concurrent.Callable;
import java.util.function.Function;

public record BaseValues(int hp, Function<Integer, Integer> mpGain, int handCapacity) {
	public BaseValues() {
		this(5000, t -> 5, 5);
	}

	public BaseValues(Callable<Triple<Integer, Function<Integer, Integer>, Integer>> values) throws Exception {
		this(values.call());
	}

	public BaseValues(Triple<Integer, Function<Integer, Integer>, Integer> values) {
		this(values.getFirst(), values.getSecond(), values.getThird());
	}
}
