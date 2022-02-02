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

package com.kuuhaku.model.persistent.guild.buttons;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "buttonmessage")
public class ButtonMessage {
	@Id
	@Column(columnDefinition = "VARCHAR(255) NOT NULL")
	private String id;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(nullable = false, name = "parent_id")
	private ButtonChannel parent;

	@Column(columnDefinition = "VARCHAR(255) NOT NULL")
	private String author;

	@Column(columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
	private boolean gatekeeper = false;

	@Column(columnDefinition = "VARCHAR(255)")
	private String role;

	@OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "parent")
	private List<Button> buttons = new ArrayList<>();

	public ButtonMessage() {
	}

	public ButtonMessage(String id, String author, boolean gatekeeper, String role) {
		this.id = id;
		this.author = author;
		this.gatekeeper = gatekeeper;
		this.role = role;
	}

	public String getId() {
		return id;
	}

	public ButtonChannel getParent() {
		return parent;
	}

	public void setParent(ButtonChannel parent) {
		this.parent = parent;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public boolean isGatekeeper() {
		return gatekeeper;
	}

	public void setGatekeeper(boolean gatekeeper) {
		this.gatekeeper = gatekeeper;
	}

	public Role getRole(Guild g) {
		return g.getRoleById(role);
	}

	public void setRole(Role role) {
		this.role = role.getId();
	}

	public List<Button> getButtons() {
		return buttons;
	}

	public void addButton(Button btn) {
		btn.setParent(this);
		buttons.add(btn);
	}

	public void removeButton(Button btn) {
		buttons.remove(btn);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ButtonMessage that = (ButtonMessage) o;
		return gatekeeper == that.gatekeeper && Objects.equals(id, that.id) && Objects.equals(author, that.author) && Objects.equals(role, that.role);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, author, gatekeeper, role);
	}
}
