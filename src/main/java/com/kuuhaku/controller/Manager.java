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

package com.kuuhaku.controller;

import com.kuuhaku.Constants;
import com.kuuhaku.util.IO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class Manager {
	private static final String SERVER_IP = System.getenv("SERVER_IP");
	private static final String DB_NAME = System.getenv("DB_NAME");
	private static final String DB_LOGIN = System.getenv("DB_LOGIN");
	private static final String DB_PASS = System.getenv("DB_PASS");

	private static final EntityManagerFactory emf = Persistence.createEntityManagerFactory("main", Map.of(
			"jakarta.persistence.jdbc.user", DB_LOGIN,
			"jakarta.persistence.jdbc.password", DB_PASS,
			"jakarta.persistence.jdbc.url", "jdbc:postgresql://%s/%s?currentSchema=shiro&sslmode=require&useEncoding=true&characterEncoding=UTF-8".formatted(
					SERVER_IP, DB_NAME
			)
	));

	private static final ThreadLocal<EntityManager> em = new ThreadLocal<>();

	static {
		String db = DAO.queryNative(String.class, "SELECT current_database()");
		String schema = DAO.queryNative(String.class, "SELECT current_schema()");
		Constants.LOGGER.info("Connected to database {}, schema {} successfully", db, schema);

		File initDir = IO.getResourceAsFile("database");
		if (initDir != null && initDir.isDirectory()) {
			Set<String> scripts = new HashSet<>();
			try (Stream<Path> ioStream = Files.walk(initDir.toPath())) {
				ioStream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".sql"))
						.sorted(Comparator.comparing(Path::toString).thenComparing(Path::getNameCount))
						.peek(s -> scripts.add(FilenameUtils.removeExtension(s.getFileName().toString())))
						.map(IO::readString)
						.forEach(DAO::applyNative);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			Constants.LOGGER.info("Applied {} scripts: {}", scripts.size(), scripts);
		}
	}

	public static EntityManager getEntityManager() {
		EntityManager man = em.get();
		if (man == null) throw new IllegalStateException("Manager has not been initialized");

		return man;
	}

	public static Runnable attach(Runnable task) {
		return () -> {
			EntityManager man = em.get();
			if (man == null) {
				em.set(man = emf.createEntityManager());
			}

			try {
				task.run();
			} finally {
				if (man.getTransaction().isActive()) {
					man.getTransaction().rollback();
				}

				man.close();
				em.remove();
			}
		};
	}

	public static <T> Supplier<T> attach(Supplier<T> task) {
		return () -> {
			EntityManager man = em.get();
			if (man == null) {
				em.set(man = emf.createEntityManager());
			}

			try {
				return task.get();
			} finally {
				if (man.getTransaction().isActive()) {
					man.getTransaction().rollback();
				}

				man.close();
				em.remove();
			}
		};
	}

	public static long ping() {
		long curr = System.currentTimeMillis();

		DAO.queryUnmapped("SELECT 1");

		return System.currentTimeMillis() - curr;
	}
}
