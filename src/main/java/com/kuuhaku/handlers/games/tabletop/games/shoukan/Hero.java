/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2019-2021  Yago Gimenez (KuuHaKu)
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

package com.kuuhaku.handlers.games.tabletop.games.shoukan;

import com.kuuhaku.handlers.games.tabletop.games.shoukan.enums.Perk;
import com.kuuhaku.handlers.games.tabletop.games.shoukan.enums.Race;
import com.kuuhaku.model.enums.KawaiponRarity;
import com.kuuhaku.model.persistent.AddedAnime;
import com.kuuhaku.model.persistent.Attributes;
import com.kuuhaku.model.persistent.Card;
import com.kuuhaku.utils.Helper;
import net.dv8tion.jda.api.entities.User;

import javax.persistence.*;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "hero")
public class Hero {
	@Id
	@Column(columnDefinition = "VARCHAR(255) NOT NULL")
	private String uid;

	@Column(columnDefinition = "VARCHAR(25) NOT NULL")
	private String name;

	@Column(columnDefinition = "TEXT")
	private String image = null;

	@Embedded
	private Attributes attrs = new Attributes();

	@Enumerated(EnumType.STRING)
	private Race race;

	@Column(columnDefinition = "INT NOT NULL DEFAULT 0")
	private int dmg = 0;

	@Column(columnDefinition = "INT NOT NULL DEFAULT 0")
	private int xp = 0;

	@Column(columnDefinition = "VARCHAR(140) NOT NULL DEFAULT ''")
	private String description;

	@Column(columnDefinition = "TEXT")
	private String effect = "";

	@ElementCollection(fetch = FetchType.EAGER)
	@Enumerated(EnumType.STRING)
	@JoinColumn(name = "hero_id")
	private Set<Perk> perks = EnumSet.noneOf(Perk.class);

	private transient int hp = -1;

	public Hero() {
	}

	public Hero(User user, String name, Race race) {
		this.uid = user.getId();
		this.name = name;
		this.race = race;
		this.description = "Lendário herói " + race.toString().toLowerCase(Locale.ROOT) + " invocado por " + user.getName();
	}

	public String getUid() {
		return uid;
	}

	public String getName() {
		return name;
	}

	public BufferedImage getImage() {
		return image == null ? null : Helper.btoa(image);
	}

	public void setImage(BufferedImage image) {
		this.image = Helper.atob(Helper.scaleAndCenterImage(image, 225, 350), "png");
	}

	public Attributes getAttrs() {
		return attrs;
	}

	public Race getRace() {
		return race;
	}

	public void setDmg(int hp) {
		this.dmg = attrs.calcMaxHp() - hp;
	}

	public void reduceDmg() {
		this.dmg = (int) Math.max(0, this.dmg - attrs.calcMaxHp() * 0.1);
	}

	public void reduceDmg(int val) {
		this.dmg = Math.max(0, this.dmg - val);
	}

	public int getLevel() {
		return (int) Math.round(Math.log(xp * Math.sqrt(5)) / Math.log(Helper.GOLDEN_RATIO)) - 1;
	}

	public int getXp() {
		return xp;
	}

	public void addXp(int xp) {
		this.xp += xp;
	}

	public int getXpToNext() {
		int level = getLevel();
		if (level < 20) return (int) Helper.getFibonacci(level + 2);
		else return -1;
	}

	public int getMaxStatPoints() {
		return getLevel() * 10;
	}

	public int getAvailableStatPoints() {
		return getMaxStatPoints() - attrs.getUsedPoints();
	}

	public Set<Perk> getPerks() {
		return perks;
	}

	public int getMaxPerks() {
		return getLevel() / 5;
	}

	public int getAvailablePerks() {
		return getMaxPerks() - perks.size();
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getEffect() {
		return effect;
	}

	public void setEffect(String effect) {
		this.effect = effect;
	}

	public int getHp() {
		double hpModif = 1;
		for (Perk perk : perks) {
			hpModif *= switch (perk) {
				case VANGUARD -> 1.33;
				case NIMBLE -> 0.75;
				case MASOCHIST -> 0.5;
				default -> 1;
			};
		}

		return hp = (int) Math.max(0, Helper.roundTrunc(attrs.calcMaxHp() * hpModif, 5) - dmg);
	}

	public int getMp() {
		double mpModif = 1;
		for (Perk perk : perks) {
			mpModif *= switch (perk) {
				case BLOODLUST -> 0.5;
				case MANALESS -> 0;
				default -> 1;
			};
		}

		return (int) Math.max(perks.contains(Perk.MANALESS) ? 0 : 1, attrs.calcMp() * mpModif);
	}

	public int getBlood() {
		int blood = 0;
		for (Perk perk : perks) {
			blood += switch (perk) {
				case BLOODLUST -> attrs.calcMp() / 2 * 100;
				default -> 0;
			};
		}

		return Math.max(0, blood);
	}

	public int getAtk() {
		double atkModif = 1;
		for (Perk perk : perks) {
			atkModif *= switch (perk) {
				case VANGUARD -> 0.75;
				case CARELESS -> 1.25;
				case MANALESS -> 0.5;
				case MASOCHIST -> 1 + Math.min(Helper.prcnt(dmg, getHp()), 1);
				default -> 1;
			};
		}

		return (int) Math.max(0, Helper.roundTrunc(attrs.calcAtk() * atkModif, 25));
	}

	public int getDef() {
		double defModif = 1;
		for (Perk perk : perks) {
			defModif *= switch (perk) {
				case VANGUARD -> 1.15;
				case CARELESS -> 0.66;
				case MANALESS -> 0.5;
				default -> 1;
			};
		}

		return (int) Math.max(0, Helper.roundTrunc(attrs.calcDef() * defModif, 25));
	}

	public Champion toChampion() {
		Champion c = new Champion(
				new Card(uid, name, new AddedAnime("HERO", true), KawaiponRarity.ULTIMATE, image),
				race, getMp(), getBlood(), getAtk(), getDef(), description, effect
		);
		c.setHero(this);

		return c;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Hero hero = (Hero) o;
		return Objects.equals(uid, hero.uid) && race == hero.race;
	}

	@Override
	public int hashCode() {
		return Objects.hash(uid, race);
	}
}
