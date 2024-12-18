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

package com.kuuhaku.model.common;

import com.kuuhaku.Constants;
import org.apache.commons.lang3.time.StopWatch;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class Checkpoint implements AutoCloseable {
	public static final Checkpoint INSTANCE = new Checkpoint();

	private final StopWatch watch = new StopWatch();
	private final ArrayDeque<Long> laps = new ArrayDeque<>();
	private final HashMap<Integer, String> comments = new HashMap<>();
	private final HashMap<Integer, String> caller = new HashMap<>();

	public Checkpoint() {
		start();
	}

	public void start() {
		watch.start();

		markCaller();

		Constants.LOGGER.info("Measurements started");
	}

	public void lap() {
		lap(null);
	}

	public void lap(String comment) {
		watch.stop();
		laps.add(watch.getTime(TimeUnit.MILLISECONDS));
		if (comment != null) {
			comments.put(laps.size(), comment);
		}

		markCaller();

		watch.reset();
		watch.start();

		Constants.LOGGER.info("Lap {} marked at {}ms", laps.size(), laps.getLast());
	}

	private void markCaller() {
		StackTraceElement line = null;
		for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
			if (ste.getClassName().startsWith("com.kuuhaku") && !ste.getClassName().equals(getClass().getName())) {
				line = ste;
				break;
			}
		}

		if (line != null) {
			caller.put(laps.size(), line.getClassName() + "." + line.getMethodName() + "(" + line.getLineNumber() + ")");
		} else {
			caller.put(laps.size(), "(Unknown source)");
		}
	}

	@Override
	public void close() {
		watch.stop();
		laps.add(watch.getTime(TimeUnit.MILLISECONDS));

		long total = laps.stream().mapToLong(l -> l).sum();
		Constants.LOGGER.info("Final lap marked at {}ms", laps.getLast());
		if (total == 0) {
			Constants.LOGGER.info("All laps took 0ms to complete");
			return;
		}

		int lngIdx = 0, i = 0;
		long lngTime = 0;
		StringBuilder sb = new StringBuilder("\nTotal time: " + total + "ms");
		for (Long lap : laps) {
			sb.append("\n%s: %sms (%s%%) %s".formatted(
					++i, lap, lap * 100 / total, comments.getOrDefault(i, "")
			));

			if (lap > lngTime) {
				lngIdx = i;
				lngTime = lap;
			}
		}

		sb.append("\nLongest lap was: %s - %sms after %s".formatted(lngIdx, lngTime, caller.get(lngIdx)));
		Constants.LOGGER.info(sb.toString());

		watch.reset();
		laps.clear();
		comments.clear();
		caller.clear();
	}
}
