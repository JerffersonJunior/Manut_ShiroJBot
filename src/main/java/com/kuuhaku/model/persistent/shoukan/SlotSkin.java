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

package com.kuuhaku.model.persistent.shoukan;

import com.kuuhaku.Constants;
import com.kuuhaku.controller.DAO;
import com.kuuhaku.model.enums.Currency;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.enums.shoukan.Side;
import com.kuuhaku.model.persistent.converter.JSONArrayConverter;
import com.kuuhaku.model.persistent.user.Account;
import com.kuuhaku.model.persistent.user.Title;
import com.kuuhaku.util.Graph;
import com.kuuhaku.util.IO;
import com.kuuhaku.util.Utils;
import com.ygimenez.json.JSONArray;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "slot_skin")
public class SlotSkin extends DAO<SlotSkin> {

	@Transient
	public static final SlotSkin DEFAULT = new SlotSkin("DEFAULT");

	@Id
	@Column(name = "id", nullable = false)
	private String id;

	@Column(name = "price")
	private int price;

	@Enumerated(EnumType.STRING)
	@Column(name = "currency")
	private Currency currency;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "titles", nullable = false, columnDefinition = "JSONB")
	@Convert(converter = JSONArrayConverter.class)
	private JSONArray titles;

	public SlotSkin() {
	}

	public SlotSkin(String id) {
		this.id = id;
	}

	public BufferedImage getImage(Side side, boolean legacy) {
		String s = side.name().toLowerCase();
		BufferedImage overlay = IO.getResourceAsImage(Constants.ORIGIN_RESOURCES + "shoukan/overlay/" + s + (legacy ? "_legacy" : "") + ".png");
		if (overlay == null) return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

		BufferedImage bi = new BufferedImage(overlay.getWidth(), overlay.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = bi.createGraphics();
		g2d.setRenderingHints(Constants.SD_HINTS);

		BufferedImage theme = IO.getResourceAsImage("shoukan/side/" + id.toLowerCase() + "_" + s + ".png");
		Graph.applyMask(theme, IO.getResourceAsImage("shoukan/mask/slot_" + s + (legacy ? "_legacy" : "") + "_mask.png"), 0);

		g2d.drawImage(theme, 5, 5, null);
		g2d.drawImage(overlay, 0, 0, null);

		g2d.dispose();

		return bi;
	}

	public String getId() {
		return id;
	}

	public String getName(I18N locale) {
		return locale.get("skin/" + id);
	}

	public String getDescription(I18N locale) {
		return locale.get("skin/" + id + "_desc");
	}

	public List<Title> getTitles() {
		if (titles == null) return List.of();

		List<Title> out = new ArrayList<>();
		for (Object title : titles) {
			if (title instanceof String s) {
				Title t = DAO.find(Title.class, s);
				if (t != null) {
					out.add(t);
				}
			}
		}

		return out;
	}

	public int getPrice() {
		return price;
	}

	public Currency getCurrency() {
		return currency;
	}

	public boolean canUse(Account acc) {
		if (titles == null) return true;

		for (Object title : titles) {
			if (title instanceof String s) {
				if (!acc.hasTitle(s)) return false;
			}
		}

		if (price > 0) {
			return !acc.getDynValue("ss_" + id.toLowerCase()).isBlank();
		}

		return true;
	}
}