package com.kuuhaku.handlers.games.RPG.Entities;

import com.kuuhaku.handlers.games.RPG.Enums.Rarity;
import com.kuuhaku.handlers.games.RPG.Exceptions.BadLuckException;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class Chest {
	private final String name;
	private final List<LootItem> lootTable;

	public Chest(String name, List<LootItem> loot) {
		this.name = name;
		this.lootTable = loot;
	}

	public String getName() {
		return name;
	}

	public Item dropLoot(int luck) {
		List<Item> filteredList = lootTable.stream().filter(i -> i.getRarity().equals(Rarity.roll(luck))).map(LootItem::getItem).collect(Collectors.toList());
		if (filteredList.size() == 0) throw new BadLuckException();
		return filteredList.get((int) Math.round(Math.random() * filteredList.size() - 1));
	}
}
