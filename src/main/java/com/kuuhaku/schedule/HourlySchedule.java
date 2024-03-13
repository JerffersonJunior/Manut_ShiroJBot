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

package com.kuuhaku.schedule;

import com.kuuhaku.controller.DAO;
import com.kuuhaku.interfaces.PreInitialize;
import com.kuuhaku.interfaces.annotations.Schedule;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.persistent.user.Account;
import com.kuuhaku.model.persistent.user.Reminder;
import com.kuuhaku.util.Utils;
import net.dv8tion.jda.api.entities.User;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Schedule("0 * * * *")
public class HourlySchedule implements Runnable, PreInitialize {
	public static final Set<Integer> SCHED_REMINDERS = new HashSet<>();
	private static final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

	@Override
	public void run() {
		ZonedDateTime limit = ZonedDateTime.now(ZoneId.of("GMT-3")).plusHours(1).truncatedTo(ChronoUnit.HOURS);
		List<Reminder> rems = DAO.queryAll(Reminder.class, "SELECT r FROM Reminder r WHERE NOT r.reminded AND r.due <= ?1", limit);
		for (Reminder r : rems) {
			scheduleReminder(r);
		}
	}

	public static void scheduleReminder(Reminder r) {
		ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT-3"));
		SCHED_REMINDERS.add(r.getId());
		exec.schedule(() -> {
			Reminder rem = r.refresh();
			try {
				Account acc = rem.getAccount();
				I18N locale = acc.getEstimateLocale();
				User u = acc.getUser();

				if (rem.getChannel().canTalk()) {
					rem.getChannel().sendMessage(locale.get("str/reminder", u.getAsMention(), rem.getMessage())).queue();
				} else {
					u.openPrivateChannel()
							.flatMap(c -> c.sendMessage(locale.get("str/reminder", u.getAsMention(), rem.getMessage())))
							.queue(null, Utils::doNothing);
				}
			} finally {
				SCHED_REMINDERS.remove(rem.getId());
				rem.setReminded(true);
				rem.save();
			}
		}, now.until(r.getDue(), ChronoUnit.MILLIS), TimeUnit.MILLISECONDS);
	}
}
