/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2020  Yago Gimenez (KuuHaKu)
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

package com.kuuhaku.controller.postgresql;

import com.kuuhaku.model.common.MarketValue;
import com.kuuhaku.model.common.StockValue;
import com.kuuhaku.model.enums.KawaiponRarity;
import com.kuuhaku.model.persistent.Card;
import com.kuuhaku.model.persistent.StockMarket;
import com.kuuhaku.utils.Helper;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockMarketDAO {
	@SuppressWarnings("unchecked")
	public static List<StockMarket> getInvestments(String id) {
		EntityManager em = Manager.getEntityManager();

		Query q = em.createQuery("SELECT sm FROM StockMarket sm WHERE sm.uid = :id", StockMarket.class);
		q.setParameter("id", id);

		try {
			return q.getResultList();
		} finally {
			em.close();
		}
	}

	public static StockMarket getCardInvestment(String id, Card c) {
		EntityManager em = Manager.getEntityManager();

		Query q = em.createQuery("SELECT sm FROM StockMarket sm WHERE sm.uid = :id AND sm.card = :card", StockMarket.class);
		q.setParameter("id", id);
		q.setParameter("card", c);

		try {
			return (StockMarket) q.getSingleResult();
		} catch (NoResultException e) {
			return new StockMarket(id, c, 0);
		} finally {
			em.close();
		}
	}

	public static void saveInvestment(StockMarket sm) {
		EntityManager em = Manager.getEntityManager();

		em.getTransaction().begin();
		em.merge(sm);
		em.getTransaction().commit();

		em.close();
	}

	public static void removeZeroInvestments() {
		EntityManager em = Manager.getEntityManager();

		em.getTransaction().begin();
		em.createQuery("DELETE FROM StockMarket sm WHERE sm.investment = 0")
				.executeUpdate();
		em.getTransaction().commit();

		em.close();
	}

	@SuppressWarnings("unchecked")
	public static Map<String, StockValue> getValues() {
		EntityManager em = Manager.getEntityManager();

		Query prev = em.createNativeQuery("""
				SELECT c.id
				  	 , c.name
				  	 , CASE x.sold >= 10
				          WHEN TRUE THEN x.value
				 	 END AS value
				FROM Card c
				LEFT JOIN (
				    SELECT x.card_id
				         , ROUND(EXP(SUM(LN(x.price)) * (1.0 / COUNT(1))) * 1000) / 1000 AS value
				         , COUNT(x.card_id)                                              AS sold
				    FROM (
				       SELECT c.id                                                     AS card_id
				            , COALESCE(cm.price, em.price, fm.price)                   AS price
				            , COALESCE(cm.buyer, em.buyer, fm.buyer)                   AS buyer
				            , COALESCE(cm.seller, em.seller, fm.seller)                AS seller
				       FROM Card c
				       LEFT JOIN Equipment e ON e.card_id = c.id
				       LEFT JOIN Field f ON f.card_id = c.id
				       LEFT JOIN CardMarket cm ON cm.card_id = c.id
				       LEFT JOIN EquipmentMarket em ON em.card_id = e.id
				       LEFT JOIN FieldMarket fm ON fm.card_id = f.id
				    ) x
				      WHERE x.buyer <> ''
				      AND x.buyer <> x.seller
				    GROUP BY x.card_id
				) x ON x.card_id = c.id
				ORDER BY c.id
				""");

		Query curr = em.createNativeQuery("""
				SELECT c.id
				     , c.name
				     , x.value
				FROM Card c
				         LEFT JOIN (
				    SELECT x.card_id
				         , ROUND(EXP(SUM(LN(x.price)) * (1.0 / COUNT(1))) * 1000) / 1000 AS value
				         , COUNT(x.card_id)                                              AS sold
				    FROM (
				        SELECT c.id                                                     AS card_id
				             , COALESCE(cm.price, em.price, fm.price)                   AS price
				             , COALESCE(cm.publishdate, em.publishdate, fm.publishdate) AS publishdate
				             , COALESCE(cm.buyer, em.buyer, fm.buyer)                   AS buyer
				             , COALESCE(cm.seller, em.seller, fm.seller)                AS seller
				        FROM Card c
				        LEFT JOIN Equipment e ON e.card_id = c.id
				        LEFT JOIN Field f ON f.card_id = c.id
				        LEFT JOIN CardMarket cm ON cm.card_id = c.id
				        LEFT JOIN EquipmentMarket em ON em.card_id = e.id
				        LEFT JOIN FieldMarket fm ON fm.card_id = f.id
				    ) x
				    WHERE x.publishDate > :date
				      AND x.buyer <> ''
				      AND x.buyer <> x.seller
				    GROUP BY x.card_id
				) x ON x.card_id = c.id
				ORDER BY c.id
				""")
				.setParameter("date", ZonedDateTime.now(ZoneId.of("GMT-3")).minusWeeks(1));

		Map<String, StockValue> out = new HashMap<>();
		List<Object[]> prevResults = (List<Object[]>) prev.getResultList();
		List<Object[]> currResults = (List<Object[]>) curr.getResultList();

		for (int i = 0; i < prevResults.size(); i++) {
			Object[] prevRes = prevResults.get(i);
			Object[] currRes = currResults.get(i);

			out.put(String.valueOf(currRes[0]), new StockValue(
					String.valueOf(currRes[0]),
					String.valueOf(currRes[1]),
					Helper.getOr((Double) prevRes[2], 0d),
					Helper.getOr((Double) currRes[2], 0d)
			));
		}

		try {
			return out;
		} finally {
			em.close();
		}
	}

	@SuppressWarnings("unchecked")
	public static List<MarketValue> getCardHistory(Card c) {
		EntityManager em = Manager.getEntityManager();

		Query q = em.createNativeQuery("""
				SELECT :card                       AS id
				     , FIRST_VALUE(x.price) OVER w AS open
				     , MAX(x.price)                AS high
				     , MIN(x.price)                AS low
				     , LAST_VALUE(x.price) OVER w  AS close
				     , x.publishdate
				FROM (
				         SELECT c.id                                                                        AS card_id
				              , COALESCE(cm.price, em.price, fm.price)                                      AS price
				              , COALESCE(cm.buyer, em.buyer, fm.buyer)                                      AS buyer
				              , COALESCE(cm.seller, em.seller, fm.seller)                                   AS seller
				              , DATE_TRUNC('DAY', COALESCE(cm.publishdate, em.publishdate, fm.publishdate)) AS publishdate
				         FROM Card c
				                  LEFT JOIN Equipment e ON e.card_id = c.id
				                  LEFT JOIN Field f ON f.card_id = c.id
				                  LEFT JOIN CardMarket cm ON cm.card_id = c.id
				                  LEFT JOIN EquipmentMarket em ON em.card_id = e.id
				                  LEFT JOIN FieldMarket fm ON fm.card_id = f.id
				     ) x
				WHERE x.buyer <> ''
				  AND x.buyer <> x.seller
				  AND x.card_id = :card
				GROUP BY x.publishdate, x.card_id, x.price
				    WINDOW w AS (PARTITION BY x.card_id, x.publishdate ORDER BY x.publishdate)
				ORDER BY publishdate
				""")
				.setParameter("card", c.getId());

		try {
			return Helper.map(MarketValue.class, q.getResultList());
		} finally {
			em.close();
		}
	}

	@SuppressWarnings("unchecked")
	public static List<MarketValue> getRarityHistory(KawaiponRarity r) {
		EntityManager em = Manager.getEntityManager();

		Query q = em.createNativeQuery("""
				SELECT :rarity                       AS id
				     , FIRST_VALUE(x.price) OVER w AS open
				     , MAX(x.price)                AS high
				     , MIN(x.price)                AS low
				     , LAST_VALUE(x.price) OVER w  AS close
				     , x.publishdate
				FROM (
				         SELECT c.rarity                                                                    AS rarity
				              , COALESCE(cm.price, em.price, fm.price)                                      AS price
				              , COALESCE(cm.buyer, em.buyer, fm.buyer)                                      AS buyer
				              , COALESCE(cm.seller, em.seller, fm.seller)                                   AS seller
				              , DATE_TRUNC('DAY', COALESCE(cm.publishdate, em.publishdate, fm.publishdate)) AS publishdate
				         FROM Card c
				                  LEFT JOIN Equipment e ON e.card_id = c.id
				                  LEFT JOIN Field f ON f.card_id = c.id
				                  LEFT JOIN CardMarket cm ON cm.card_id = c.id
				                  LEFT JOIN EquipmentMarket em ON em.card_id = e.id
				                  LEFT JOIN FieldMarket fm ON fm.card_id = f.id
				     ) x
				WHERE x.buyer <> ''
				  AND x.buyer <> x.seller
				  AND x.rarity = :rarity
				GROUP BY x.publishdate, x.rarity, x.price
				    WINDOW w AS (PARTITION BY x.rarity, x.publishdate ORDER BY x.publishdate)
				ORDER BY publishdate
				""")
				.setParameter("rarity", r.name());

		try {
			return Helper.map(MarketValue.class, q.getResultList());
		} finally {
			em.close();
		}
	}
}
