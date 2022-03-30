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

package com.kuuhaku.events.cron;

import com.kuuhaku.Main;
import com.kuuhaku.controller.postgresql.MemberDAO;
import com.kuuhaku.controller.postgresql.TempRoleDAO;
import com.kuuhaku.controller.postgresql.VoiceTimeDAO;
import com.kuuhaku.handlers.api.websocket.EncoderClient;
import com.kuuhaku.model.persistent.BotStats;
import com.kuuhaku.model.persistent.MutedMember;
import com.kuuhaku.model.persistent.TempRole;
import com.kuuhaku.model.persistent.VoiceTime;
import com.kuuhaku.utils.Constants;
import com.kuuhaku.utils.ShiroInfo;
import com.kuuhaku.utils.helpers.MiscHelper;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

import javax.websocket.DeploymentException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MinuteEvent implements Job {

	@Override
	public void execute(JobExecutionContext context) {
		new BotStats().save();

		if (Main.getInfo().isEncoderDisconnected()) {
			try {
				Main.getInfo().setEncoderClient(new EncoderClient(Constants.SOCKET_ROOT + "/encoder"));
			} catch (URISyntaxException | DeploymentException | IOException e) {
				MiscHelper.logger(ShiroInfo.class).error(e + " | " + e.getStackTrace()[0]);
			}
		}

		Collection<VoiceTime> voiceTimes = Main.getEvents().getVoiceTimes().values();
		for (VoiceTime vt : voiceTimes) {
			vt.update();
			VoiceTimeDAO.saveVoiceTime(vt);
		}

		for (Guild g : Main.getShiro().getGuilds()) {
			for (Emote e : g.getEmotes()) {
				if (e.getName().startsWith("TEMP_")) {
					e.delete().queue(null, MiscHelper::doNothing);
				}
			}
		}

		for (MutedMember m : MemberDAO.getMutedMembers()) {
			Guild g = Main.getGuildByID(m.getGuild());

			if (g == null) {
				MemberDAO.removeMutedMember(m);
			} else if (!m.isMuted()) {
				try {
					if (!g.getSelfMember().hasPermission(Permission.MANAGE_ROLES, Permission.MANAGE_CHANNEL, Permission.MANAGE_PERMISSIONS))
						continue;

					Member mb = g.getMemberById(m.getUid());
					if (mb == null) {
						MemberDAO.removeMutedMember(m);
						continue;
					}

					List<AuditableRestAction<Void>> act = new ArrayList<>();
					for (GuildChannel gc : g.getChannels()) {
						PermissionOverride po = gc.getPermissionOverride(mb);
						if (po != null)
							act.add(po.delete());
					}

					RestAction.allOf(act)
							.queue(s -> {
								MiscHelper.logToChannel(g.getSelfMember().getUser(), false, null, mb.getAsMention() + " foi dessilenciado por " + g.getSelfMember().getAsMention(), g);
								MemberDAO.removeMutedMember(m);
							}, MiscHelper::doNothing);
				} catch (HierarchyException ignore) {
				} catch (IllegalArgumentException | NullPointerException e) {
					MemberDAO.removeMutedMember(m);
				}
			}
		}

		List<TempRole> tempRoles = TempRoleDAO.getExpiredRoles();
		for (TempRole role : tempRoles) {
			try {
				Guild g = Main.getGuildByID(role.getSid());
				Role r = g.getRoleById(role.getRid());

				if (r != null)
					g.removeRoleFromMember(role.getUid(), r).queue();
			} catch (Exception ignore) {
			} finally {
				TempRoleDAO.removeTempRole(role);
			}
		}
	}
}
