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

import com.kuuhaku.controller.DAO;

import java.util.Objects;

@SuppressWarnings("rawtypes")
public record EntityKey(Class<? extends DAO> klass, Object key) {
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		EntityKey entityKey = (EntityKey) o;
		return Objects.equals(key, entityKey.key) && Objects.equals(klass, entityKey.klass);
	}

	@Override
	public int hashCode() {
		return Objects.hash(klass, key);
	}
}
