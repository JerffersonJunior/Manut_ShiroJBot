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

package com.kuuhaku.model.persistent.shoukan;

import com.kuuhaku.controller.DAO;
import com.kuuhaku.model.persistent.converter.JSONArrayConverter;
import com.kuuhaku.model.persistent.converter.JSONObjectConverter;
import com.kuuhaku.model.records.shoukan.history.Info;
import com.kuuhaku.model.records.shoukan.history.Match;
import com.kuuhaku.model.records.shoukan.history.Turn;
import com.ygimenez.json.JSONArray;
import com.ygimenez.json.JSONObject;
import com.ygimenez.json.JSONUtils;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "match_history")
public class MatchHistory extends DAO<Field> {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private int id;

	@Column(name = "head", nullable = false, columnDefinition = "JSONB")
	@Convert(converter = JSONObjectConverter.class)
	private JSONObject head;

	@Column(name = "data", nullable = false, columnDefinition = "JSONB")
	@Convert(converter = JSONArrayConverter.class)
	private JSONArray data;

	public MatchHistory() {
	}

	public MatchHistory(Match match) {
		this.head = new JSONObject(match.info());
		this.data = new JSONArray(match.turns());
	}

	public Info getHead() {
		return JSONUtils.fromJSON(head.toString(), Info.class);
	}

	public List<Turn> getData() {
		List<Turn> out = new ArrayList<>();
		for (Object turn : data) {
			out.add(JSONUtils.fromJSON(String.valueOf(turn), Turn.class));
		}

		return out;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		MatchHistory that = (MatchHistory) o;
		return id == that.id;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
