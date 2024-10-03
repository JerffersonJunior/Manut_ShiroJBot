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

package com.kuuhaku.model.common.dunhun;

public class MonsterModifiers {
	private int attack;
	private float attackMult = 1;
	private int defense;
	private float defenseMult = 1;
	private int maxHp;
	private float hpMult = 1;
	private int maxAp;
	private int dodge;
	private int parry;
	private int initiative;

	public float getAttack() {
		return attack;
	}

	public void addAttack(int value) {
		attack += value;
	}

	public float getAttackMult() {
		return attackMult;
	}

	public void addAttackMult(float mult) {
		attackMult += mult;
	}

	public float getDefense() {
		return defense;
	}

	public void addDefense(int value) {
		defense += value;
	}

	public float getDefenseMult() {
		return defenseMult;
	}

	public void addDefenseMult(float mult) {
		defenseMult += mult;
	}

	public int getMaxHp() {
		return maxHp;
	}

	public void addMaxHp(int value) {
		maxHp += value;
	}

	public float getHpMult() {
		return hpMult;
	}

	public void addHpMult(float mult) {
		hpMult += mult;
	}

	public int getMaxAp() {
		return maxAp;
	}

	public void addMaxAp(int value) {
		maxAp += value;
	}

	public int getDodge() {
		return dodge;
	}

	public void addDodge(int value) {
		dodge += value;
	}

	public int getParry() {
		return parry;
	}

	public void addParry(int value) {
		parry += value;
	}

	public int getInitiative() {
		return initiative;
	}

	public void addInitiative(int value) {
		initiative += value;
	}

	public void reset() {
		attack = defense = maxHp = maxAp = dodge = parry = initiative = 0;
		attackMult = defenseMult = hpMult = 1;
	}
}
