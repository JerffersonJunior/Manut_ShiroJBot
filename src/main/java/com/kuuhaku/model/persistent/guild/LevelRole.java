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

package com.kuuhaku.model.persistent.guild;

import com.kuuhaku.controller.DAO;
import com.kuuhaku.model.persistent.converter.RoleConverter;
import com.kuuhaku.model.persistent.javatype.RoleJavaType;
import com.kuuhaku.model.persistent.id.LevelRoleId;
import jakarta.persistence.*;
import net.dv8tion.jda.api.entities.Role;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JavaType;

@Entity
@Table(name = "color_role")
public class LevelRole extends DAO<LevelRole> {
	@EmbeddedId
	private LevelRoleId id;

	@Column(name = "level", nullable = false)
	private int level;

	@Column(name = "role", nullable = false)
	@Convert(converter = RoleConverter.class)
	@JavaType(RoleJavaType.class)
	private Role role;

	@ManyToOne(optional = false)
	@JoinColumn(name = "gid", nullable = false)
	@Fetch(FetchMode.JOIN)
	@MapsId("gid")
	private GuildSettings settings;

	public LevelRole() {
	}

	public LevelRole(GuildSettings settings, int level, Role role) {
		this.id = new LevelRoleId(role.getGuild().getId());
		this.level = level;
		this.role = role;
		this.settings = settings;
	}

	public LevelRoleId getId() {
		return id;
	}

	public int getLevel() {
		return level;
	}

	public Role getRole() {
		return role;
	}

	public GuildSettings getSettings() {
		return settings;
	}
}
