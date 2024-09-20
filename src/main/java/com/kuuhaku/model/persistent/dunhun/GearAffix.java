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

package com.kuuhaku.model.persistent.dunhun;

import com.kuuhaku.controller.DAO;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.records.id.GearAffixId;
import com.kuuhaku.util.Utils;
import jakarta.persistence.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.Objects;

@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "gear_affix", schema = "dunhun")
public class GearAffix extends DAO<GearAffix> {
	@EmbeddedId
	private GearAffixId id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "gear_id", nullable = false, updatable = false)
	@Fetch(FetchMode.JOIN)
	@MapsId("gearId")
	private Gear gear;

	@ManyToOne(optional = false)
	@JoinColumn(name = "affix_id", nullable = false, updatable = false)
	@Fetch(FetchMode.JOIN)
	@MapsId("affixId")
	private Affix affix;

	@Column(name = "roll", nullable = false)
	private double roll;

	public GearAffixId getId() {
		return id;
	}

	public Gear getGear() {
		return gear;
	}

	public Affix getAffix() {
		return affix;
	}

	public double getRoll() {
		return roll;
	}

	public String getName(I18N locale) {
		String ending = gear.getBasetype().getInfo(locale).getEnding();

		return Utils.regex(affix.getInfo(locale).getName(), "\\[(?<F>\\w*)|(?<M>\\w*)]")
				.replaceAll(r -> r.group(ending));
	}

	public String getDescription(I18N locale) {
		return Utils.regex(affix.getInfo(locale).getName(), "\\{(\\d+)-(\\d+)}").replaceAll(r -> {
			int min = Integer.parseInt(r.group(1));
			int max = Integer.parseInt(r.group(2));

			return String.valueOf((int) (min + (max - min) * roll));
		});
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		GearAffix gearAffix = (GearAffix) o;
		return Objects.equals(id, gearAffix.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
