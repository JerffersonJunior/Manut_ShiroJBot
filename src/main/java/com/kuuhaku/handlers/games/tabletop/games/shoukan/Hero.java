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

import com.kuuhaku.controller.postgresql.AccountDAO;
import com.kuuhaku.controller.postgresql.CardDAO;
import com.kuuhaku.handlers.games.tabletop.games.shoukan.enums.Perk;
import com.kuuhaku.handlers.games.tabletop.games.shoukan.enums.Race;
import com.kuuhaku.model.enums.KawaiponRarity;
import com.kuuhaku.model.persistent.AddedAnime;
import com.kuuhaku.model.persistent.Attributes;
import com.kuuhaku.model.persistent.Card;
import com.kuuhaku.model.persistent.id.CompositeHeroId;
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
@IdClass(CompositeHeroId.class)
public class Hero implements Cloneable {
	@Id
	@Column(columnDefinition = "INT NOT NULL DEFAULT 0")
	private int id;

	@Id
	@Column(columnDefinition = "VARCHAR(255) NOT NULL")
	private String uid;

	@Column(columnDefinition = "VARCHAR(25) NOT NULL")
	private String name;

	@Column(columnDefinition = "TEXT")
	private String image = null;

	@Embedded
	private Attributes stats;

	@Enumerated(EnumType.STRING)
	private Race race;

	@Column(columnDefinition = "INT NOT NULL DEFAULT 0")
	private int dmg = 0;

	@Column(columnDefinition = "INT NOT NULL DEFAULT 0")
	private int xp = 0;

	@Column(columnDefinition = "INT NOT NULL DEFAULT 0")
	private int effect = 0;

	@Column(columnDefinition = "INT NOT NULL DEFAULT 0")
	private int bonusPoints = 0;

	@ElementCollection(fetch = FetchType.EAGER)
	@Enumerated(EnumType.STRING)
	@JoinColumn(name = "hero_id")
	private Set<Perk> perks = EnumSet.noneOf(Perk.class);

