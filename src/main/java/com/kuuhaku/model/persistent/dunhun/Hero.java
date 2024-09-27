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

import com.kuuhaku.Constants;
import com.kuuhaku.controller.DAO;
import com.kuuhaku.model.common.dunhun.Equipment;
import com.kuuhaku.model.common.dunhun.HeroModifiers;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.enums.shoukan.Race;
import com.kuuhaku.model.persistent.converter.JSONArrayConverter;
import com.kuuhaku.model.persistent.converter.JSONObjectConverter;
import com.kuuhaku.model.persistent.shoukan.CardAttributes;
import com.kuuhaku.model.persistent.shoukan.Senshi;
import com.kuuhaku.model.persistent.user.Account;
import com.kuuhaku.model.records.Attributes;
import com.kuuhaku.util.Graph;
import com.ygimenez.json.JSONArray;
import com.ygimenez.json.JSONObject;
import jakarta.persistence.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.text.WordUtils;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Entity
@Table(name = "hero", schema = "dunhun")
public class Hero extends DAO<Hero> {
	@Id
	@Column(name = "id", nullable = false)
	private String id;

	@Column(name = "hp", nullable = false)
	private int hp;

	@Embedded
	private HeroStats stats = new HeroStats();

	@ManyToOne(optional = false)
	@PrimaryKeyJoinColumn(name = "account_uid")
	@Fetch(FetchMode.JOIN)
	private Account account;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "equipment", nullable = false, columnDefinition = "JSONB")
	@Convert(converter = JSONObjectConverter.class)
	private JSONObject equipment = new JSONObject();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "skills", nullable = false, columnDefinition = "JSONB")
	@Convert(converter = JSONArrayConverter.class)
	private JSONArray skills = new JSONArray();

	private transient final HeroModifiers modifiers = new HeroModifiers();
	private transient Equipment equipCache;
	private transient List<Skill> skillCache;

	public Hero() {
	}

	public Hero(Account account, String name, Race race) {
		this.id = name.toUpperCase();
		this.account = account;
		stats.setRace(race);
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return WordUtils.capitalizeFully(id.replace("_", " "));
	}

	public boolean setImage(BufferedImage img) {
		String hash = HexFormat.of().formatHex(DigestUtils.getMd5Digest().digest(("H:" + id).getBytes()));
		File parent = new File(System.getenv("CARDS_PATH") + "../heroes");
		if (!parent.exists()) parent.mkdir();

		File f = new File(parent, hash + ".png");
		img = Graph.scaleAndCenterImage(Graph.toColorSpace(img, BufferedImage.TYPE_INT_ARGB), 225, 350);

		try {
			ImageIO.write(img, "png", f);
			return true;
		} catch (IOException e) {
			Constants.LOGGER.error(e, e);
			return false;
		}
	}

	public int getHp() {
		return hp;
	}

	public int getMaxHp() {
		return (int) ((hp + modifiers.getMaxHp()) * (1 + 0.1 * getAttributes().vit()));
	}

	public void setHp(int hp) {
		this.hp = hp;
	}

	public Attributes getAttributes() {
		return getStats().getAttributes().merge(getModifiers().getAttributes());
	}

	public HeroStats getStats() {
		return stats;
	}

	public HeroModifiers getModifiers() {
		return modifiers;
	}

	public Account getAccount() {
		return account;
	}

	public Equipment getEquipment() {
		if (equipCache != null) return equipCache;

		List<Integer> ids = new ArrayList<>();
		for (Object o : equipment.values()) {
			if (o instanceof Number n) {
				ids.add(n.intValue());
			} else if (o instanceof Collection<?> c) {
				for (Object n : c) {
					ids.add(((Number) n).intValue());
				}
			}
		}

		Map<Integer, Gear> gear = DAO.queryAll(Gear.class, "SELECT g FROM Gear g WHERE g.id IN ?1", ids)
				.parallelStream()
				.collect(Collectors.toMap(Gear::getId, Function.identity()));

		return equipCache = new Equipment((gs, i) -> {
			if (i < 0) {
				return gear.get(equipment.getInt(gs.name()));
			}

			return gear.get(equipment.getJSONArray(gs.name()).getInt(i));
		});
	}

	public List<Gear> getInventory() {
		List<Integer> ids = DAO.queryAllNative(Integer.class, """
				SELECT g.id
				FROM gear g
				INNER JOIN hero h ON h.id = g.owner_id
				WHERE h.id = ?1
				  AND NOT jsonb_path_exists(h.equipment, '$.* ? (@ == $val)', cast('{"val": ' || g.id || '}' AS JSONB))
				""", id);

		return DAO.queryAll(Gear.class, "SELECT g FROM Gear g WHERE g.id IN ?1", ids);
	}

	public List<Skill> getSkills() {
		if (skillCache != null) return skillCache;

		return skillCache = DAO.queryAll(Skill.class, "SELECT s FROM Skill s WHERE s.id IN ?1", skills);
	}

	public Senshi asSenshi(I18N locale) {
		Senshi s = new Senshi(id, stats.getRace());
		CardAttributes base = s.getBase();

		int dmg = 100;
		int def = 100;
		for (Gear g : getEquipment()) {
			if (g == null) continue;

			g.load(locale, this, s);
			dmg += g.getDmg();
			def += g.getDfs();
		}

		Attributes a = getAttributes();
		base.setAtk((int) (dmg * (1 + a.str() * 0.075 + a.dex() * 0.05)));
		base.setDfs((int) (def * (1 + a.str() * 0.06 + a.dex() * 0.03)));
		base.setDodge(Math.max(0, a.dex() / 2 - a.vit() / 5));
		base.setParry(Math.max(0, a.dex() / 5));

		s.getStats().getPower().set(0.05 * a.wis());

		base.setMana(1 + (base.getAtk() + base.getDfs()) / 750);
		base.setSacrifices((base.getAtk() + base.getDfs()) / 3000);

		base.getTags().add("HERO");

		return s;
	}

	@Override
	public void beforeSave() {
		if (equipCache != null) {
			equipment = new JSONObject(equipCache.toString());
			equipCache = null;
		}

		if (skillCache != null) {
			skills = skillCache.stream()
					.map(Skill::getId)
					.collect(Collectors.toCollection(JSONArray::new));
			skillCache = null;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Hero hero = (Hero) o;
		return Objects.equals(id, hero.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
