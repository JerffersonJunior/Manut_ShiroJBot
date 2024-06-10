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

@Schedule("0 0 * * *")
public class DailySchedule implements Runnable, PreInitialize {
	@Override
	public void run() {
		DAO.applyNative((Class<?>) null, "CALL refresh_all_views('shiro')");
	}
}
