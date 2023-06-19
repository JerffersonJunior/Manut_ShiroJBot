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

package com.kuuhaku.model.common.shoukan;

import com.kuuhaku.controller.DAO;
import com.kuuhaku.interfaces.shoukan.Drawable;
import com.kuuhaku.model.common.BondedList;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.enums.shoukan.Flag;
import com.kuuhaku.model.enums.shoukan.Race;
import com.kuuhaku.model.persistent.id.LocalizedId;
import com.kuuhaku.model.persistent.shiro.Card;
import com.kuuhaku.model.persistent.shoukan.LocalizedDescription;
import com.kuuhaku.model.persistent.shoukan.Senshi;
import com.kuuhaku.util.Calc;
import com.kuuhaku.model.common.Copier;
import com.kuuhaku.util.Utils;
import com.ygimenez.json.JSONObject;
import org.apache.commons.collections4.set.ListOrderedSet;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;

public class CardExtra implements Cloneable {
	private final HashSet<ValueMod> mana;
	private final HashSet<ValueMod> blood;
	private final HashSet<ValueMod> sacrifices;

	private final HashSet<ValueMod> atk;
	private final HashSet<ValueMod> dfs;

	private final HashSet<ValueMod> dodge;
	private final HashSet<ValueMod> block;

	private final HashSet<ValueMod> costMult;
	private final HashSet<ValueMod> attrMult;
	private final HashSet<ValueMod> power;

	private final HashSet<ValueMod> tier;

	private final EnumSet<Flag> flags;
	private final EnumSet<Flag> tempFlags;
	private final EnumSet<Flag> permFlags;

	private final JSONObject data;
	private final JSONObject perm;
	private final ListOrderedSet<String> curses;

	private Race race = null;
	private Card vanity = null;
	private Senshi disguise = null;

	private String write = null;

	private Drawable<?> source = null;
	private String description = null;
	private String effect = null;

	private transient Field[] fieldCache = null;

	public CardExtra(
			HashSet<ValueMod> mana, HashSet<ValueMod> blood, HashSet<ValueMod> sacrifices,
			HashSet<ValueMod> atk, HashSet<ValueMod> dfs, HashSet<ValueMod> dodge,
			HashSet<ValueMod> block, HashSet<ValueMod> costMult, HashSet<ValueMod> attrMult, HashSet<ValueMod> tier,
			HashSet<ValueMod> power, EnumSet<Flag> flags, EnumSet<Flag> tempFlags,
			EnumSet<Flag> permFlags, JSONObject data, JSONObject perm,
			ListOrderedSet<String> curses
	) {
		this.mana = mana;
		this.blood = blood;
		this.sacrifices = sacrifices;
		this.atk = atk;
		this.dfs = dfs;
		this.dodge = dodge;
		this.block = block;
		this.costMult = costMult;
		this.attrMult = attrMult;
		this.power = power;
		this.tier = tier;
		this.flags = flags;
		this.tempFlags = tempFlags;
		this.permFlags = permFlags;
		this.data = data;
		this.perm = perm;
		this.curses = curses;
	}

	public CardExtra() {
		this(
				new HashSet<>(),
				new HashSet<>(),
				new HashSet<>(),
				new HashSet<>(),
				new HashSet<>(),
				new HashSet<>(),
				new HashSet<>(),
				new HashSet<>(),
				new HashSet<>(),
				new HashSet<>(),
				new HashSet<>(),
				EnumSet.noneOf(Flag.class),
				EnumSet.noneOf(Flag.class),
				EnumSet.noneOf(Flag.class),
				new JSONObject(),
				new JSONObject(),
				ListOrderedSet.listOrderedSet(BondedList.withBind((s, it) -> !s.isBlank()))
		);
	}

	public int getMana() {
		return Calc.round(sum(mana));
	}

	public ValueMod getMana(Drawable<?> source) {
		for (ValueMod mod : mana) {
			if (Objects.equals(source, mod.getSource())) {
				return mod;
			}
		}

		return new ValueMod(source, 0);
	}

	public void setMana(int mana) {
		for (ValueMod mod : this.mana) {
			if (mod instanceof PermMod) {
				mod.setValue(mod.getValue() + mana);
				return;
			}
		}

		this.mana.add(new PermMod(mana));
	}

	public void setMana(Drawable<?> source, int mana) {
		ValueMod mod = new ValueMod(source, mana);
		this.mana.remove(mod);
		this.mana.add(mod);
	}

	public void setMana(Drawable<?> source, int mana, int expiration) {
		ValueMod mod = new ValueMod(source, mana, expiration);
		this.mana.remove(mod);
		this.mana.add(mod);
	}