	@Column(columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
	private boolean returned = false;

	private transient int hp = -1;

	public Hero() {
	}

	public Hero(User user, String name, Race race, BufferedImage image) {
		this.id = CardDAO.getHeroes(user.getId()).size() + 1;
		this.uid = user.getId();
		this.name = name;
		this.stats = new Attributes(race.getStartingStats());
		this.race = race;
		this.image = Helper.atob(Helper.scaleAndCenterImage(Helper.removeAlpha(image), 225, 350), "jpg");
	}

	public int getId() {
		return id;
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
		this.image = Helper.atob(Helper.scaleAndCenterImage(Helper.removeAlpha(image), 225, 350), "jpg");
	}

	public Attributes getStats() {
		return stats;
	}

	public void resetStats() {
		stats = new Attributes(race.getStartingStats());
	}

	public Race getRace() {
		return race;
	}

	public void setDmg() {
		this.dmg = getMaxHp() - hp;
	}

	public void reduceDmg() {
		double healModif = 1;
		for (Perk perk : perks) {
			healModif *= switch (perk) {
				case OPTIMISTIC -> 1.5;
				case PESSIMISTIC -> 0.5;
				default -> 1;
			};
		}

		this.dmg = (int) Math.max(0, this.dmg - getMaxHp() * 0.1 * healModif);
	}

	public void reduceDmg(int val) {
		this.dmg = Math.max(0, this.dmg - val);
	}

	public int getLevel() {
		return Math.max(1, (int) Math.round(Math.log(xp * Math.sqrt(5)) / Math.log(Helper.GOLDEN_RATIO)) - 1);
	}

	public int getXp() {
		return xp;
	}

	public void addXp(int xp) {
		this.xp += xp;
	}

	public void setXp(int xp) {
		this.xp = xp;
	}

	public int getXpToNext() {
		int level = getLevel();
		if (level < 20) return (int) Helper.getFibonacci(level + 2);
		else return -1;
	}

	public int getBonusPoints() {
		return bonusPoints;
	}

	public void addBonusPoints(int bonusPoints) {
		this.bonusPoints += bonusPoints;
	}

	public int getMaxStatPoints() {
		return 5 + getLevel() * 5 + bonusPoints;
	}

	public int getAvailableStatPoints() {
		return getMaxStatPoints() - stats.getUsedPoints();
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
		Champion ref = CardDAO.getChampion(effect);

		return ref == null ? "Lendário herói " + race.toString().toLowerCase(Locale.ROOT) + " invocado por " + Helper.getUsername(uid) : ref.getDescription();
	}

	public void setReferenceChampion(int id) {
		this.effect = id;
	}

	public Champion getReferenceChampion() {
		return CardDAO.getChampion(effect);
	}

	public int getDmg() {
		if (dmg > getMaxHp()) dmg = getMaxHp();
		return dmg;
	}

	public int getMaxHp() {
		return stats.calcMaxHp(perks);
	}

	public int getHp() {
		if (hp == -1) hp = Math.max(0, getMaxHp() - dmg);
		return hp;
	}

	public void setHp(int hp) {
		this.hp = Math.max(0, hp);
	}

	public int getMp() {
		double mpModif = 1;
		for (Perk perk : perks) {
			mpModif *= switch (perk) {
				case BLOODLUST -> 0.5;
				case MANALESS -> 0;
				case MINDSHIELD -> 2;
				default -> 1;
			};
		}

		Champion ref = getReferenceChampion();
		int refMp = ref == null ? 0 : ref.getMana() / 2;
		return (int) Math.ceil(Math.max(perks.contains(Perk.MANALESS) ? 0 : 1, (stats.calcMp() + refMp) * mpModif));
	}

	public int getBlood() {
		int blood = 0;
		for (Perk perk : perks) {
			blood += switch (perk) {
				case BLOODLUST -> stats.calcMp() / 2 * 100;
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
				case MASOCHIST -> 1 + Math.min(Helper.prcnt(getDmg(), getMaxHp()) / 2, 0.5);
				default -> 1;
			};
		}

		return (int) Math.max(0, Helper.roundTrunc(stats.calcAtk() * atkModif, 25));
	}

	public int getDef() {
		double defModif = 1;
		for (Perk perk : perks) {
			defModif *= switch (perk) {
				case VANGUARD -> 1.15;
				case CARELESS -> 0.66;
				case MANALESS -> 0.5;
				case MASOCHIST -> 1 - Math.min(Helper.prcnt(getDmg(), getMaxHp()) / 2, 0.5);
				case ARMORED -> 1 + stats.calcDodge() * 0.01;
				default -> 1;
			};
		}

		return (int) Math.max(0, Helper.roundTrunc(stats.calcDef() * defModif, 25));
	}

	public int getDodge() {
		double ddgModif = 1;
		for (Perk perk : perks) {
			ddgModif *= switch (perk) {
				case NIMBLE -> 1.25;
				case ARMORED -> 0;
				case MANALESS -> 0.5;
				default -> 1;
			};
		}

		return (int) Math.round(stats.calcDodge() * ddgModif);
	}

	public Champion toChampion() {
		Champion ref = CardDAO.getChampion(effect);
		Champion c = new Champion(
				new Card(uid, name, new AddedAnime("HERO", true), KawaiponRarity.ULTIMATE, image),
				race, getMp(), getBlood(), getAtk(), getDef(), getDescription(), ref == null ? null : ref.getRawEffect()
		);
		c.setAcc(AccountDAO.getAccount(uid));
		c.setHero(this);

		return c;
	}

	public boolean isReturned() {
		return returned;
	}

	public void setReturned(boolean returned) {
		this.returned = returned;
	}

	@Override
	public Hero clone() {
		try {
			Hero h = (Hero) super.clone();
			h.stats = new Attributes(stats.getStats());
			return h;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
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
