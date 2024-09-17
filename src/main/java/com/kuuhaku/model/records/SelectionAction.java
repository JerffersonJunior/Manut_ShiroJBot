/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2019-2024  Yago Gimenez (KuuHaKu)
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

import com.kuuhaku.interfaces.shoukan.Drawable;
import com.kuuhaku.model.common.SupplyChain;
import com.kuuhaku.model.records.shoukan.SelectionRange;

import java.util.List;

public record SelectionAction(
		Drawable<?> source,
		String caption,
		List<SelectionCard> cards,
		SelectionRange range,
		List<Integer> indexes,
		SupplyChain<List<Drawable<?>>> result
) {
}
