/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2019-2022  Yago Gimenez (KuuHaKu)
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

package com.kuuhaku.model.records.shoukan;

import com.kuuhaku.model.common.shoukan.Hand;
import com.kuuhaku.model.enums.shoukan.TargetType;
import com.kuuhaku.model.enums.shoukan.Trigger;
import com.kuuhaku.model.persistent.shoukan.Senshi;

import java.util.ArrayList;
import java.util.List;

public record Targeting(Hand hand, int allyPos, int enemyPos) {
	public Targeting(Hand hand, int allyPos, int enemyPos) {
		this.hand = hand;
		this.allyPos = Math.max(-1, allyPos - 1);
		this.enemyPos = Math.max(-1, enemyPos - 1);
	}

	public Senshi ally() {
		return allyPos == -1 ? null : hand.getGame()
				.getSlots(hand.getSide())
				.get(allyPos)
				.getUnblocked();
	}

	public Senshi enemy() {
		return enemyPos == -1 ? null : hand.getGame()
				.getSlots(hand.getSide().getOther())
				.get(enemyPos)
				.getUnblocked();
	}

	public boolean validate(TargetType type) {
		return switch (type) {
			case NONE -> true;
			case ALLY -> allyPos != -1;
			case ENEMY -> enemyPos != -1;
			case BOTH -> allyPos != -1 && enemyPos != -1;
		};
	}

	public Target[] targets(Trigger trigger) {
		List<Target> targets = new ArrayList<>();

		if (allyPos > -1) {
			Senshi ally = ally();
			if (ally != null) {
				targets.add(ally.asTarget(trigger));
			} else {
				targets.add(new Target(null, hand.getSide(), allyPos, trigger, TargetType.ALLY));
			}
		}

		if (enemyPos > -1) {
			Senshi enemy = enemy();
			if (enemy != null) {
				targets.add(enemy.asTarget(trigger));
			} else {
				targets.add(new Target(null, hand.getSide().getOther(), enemyPos, trigger, TargetType.ENEMY));
			}
		}

		return targets.toArray(Target[]::new);
	}
}
