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

package com.kuuhaku.model.records;

import com.kuuhaku.model.persistent.user.Account;
import org.apache.commons.lang3.function.TriFunction;

import java.util.Random;
import java.util.function.Function;

public record DropCondition(String key, Function<Random, Object[]> extractor, TriFunction<Random, Object[], Account, Boolean> condition) {
	public DropCondition(String key, TriFunction<Random, Object[], Account, Boolean> condition) {
		this(key, (rng) -> new Object[0], condition);
	}
}
