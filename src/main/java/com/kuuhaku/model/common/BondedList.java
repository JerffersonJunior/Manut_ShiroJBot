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

import org.apache.commons.collections4.list.TreeList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class BondedList<T> extends TreeList<T> {
	private final BiFunction<T, ListIterator<T>, Boolean> condition;
	private final Consumer<T> onAdd;
	private final Consumer<T> onRemove;

	public static <T> BondedList<T> withBind(BiFunction<T, ListIterator<T>, Boolean> condition) {
		return new BondedList<>(condition);
	}

	public BondedList() {
		this((a, b) -> true, t -> {
		}, t -> {
		});
	}

	public BondedList(BiFunction<T, ListIterator<T>, Boolean> condition) {
		this(condition, t -> {
		}, t -> {
		});
	}

	public BondedList(Consumer<T> onRemove) {
		this((t, i) -> true, t -> {
		}, onRemove);
	}

	public BondedList(Consumer<T> onAdd, Consumer<T> onRemove) {
		this((t, i) -> true, onAdd, onRemove);
	}

	public BondedList(BiFunction<T, ListIterator<T>, Boolean> condition, Consumer<T> onRemove) {
		this(condition, t -> {}, onRemove);
	}

	public BondedList(BiFunction<T, ListIterator<T>, Boolean> condition, Consumer<T> onAdd, Consumer<T> onRemove) {
		this.condition = condition;
		this.onAdd = onAdd;
		this.onRemove = onRemove;
	}

	public BondedList(@NotNull Collection<? extends T> c, BiFunction<T, ListIterator<T>, Boolean> check) {
		this(c, check, t -> {
		}, t -> {
		});
	}

	public BondedList(@NotNull Collection<? extends T> c, BiFunction<T, ListIterator<T>, Boolean> condition, Consumer<T> onAdd, Consumer<T> onRemove) {
		this.condition = condition;
		this.onAdd = onAdd;
		this.onRemove = onRemove;
		addAll(c);
	}

	public BiFunction<T, ListIterator<T>, Boolean> getCondition() {
		return condition;
	}

	public Consumer<T> getOnRemove() {
		return onRemove;
	}

	public T getFirst() {
		return get(0);
	}

	public T getLast() {
		return get(size() - 1);
	}

	@Override
	public boolean add(T t) {
		return add(size(), t, new ArrayList<>());
	}

	@Override
	public void add(int index, T t) {
		add(index, t, new ArrayList<>());
	}

	public boolean add(int index, T t, List<T> aux) {
		if (t == null) return false;
		ListIterator<T> it = aux.listIterator();

		int size = aux.size();
		boolean ok = condition.apply(t, it);
		if (aux.size() != size) {
			addAll(index, aux);
			aux.clear();

			it = aux.listIterator();
		}

		if (ok) {
			it.add(t);
		}

		it = aux.listIterator();
		while (it.hasNext()) {
			T next = it.next();
			super.add(Math.min(index, size()), next);
			onAdd.accept(next);
			it.remove();
		}

		return ok;
	}

	public void addFirst(T t) {
		add(0, t);
	}

	public void addlast(T t) {
		add(size(), t);
	}

	@Override
	public boolean addAll(Collection<? extends T> c) {
		return addAll(size(), c);
	}

	@Override
	public boolean addAll(int index, Collection<? extends T> c) {
		List<T> aux = new ArrayList<>();

		int before = size();
		for (T t : List.copyOf(c)) {
			add(index++, t, aux);
		}

		return before != size();
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean remove(Object o) {
		if (super.contains(o)) {
			onRemove.accept((T) o);
		}

		return super.remove(o);
	}

	@Override
	public T remove(int index) {
		onRemove.accept(get(index));
		return super.remove(index);
	}

	public T removeFirst() {
		if (isEmpty()) return null;
		return remove(0);
	}

	public T removeFirst(Predicate<T> cond) {
		ListIterator<T> it = listIterator();
		while (it.hasNext()) {
			T t = it.next();
			if (cond.test(t)) {
				onRemove.accept(t);
				it.remove();

				return t;
			}
		}

		return null;
	}

	public T removeLast() {
		if (isEmpty()) return null;
		return remove(size() - 1);
	}

	public T removeLast(Predicate<T> cond) {
		ListIterator<T> it = listIterator(size());
		while (it.hasPrevious()) {
			T t = it.previous();
			if (cond.test(t)) {
				onRemove.accept(t);
				it.remove();

				return t;
			}
		}

		return null;
	}

	@Override
	public void clear() {
		for (T t : this) {
			onRemove.accept(t);
		}

		super.clear();
	}
}
