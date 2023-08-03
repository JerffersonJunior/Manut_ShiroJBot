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

package com.kuuhaku.model.common;

import com.kuuhaku.Constants;
import com.kuuhaku.util.Calc;
import com.kuuhaku.util.Utils;
import kotlin.Pair;
import org.apache.commons.collections4.list.TreeList;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Curve bias:
 * <br>
 * <br>
 * lowㅤㅤㅤㅤㅤㅤㅤhigh
 * <br>
 * <----------|---------->
 * <br>
 * -Nㅤㅤㅤㅤ0ㅤㅤㅤㅤN
 * <br>
 * <br>
 * Values <0 tend towards higher weights, values >0 tend towards lower weights
 **/
public class RandomList<T> {
	private final NavigableMap<Double, T> map = new TreeMap<>();
	private final List<Pair<Double, T>> pool = new TreeList<>();
	private final Supplier<Double> randGen;
	private final double mult;
	private double skew = 0.5;
	private double total = 0;

	public RandomList() {
		this(0);
	}

	public RandomList(RandomGenerator rng) {
		this(rng, 0);
	}

	public RandomList(double mult) {
		this(Constants.DEFAULT_RNG.get(), mult);
	}

	public RandomList(Supplier<Double> randGen) {
		this(randGen, 0);
	}

	public RandomList(RandomGenerator rng, double mult) {
		this.mult = mult;
		this.randGen = () -> Calc.clamp(rng.nextGaussian(skew, 0.15), 0, 1);
	}

	public RandomList(Supplier<Double> randGen, double mult) {
		this.mult = mult;
		this.randGen = randGen;
	}

	public void add(@NotNull T item) {
		add(item, 1);
	}

	public void add(@NotNull T item, double weight) {
		if (weight <= 0) return;

		total = 0;
		map.clear();
		pool.add(new Pair<>(weight, item));
	}

	public T get() {
		if (pool.isEmpty()) return null;
		else if (map.isEmpty()) {
			pool.sort(Comparator.<Pair<Double, T>>comparingDouble(Pair::getFirst).reversed());
			double min = pool.get(pool.size() - 1).getFirst();
			double max = pool.get(0).getFirst();

			for (Pair<Double, T> p : pool) {
				double weight = p.getFirst();
				double fac = 1 - Calc.offsetPrcnt(weight, max, min);
				double mult = Math.pow(1 + fac / 2, this.mult);

				System.out.println(weight * mult);
				map.put(total += (weight * mult), p.getSecond());
			}
		}

		return map.ceilingEntry(randGen.get() * total).getValue();
	}

	public T remove() {
		T t = get();
		if (t == null) return null;

		remove(t);
		return t;
	}

	public void remove(@NotNull T item) {
		Iterator<Map.Entry<Double, T>> it = map.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Double, T> entry = it.next();
			if (entry.getValue().equals(item)) {
				total -= entry.getKey();
				it.remove();
				break;
			}
		}
	}

	public Collection<T> values() {
		return map.values();
	}

	public List<Pair<Double, T>> entries() {
		return pool;
	}

	public void skew(double skew) {
		if (!Utils.between(skew, 0, 1)) throw new IllegalArgumentException();

		this.skew = skew;
	}
}
