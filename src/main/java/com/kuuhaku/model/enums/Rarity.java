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

package com.kuuhaku.model.enums;

import com.kuuhaku.controller.DAO;
import com.kuuhaku.util.Graph;

import java.awt.*;
import java.util.Arrays;

public enum Rarity {
	COMMON(1, 0xFFFFFF, "<:common:726171819664736268>"),
	UNCOMMON(2, 0x03BB85, "<:uncommon:726171819400232962>"),
	RARE(3, 0x70D1F4, "<:rare:726171819853480007>"),
	ULTRA_RARE(4, 0x9966CC, "<:ultra_rare:726171819786240091>"),
	LEGENDARY(5, 0xDC9018, "<:legendary:726171819945623682>"),
	ULTIMATE(-1, 0xD400AA, "<:ultimate:1002748864643743774>"),
	EVOGEAR,
	FIELD,
	FUSION,
	NONE;

	private final int index;
	private final int color;
	private final String emote;

	Rarity(int index, int color, String emote) {
		this.index = index;
		this.color = color;
		this.emote = emote;
	}

	Rarity() {
		this.index = -1;
		this.color = 0;
		this.emote = "";
	}

	public int getIndex() {
		return index;
	}

	public Color getColor(boolean chrome) {
		Color color = new Color(this.color);
		if (chrome) {
			color = Graph.rotate(color, 180);
		}

		return color;
	}

	public String getEmote() {
		return emote;
	}

	public int getCount() {
		return DAO.queryNative(Integer.class, "SELECT COUNT(1) FROM card WHERE rarity = ?1", name());
	}

	public static Rarity[] getActualRarities() {
		return Arrays.stream(values())
				.filter(r -> r.getIndex() > 0)
				.toArray(Rarity[]::new);
	}
}
