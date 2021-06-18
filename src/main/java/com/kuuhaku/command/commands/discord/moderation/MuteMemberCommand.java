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

package com.kuuhaku.command.commands.discord.moderation;

import com.kuuhaku.command.Category;
import com.kuuhaku.command.Executable;
import com.kuuhaku.controller.postgresql.MemberDAO;
import com.kuuhaku.model.annotations.Command;
import com.kuuhaku.model.annotations.Requires;
import com.kuuhaku.model.enums.I18n;
import com.kuuhaku.model.persistent.MutedMember;
import com.kuuhaku.utils.Helper;
import com.kuuhaku.utils.ShiroInfo;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;

import java.util.ArrayList;
import java.util.List;

@Command(
		name = "silenciar",
		aliases = {"mute", "silence"},
		usage = "req_member-time-reason",
		category = Category.MODERATION
)
@Requires({Permission.MANAGE_ROLES, Permission.MANAGE_CHANNEL, Permission.MANAGE_PERMISSIONS})
public class MuteMemberCommand implements Executable {

	@Override
	public void execute(User author, Member member, String command, String argsAsText, String[] args, Message message, TextChannel channel, Guild guild, String prefix) {
		if (message.getMentionedMembers().isEmpty()) {
			channel.sendMessage(I18n.getString("err_mention-required")).queue();
			return;
		} else if (!member.hasPermission(Permission.MESSAGE_MANAGE)) {
			channel.sendMessage(I18n.getString("err_mute-not-allowed")).queue();
			return;
		}

		Member mb = message.getMentionedMembers().get(0);

		if (!member.canInteract(mb)) {
			channel.sendMessage(I18n.getString("err_cannot-mute-higher-role")).queue();
			return;
		} else if (!guild.getSelfMember().canInteract(mb)) {
			channel.sendMessage(I18n.getString("err_cannot-mute-higher-role-me")).queue();
			return;
		} else if (ShiroInfo.getStaff().contains(mb.getId())) {
			channel.sendMessage(I18n.getString("err_cannot-mute-staff")).queue();
			return;
		} else if (MemberDAO.getMutedMemberById(mb.getId()) != null && MemberDAO.getMutedMemberById(mb.getId()).isMuted()) {
			channel.sendMessage("❌ | Esse membro já está silenciado.").queue();
			return;
		}

		String[] params = argsAsText.split("(?:[0-9]+d\\s*)?(?:[0-9]+h\\s*)?(?:[0-9]+m\\s*)(?:[0-9]+s)?");
		if (params.length < 2) {
			channel.sendMessage("❌ | Você precisa informar um tempo e uma razão.").queue();
			return;
		}

		String reason = params[1];
		MutedMember m = Helper.getOr(MemberDAO.getMutedMemberById(mb.getId()), new MutedMember(mb.getId(), guild.getId()));
		long time = Helper.stringToDurationMillis(argsAsText);
		if (time < 60000) {
			channel.sendMessage("❌ | O tempo deve ser maior que 1 minuto.").queue();
			return;
		}

		m.setReason(reason);
		m.mute(time);

		List<PermissionOverrideAction> act = new ArrayList<>();
		for (TextChannel chn : guild.getTextChannels()) {
			act.add(chn.putPermissionOverride(mb).deny(Helper.ALL_MUTE_PERMISSIONS));
		}

		RestAction.allOf(act)
				.flatMap(s -> channel.sendMessage("✅ | Usuário silenciado por " + Helper.toStringDuration(time) + " com sucesso!\nRazão: `" + reason + "`"))
				.queue(s -> {
					Helper.logToChannel(author, false, null, mb.getAsMention() + " foi silenciado por " + Helper.toStringDuration(time) + ".\nRazão: `" + reason + "`", guild);
					MemberDAO.saveMutedMember(m);
				}, Helper::doNothing);
	}
}
