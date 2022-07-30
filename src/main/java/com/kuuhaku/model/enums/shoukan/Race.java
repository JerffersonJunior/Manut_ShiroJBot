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

package com.kuuhaku.model.enums.shoukan;

import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.util.Graph;
import com.kuuhaku.util.IO;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public enum Race {
	// Pure races
	HUMAN(0x1),
	BEAST(0x2),
	MACHINE(0x4),
	DIVINITY(0x8),
	SPIRIT(0x10),
	UNDEAD(0x20),
	MYSTICAL(0x40),
	DEMON(0x80),

	// Semi-races
	WEREBEAST(HUMAN.flag | BEAST.flag),
	CYBORG(HUMAN.flag | MACHINE.flag),
	DEMIGOD(HUMAN.flag | DIVINITY.flag),
	ESPER(HUMAN.flag | SPIRIT.flag),
	LICH(HUMAN.flag | UNDEAD.flag),
	ELF(HUMAN.flag | MYSTICAL.flag),
	ONI(HUMAN.flag | DEMON.flag),
	WARBEAST(BEAST.flag | MACHINE.flag),
	PRIMAL(BEAST.flag | DIVINITY.flag),
	FAMILIAR(BEAST.flag | SPIRIT.flag),
	GHOUL(BEAST.flag | UNDEAD.flag),
	FEY(BEAST.flag | MYSTICAL.flag),
	FIEND(BEAST.flag | DEMON.flag),
	EX_MACHINA(MACHINE.flag | DIVINITY.flag),
	SHIKI(MACHINE.flag | SPIRIT.flag),
	VIRUS(MACHINE.flag | UNDEAD.flag),
	FABLED(MACHINE.flag | MYSTICAL.flag),
	POSSESSED(MACHINE.flag | DEMON.flag),
	HERALD(DIVINITY.flag | SPIRIT.flag),
	SHINIGAMI(DIVINITY.flag | UNDEAD.flag),
	ANGEL(DIVINITY.flag | MYSTICAL.flag),
	FALLEN(DIVINITY.flag | DEMON.flag),
	GHOST(SPIRIT.flag | UNDEAD.flag),
	PIXIE(SPIRIT.flag | MYSTICAL.flag),
	TORMENTED(SPIRIT.flag | DEMON.flag),
	REBORN(UNDEAD.flag | MYSTICAL.flag),
	SPAWN(UNDEAD.flag | DEMON.flag),
	IMP(MYSTICAL.flag | DEMON.flag),

	NONE(0);

	private final int flag;

	Race(int flag) {
		this.flag = flag;
	}

	public String getName(I18N locale) {
		return locale.get("race/" + name());
	}

	public String getMajor(I18N locale) {
		return locale.get("major/" + name());
	}

	public String getMinor(I18N locale) {
		return locale.get("minor/" + name());
	}

	public String getSynergy(I18N locale) {
		return locale.get("synergy/" + name());
	}

	public String getDescription(I18N locale) {
		return locale.get("race/" + name() + "_desc");
	}

	public boolean isRace(Race race) {
		return (this.flag & race.flag) == race.flag;
	}

	public Race fuse(Race with) {
		return getByFlag(this.flag | with.flag);
	}

	public Race[] split() {
		List<Race> races = new ArrayList<>();

		int bits = flag;
		int i = 1;
		while (bits > 0) {
			if ((bits & 1) == 1) {
				races.add(getByFlag(i));
			}
			i <<= 1;
			bits >>= 1;
		}

		return races.toArray(Race[]::new);
	}

	public BufferedImage getImage() {
		return IO.getResourceAsImage("shoukan/race/full/" + name() + ".png");
	}

	public BufferedImage getIcon() {
		return IO.getResourceAsImage("shoukan/race/icon/" + name() + ".png");
	}

	public Color getColor() {
		Race[] pures = split();
		if (pures.length == 1) {
			return Graph.getColor(getImage());
		} else {
			return Graph.mix(Graph.getColor(pures[0].getImage()), Graph.getColor(pures[1].getImage()));
		}
	}

	public static Race getByFlag(int flag) {
		for (Race r : values()) {
			if (r.flag == flag) return r;
		}

		return NONE;
	}
}
