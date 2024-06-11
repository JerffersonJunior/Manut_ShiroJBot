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

package com.kuuhaku.model.persistent.id;

import com.kuuhaku.controller.DAO;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CustomAnswerId implements Serializable {
	@Serial
	private static final long serialVersionUID = 8010334365774039399L;

	@Column(name = "id", nullable = false)
	private int id;

	@Column(name = "gid", nullable = false)
	private String gid;

	public CustomAnswerId() {
	}

	public CustomAnswerId(String gid) {
		if (gid.isBlank()) throw new IllegalArgumentException("GID cannot be blank");
		DAO.applyNative(null, "CREATE SEQUENCE IF NOT EXISTS custom_answer_id_seq");

		this.id = DAO.queryNative(Integer.class, "SELECT nextval('custom_answer_id_seq')");
		this.gid = gid;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getGid() {
		return gid;
	}

	public void setGid(String gid) {
		this.gid = gid;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CustomAnswerId that = (CustomAnswerId) o;
		return id == that.id && Objects.equals(gid, that.gid);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, gid);
	}
}