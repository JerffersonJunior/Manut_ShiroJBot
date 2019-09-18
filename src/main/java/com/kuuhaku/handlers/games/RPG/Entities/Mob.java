package com.kuuhaku.handlers.games.RPG.Entities;

import com.kuuhaku.handlers.games.RPG.Enums.Rarity;
import com.kuuhaku.handlers.games.RPG.Exceptions.BadLuckException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class Mob extends Character{
	private final List<LootItem> lootTable;
	private final int xp;

	public Mob(String name, String img, String bio, int strength, int perception, int endurance, int charisma, int intelligence, int agility, int luck, int xp) {
		super(name, img, bio, strength, perception, endurance, charisma, intelligence, agility, luck);
		this.lootTable = new ArrayList<>();
		this.xp = xp;
	}

	public Mob(String name, String img, String bio, int strength, int perception, int endurance, int charisma, int intelligence, int agility, int xp, int luck, List<LootItem> loot) {
		super(name, img, bio, strength, perception, endurance, charisma, intelligence, agility, luck);
		this.lootTable = loot;
		this.xp = xp;
	}

	public Mob(String[] ssvData, int xp) {
		super(ssvData);
		this.lootTable = new ArrayList<>();
		this.xp = xp;
	}

	@Override
	public RestAction openProfile(TextChannel channel) {
		EmbedBuilder eb = new EmbedBuilder();

		eb.setTitle(super.getName());
		eb.setThumbnail(super.getImage());
		eb.setDescription(super.getBio());
		eb.addField("Vida", String.valueOf(super.getStatus().getLife()), false);
		eb.addField("Defesa", String.valueOf(super.getStatus().getDefense()), true);
		eb.addField("Força", String.valueOf(super.getStatus().getStrength()), true);
		eb.addField("Percepção", String.valueOf(super.getStatus().getPerception()), true);
		eb.addField("Resistência", String.valueOf(super.getStatus().getEndurance()), true);
		eb.addField("Carisma", String.valueOf(super.getStatus().getCharisma()), true);
		eb.addField("Inteligência", String.valueOf(super.getStatus().getIntelligence()), true);
		eb.addField("Agilidade", String.valueOf(super.getStatus().getAgility()), true);
		eb.addField("Sorte", String.valueOf(super.getStatus().getLuck()), true);
		eb.addBlankField(false);
		eb.addField("XP", String.valueOf(xp), true);
		eb.addField("Drops", lootTable.stream().map(i -> "(" + i.getItem().getType().getName() + " " + i.getRarity().getName() + ") " +i.getItem().getName() + "\n").collect(Collectors.joining()), false);

		return channel.sendMessage(eb.build());
	}

	public void addLoot(LootItem item) {
		lootTable.add(item);
	}

	public void removeLoot(LootItem item) {
		lootTable.remove(item);
	}

	public List<LootItem> getLootTable() {
		return lootTable;
	}

	public Item dropLoot(int luck) {
		List<Item> filteredList = lootTable.stream().filter(i -> i.getRarity().equals(Rarity.roll(luck))).map(LootItem::getItem).collect(Collectors.toList());
		if (filteredList.size() == 0) throw new BadLuckException();
		return filteredList.get((int) Math.round(Math.random() * filteredList.size() - 1));
	}

	public int getXp() {
		return xp;
	}
}