	public int getBlood() {
		return Calc.round(sum(blood));
	}

	public ValueMod getBlood(Drawable<?> source) {
		for (ValueMod mod : blood) {
			if (Objects.equals(source, mod.getSource())) {
				return mod;
			}
		}

		return new ValueMod(source, 0);
	}

	public void setBlood(int blood) {
		for (ValueMod mod : this.blood) {
			if (mod instanceof PermMod) {
				mod.setValue(mod.getValue() + blood);
				return;
			}
		}

		this.blood.add(new PermMod(blood));
	}

	public void setBlood(Drawable<?> source, int blood) {
		ValueMod mod = new ValueMod(source, blood);
		this.blood.remove(mod);
		this.blood.add(mod);
	}

	public void setBlood(Drawable<?> source, int blood, int expiration) {
		ValueMod mod = new ValueMod(source, blood, expiration);
		this.blood.remove(mod);
		this.blood.add(mod);
	}

	public int getSacrifices() {
		return Calc.round(sum(sacrifices));
	}

	public ValueMod getSacrifices(Drawable<?> source) {
		for (ValueMod mod : sacrifices) {
			if (Objects.equals(source, mod.getSource())) {
				return mod;
			}
		}

		return new ValueMod(source, 0);
	}

	public void setSacrifices(int sacrifices) {
		for (ValueMod mod : this.sacrifices) {
			if (mod instanceof PermMod) {
				mod.setValue(mod.getValue() + sacrifices);
				return;
			}
		}

		this.sacrifices.add(new PermMod(sacrifices));
	}

	public void setSacrifices(Drawable<?> source, int sacrifices) {
		ValueMod mod = new ValueMod(source, sacrifices);
		this.sacrifices.remove(mod);
		this.sacrifices.add(mod);
	}

	public void setSacrifices(Drawable<?> source, int sacrifices, int expiration) {
		ValueMod mod = new ValueMod(source, sacrifices, expiration);
		this.sacrifices.remove(mod);
		this.sacrifices.add(mod);
	}

	public int getAtk() {
		return Calc.round(sum(atk));
	}

	public ValueMod getAtk(Drawable<?> source) {
		for (ValueMod mod : atk) {
			if (Objects.equals(source, mod.getSource())) {
				return mod;
			}
		}

		return new ValueMod(source, 0);
	}

	public void setAtk(int atk) {
		for (ValueMod mod : this.atk) {
			if (mod instanceof PermMod) {
				mod.setValue(mod.getValue() + atk);
				return;
			}
		}

		this.atk.add(new PermMod(atk));
	}

	public void setAtk(Drawable<?> source, int atk) {
		ValueMod mod = new ValueMod(source, atk);
		this.atk.remove(mod);
		this.atk.add(mod);
	}

	public void setAtk(Drawable<?> source, int atk, int expiration) {
		ValueMod mod = new ValueMod(source, atk, expiration);
		this.atk.remove(mod);
		this.atk.add(mod);
	}

	public int getDfs() {
		return Calc.round(sum(dfs));
	}

	public ValueMod getDfs(Drawable<?> source) {
		for (ValueMod mod : dfs) {
			if (Objects.equals(source, mod.getSource())) {
				return mod;
			}
		}

		return new ValueMod(source, 0);
	}

	public void setDfs(int dfs) {
		for (ValueMod mod : this.dfs) {
			if (mod instanceof PermMod) {
				mod.setValue(mod.getValue() + dfs);
				return;
			}
		}

		this.dfs.add(new PermMod(dfs));
	}

	public void setDfs(Drawable<?> source, int dfs) {
		ValueMod mod = new ValueMod(source, dfs);
		this.dfs.remove(mod);
		this.dfs.add(mod);
	}

	public void setDfs(Drawable<?> source, int dfs, int expiration) {
		ValueMod mod = new ValueMod(source, dfs, expiration);
		this.dfs.remove(mod);
		this.dfs.add(mod);
	}

	public int getDodge() {
		return Calc.round(sum(dodge));
	}

	public ValueMod getDodge(Drawable<?> source) {
		for (ValueMod mod : dodge) {
			if (Objects.equals(source, mod.getSource())) {
				return mod;
			}
		}

		return new ValueMod(source, 0);
	}

	public void setDodge(int dodge) {
		for (ValueMod mod : this.dodge) {
			if (mod instanceof PermMod) {
				mod.setValue(mod.getValue() + dodge);
				return;
			}
		}

		this.dodge.add(new PermMod(dodge));
	}

	public void setDodge(Drawable<?> source, int dodge) {
		ValueMod mod = new ValueMod(source, dodge);
		this.dodge.remove(mod);
		this.dodge.add(mod);
	}

