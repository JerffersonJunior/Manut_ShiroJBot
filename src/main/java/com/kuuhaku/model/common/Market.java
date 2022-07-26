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

package com.kuuhaku.model.common;

import com.kuuhaku.controller.DAO;
import com.kuuhaku.model.persistent.shiro.GlobalProperty;
import com.kuuhaku.model.persistent.user.Account;
import com.kuuhaku.model.persistent.user.StashedCard;
import com.kuuhaku.util.Utils;
import com.kuuhaku.util.XStringBuilder;
import com.kuuhaku.util.json.JSONObject;
import org.apache.commons.cli.Option;

import java.awt.*;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Market {
	private static final Dimension BANNER_SIZE = new Dimension(450, 100);
	private final String uid;
	private final Map<String, String> FILTERS = new LinkedHashMap<>() {{
		put("n", "AND c.card.id LIKE '%%'||?%s||'%%'");
		put("r", "AND CAST(c.card.rarity AS STRING) LIKE '%%'||?%s||'%%'");
		put("a", "AND c.card.anime.id LIKE '%%'||?%s||'%%'");
		put("c", "AND c.chrome = TRUE");
		put("k", "AND c.type = 'KAWAIPON'");
		put("e", "AND c.type = 'EVOGEAR'");
		put("f", "AND c.type = 'FIELD'");
		put("gl", "AND c.price >= ?%s");
		put("lt", "AND c.price <= ?%s");
		put("m", "AND c.kawaipon.uid = ?%s");
	}};

	public Market(String uid) {
		this.uid = uid;
	}

	public List<StashedCard> getOffers(Option[] opts) {
		List<Object> params = new ArrayList<>();
		XStringBuilder query = new XStringBuilder("""
				SELECT c FROM StashedCard c
				LEFT JOIN KawaiponCard kc ON kc.stashEntry = c
				LEFT JOIN Evogear e ON e.card = c.card
				WHERE c.price > 0
				""");

		AtomicInteger i = new AtomicInteger(1);
		for (Option opt : opts) {
			query.appendNewLine(FILTERS.get(opt.getOpt()).formatted(i.getAndIncrement()));

			if (opt.hasArg()) {
				params.add(opt.getValue().toUpperCase(Locale.ROOT));
			}
		}

		query.appendNewLine("""
				ORDER BY c.price / COALESCE(
						e.tier,
					   	CASE c.card.rarity
					   		WHEN 'COMMON' THEN 1
				      		WHEN 'UNCOMMON' THEN 1.5
				      		WHEN 'RARE' THEN 2
				      		WHEN 'ULTRA_RARE' THEN 2.5
				      		WHEN 'LEGENDARY' THEN 3
				      		ELSE 1
				       	END)
					   	, c.card.id
				""");

		return DAO.queryAll(StashedCard.class, query.toString(), params.toArray());
	}

	public boolean buy(int id) {
		StashedCard sc = DAO.find(StashedCard.class, id);
		if (sc == null) return false;

		int price = sc.getPrice();
		DAO.apply(Account.class, sc.getKawaipon().getUid(), a -> {
			a.addCR(price, "Sold " + sc);
			a.getUser().openPrivateChannel()
					.flatMap(c -> c.sendMessage(a.getEstimateLocale().get("success/market_notification", sc, price)))
					.queue(null, Utils::doNothing);
		});
		DAO.apply(Account.class, uid, a -> {
			a.consumeCR(price, "Purchased " + sc);
			sc.setKawaipon(a.getKawaipon());
			sc.setPrice(0);
			sc.save();
		});

		return true;
	}

	public int getDailyOffer() {
		int seed = LocalDate.now().get(ChronoField.EPOCH_DAY);

		GlobalProperty today = DAO.find(GlobalProperty.class, "daily_offer");
		JSONObject jo;
		if (today == null) {
			jo = new JSONObject(){{
				put("updated", seed);
				put("id", DAO.queryNative(Integer.class, "SELECT c.id FROM stashed_card c WHERE c.price > 0 ORDER BY RANDOM()"));
			}};

			new GlobalProperty("daily_offer", jo).save();
		} else {
			jo = new JSONObject(today.getValue());

			if (jo.getInt("updated") != seed) {
				jo.put("updated", seed);
				jo.put("id", DAO.queryNative(Integer.class, "SELECT c.id FROM stashed_card c WHERE c.price > 0 ORDER BY RANDOM()"));

				today.save();
			}
		}

		return jo.getInt("id");
	}
}
