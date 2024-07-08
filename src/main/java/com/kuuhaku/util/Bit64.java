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

package com.kuuhaku.util;

import java.util.EnumSet;
import java.util.Set;

public abstract class Bit64 {
	public static long set(long bits, long index, boolean value) {
		return set(bits, index, Utils.toNum(value), 1);
	}

	public static long set(long bits, long index, long value, long size) {
		index *= size;
		if (!Utils.between(index, 0, Long.SIZE)) return bits;

		long mask = ((1L << size) - 1) << index;
		if (value < 0) value = 0;
		if (value > mask) value -= mask;

		return (bits & ~mask) | value << index;
	}

	public static boolean on(long bits, long index) {
		return get(bits, index, 1) > 0;
	}

	public static boolean on(long bits, long index, long size) {
		return get(bits, index, size) > 0;
	}

	public static long get(long bits, long index, long size) {
		index *= size;
		if (!Utils.between(index, 0, Long.SIZE)) return 0;

		long mask = (1L << size) - 1;
		return (bits >> index) & mask;
	}

	public static <T extends Enum<T>> Set<T> toEnumSet(Class<T> klass, long bits) {
		Set<T> out = EnumSet.noneOf(klass);
		T[] fields = klass.getEnumConstants();
		for (T field : fields) {
			if (bits == 0) return out;
			else if ((bits & 1) == 1) {
				out.add(field);
			}

			bits >>= 1;
		}

		return out;
	}
}