	public void setDodge(Drawable<?> source, int dodge, int expiration) {
		ValueMod mod = new ValueMod(source, dodge, expiration);
		this.dodge.remove(mod);
		this.dodge.add(mod);
	}

	public int getBlock() {
		return Calc.round(sum(block));
	}

	public ValueMod getBlock(Drawable<?> source) {
		for (ValueMod mod : block) {
			if (Objects.equals(source, mod.getSource())) {
				return mod;
			}
		}

		return new ValueMod(source, 0);
	}

	public void setBlock(int block) {
		for (ValueMod mod : this.block) {
			if (mod instanceof PermMod) {
				mod.setValue(mod.getValue() + block);
				return;
			}
		}

		this.block.add(new PermMod(block));
	}

	public void setBlock(Drawable<?> source, int block) {
		ValueMod mod = new ValueMod(source, block);
		this.block.remove(mod);
		this.block.add(mod);
	}

	public void setBlock(Drawable<?> source, int block, int expiration) {
		ValueMod mod = new ValueMod(source, block, expiration);
		this.block.remove(mod);
		this.block.add(mod);
	}

	public double getCostMult() {
		return 1 + sum(costMult);
	}

	public ValueMod getCostMult(Drawable<?> source) {
		for (ValueMod mod : costMult) {
			if (Objects.equals(source, mod.getSource())) {
				return mod;
			}
		}

		return new ValueMod(source, 0);
	}

	public void setCostMult(double costMult) {
		for (ValueMod mod : this.costMult) {
			if (mod instanceof PermMod) {
				mod.setValue(mod.getValue() + costMult);
				return;
			}
		}

		this.costMult.add(new PermMod(costMult));
	}

	public void setCostMult(Drawable<?> source, double costMult) {
		ValueMod mod = new ValueMod(source, costMult);
		this.costMult.remove(mod);
		this.costMult.add(mod);
	}

	public void setCostMult(Drawable<?> source, double costMult, int expiration) {
		ValueMod mod = new ValueMod(source, costMult, expiration);
		this.costMult.remove(mod);
		this.costMult.add(mod);
	}

	public double getAttrMult() {
		return 1 + sum(attrMult);
	}

	public ValueMod getAttrMult(Drawable<?> source) {
		for (ValueMod mod : attrMult) {
			if (Objects.equals(source, mod.getSource())) {
				return mod;
			}
		}

		return new ValueMod(source, 0);
	}

	public void setAttrMult(double attrMult) {
		for (ValueMod mod : this.attrMult) {
			if (mod instanceof PermMod) {
				mod.setValue(mod.getValue() + attrMult);
				return;
			}
		}

		this.attrMult.add(new PermMod(attrMult));
	}

	public void setAttrMult(Drawable<?> source, double attrMult) {
		ValueMod mod = new ValueMod(source, attrMult);
		this.attrMult.remove(mod);
		this.attrMult.add(mod);
	}

	public void setAttrMult(Drawable<?> source, double attrMult, int expiration) {
		ValueMod mod = new ValueMod(source, attrMult, expiration);
		this.attrMult.remove(mod);
		this.attrMult.add(mod);
	}

	public double getPower() {
		return (1 + sum(power)) * (hasFlag(Flag.EMPOWERED) ? 1.5 : 1);
	}

	public ValueMod getPower(Drawable<?> source) {
		for (ValueMod mod : power) {
			if (Objects.equals(source, mod.getSource())) {
				return mod;
			}
		}

		return new ValueMod(source, 0);
	}

	public void setPower(double power) {
		for (ValueMod mod : this.power) {
			if (mod instanceof PermMod) {
				mod.setValue(mod.getValue() + power);
				return;
			}
		}

		this.power.add(new PermMod(power));
	}

	public void setPower(Drawable<?> source, double power) {
		ValueMod mod = new ValueMod(source, power);
		this.power.remove(mod);
		this.power.add(mod);
	}

	public void setPower(Drawable<?> source, double power, int expiration) {
		ValueMod mod = new ValueMod(source, power, expiration);
		this.power.remove(mod);
		this.power.add(mod);
	}

	public int getTier() {
		return Calc.round(sum(tier));
	}

	public ValueMod getTier(Drawable<?> source) {
		for (ValueMod mod : tier) {
			if (Objects.equals(source, mod.getSource())) {
				return mod;
			}
		}

		return new ValueMod(source, 0);
	}

	public void setTier(int tier) {
		for (ValueMod mod : this.tier) {
			if (mod instanceof PermMod) {
				mod.setValue(mod.getValue() + tier);
				return;
			}
		}

		this.tier.add(new PermMod(tier));
	}

