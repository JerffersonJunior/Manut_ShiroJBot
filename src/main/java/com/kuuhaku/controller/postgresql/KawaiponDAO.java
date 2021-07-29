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

package com.kuuhaku.controller.postgresql;

import com.kuuhaku.model.persistent.Kawaipon;
import com.kuuhaku.utils.Helper;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.List;

public class KawaiponDAO {
	public static Kawaipon getKawaipon(String id) {
		EntityManager em = Manager.getEntityManager();

		try {
			return Helper.getOr(em.find(Kawaipon.class, id), new Kawaipon(id));
		} finally {
			em.close();
		}
	}

	public static void saveKawaipon(Kawaipon k) {
		EntityManager em = Manager.getEntityManager();

		em.getTransaction().begin();
		em.merge(k);
		em.getTransaction().commit();

		em.close();
	}

	public static void removeKawaipon(Kawaipon k) {
		EntityManager em = Manager.getEntityManager();

		em.getTransaction().begin();
		em.remove(em.contains(k) ? k : em.merge(k));
		em.getTransaction().commit();

		em.close();
	}

	@SuppressWarnings({"unchecked", "SqlResolve"})
	public static List<Object[]> getCardRank() {
		EntityManager em = Manager.getEntityManager();

		Query q = em.createNativeQuery("SELECT * FROM shiro.\"GetKawaiponRanking\" k");

		List<Object[]> kps = q.getResultList();

		em.close();

		return kps;
	}
}