	public void setTier(Drawable<?> source, int tier) {
		ValueMod mod = new ValueMod(source, tier);
		this.tier.remove(mod);
		this.tier.add(mod);
	}

	public void setTier(Drawable<?> source, int tier, int expiration) {
		ValueMod mod = new ValueMod(source, tier, expiration);
		this.tier.remove(mod);
		this.tier.add(mod);
	}

	public void setTFlag(Flag flag, boolean value) {
		if (hasFlag(flag) == value) return;

		tempFlags.add(flag);
	}

	public void clearTFlags() {
		tempFlags.clear();
	}

	public void setFlag(Flag flag, boolean value) {
		setFlag(flag, value, false);
	}

	public void setFlag(Flag flag, boolean value, boolean permanent) {
		if (hasFlag(flag) == value) return;

		if (value) {
			(permanent ? permFlags : flags).add(flag);
		} else {
			(permanent ? permFlags : flags).remove(flag);
		}
	}

	public boolean hasFlag(Flag flag) {
		return tempFlags.contains(flag) || flags.contains(flag) || permFlags.contains(flag);
	}

	public boolean popFlag(Flag flag) {
		return tempFlags.remove(flag) || flags.remove(flag) || permFlags.contains(flag);
	}

	public JSONObject getData() {
		return data;
	}

	public JSONObject getPerm() {
		return perm;
	}

	public ListOrderedSet<String> getCurses() {
		return curses;
	}

	public Race getRace() {
		return race;
	}

	public void setRace(Race race) {
		this.race = race;
	}

	public Card getVanity() {
		return vanity;
	}

	public void setVanity(Card vanity) {
		this.vanity = vanity;
	}

	public Senshi getDisguise() {
		return disguise;
	}

	public void setDisguise(Senshi disguise) {
		this.disguise = disguise;
	}

	public String getWrite() {
		return Utils.getOr(write, "");
	}

	public void setWrite(String write) {
		this.write = write;
	}

	public Drawable<?> getSource() {
		return source;
	}

	public void setSource(Drawable<?> source) {
		this.source = source;
	}

	public String getDescription(I18N locale) {
		if (description == null || description.isBlank() || description.contains(" ")) return description;

		LocalizedDescription desc = DAO.find(LocalizedDescription.class, new LocalizedId(description, locale));
		return desc == null ? description : desc.getDescription();
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

	public void expireMods() {
		Predicate<ValueMod> check = mod -> {
			if (mod.getExpiration() > 0) {
				mod.decExpiration();
			}

			return mod.isExpired();
		};

		removeExpired(check);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public void removeExpired(Predicate<ValueMod> check) {
		if (fieldCache == null) {
			fieldCache = getClass().getDeclaredFields();
		}

		for (Field f : fieldCache) {
			try {
				if (f.get(this) instanceof HashSet s) {
					s.removeIf(check);
				}
			} catch (IllegalAccessException ignore) {
			}
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public void clear() {
		if (fieldCache == null) {
			fieldCache = getClass().getDeclaredFields();
		}

		for (Field f : fieldCache) {
			try {
				if (f.get(this) instanceof HashSet s) {
					s.removeIf(o -> !(o instanceof PermMod));
				}
			} catch (IllegalAccessException ignore) {
			}
		}
	}

	private double sum(Set<ValueMod> mods) {
		double out = 0;
		for (ValueMod mod : mods) {
			out += mod.getValue();
		}

		return Calc.round(out, 2);
	}

	@Override
	@SuppressWarnings("rawtypes")
	public CardExtra clone() {
		Copier<HashSet, ValueMod> copier = new Copier<>(HashSet.class, ValueMod.class);

		CardExtra clone = new CardExtra(
				copier.makeCopy(mana),
				copier.makeCopy(blood),
				copier.makeCopy(sacrifices),
				copier.makeCopy(atk),
				copier.makeCopy(dfs),
				copier.makeCopy(dodge),
				copier.makeCopy(block),
				copier.makeCopy(attrMult),
				copier.makeCopy(costMult),
				copier.makeCopy(power),
				copier.makeCopy(tier),
				EnumSet.noneOf(Flag.class),
				EnumSet.noneOf(Flag.class),
				EnumSet.copyOf(permFlags),
				data.clone(),
				perm.clone(),
				ListOrderedSet.listOrderedSet(BondedList.withBind((s, it) -> !s.isBlank()))
		);

		clone.clear();

		clone.race = race;
		clone.source = source;
		clone.description = description;
		clone.effect = effect;

		return clone;
	}
}