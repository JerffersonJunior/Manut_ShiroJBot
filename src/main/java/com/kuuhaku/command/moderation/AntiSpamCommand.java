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

package com.kuuhaku.command.moderation;

import com.github.ygimenez.method.Pages;
import com.kuuhaku.interfaces.Executable;
import com.kuuhaku.interfaces.annotations.Command;
import com.kuuhaku.interfaces.annotations.Requires;
import com.kuuhaku.model.enums.AutoModType;
import com.kuuhaku.model.enums.Category;
import com.kuuhaku.model.enums.GuildFeature;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.persistent.guild.GuildSettings;
import com.kuuhaku.model.records.EventData;
import com.kuuhaku.model.records.MessageData;
import com.ygimenez.json.JSONObject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.automod.AutoModResponse;
import net.dv8tion.jda.api.entities.automod.AutoModRule;
import net.dv8tion.jda.api.entities.automod.build.AutoModRuleData;
import net.dv8tion.jda.api.entities.automod.build.TriggerConfig;

@Command(
		name = "antispam",
		category = Category.MODERATION
)
@Requires({
		Permission.MANAGE_SERVER,
		Permission.MODERATE_MEMBERS
})
public class AntiSpamCommand implements Executable {
	@Override
	public void execute(JDA bot, I18N locale, EventData data, MessageData.Guild event, JSONObject args) {
		GuildSettings settings = data.config().getSettings();
		if (settings.isFeatureEnabled(GuildFeature.ANTI_SPAM)) {
			settings.getFeatures().remove(GuildFeature.ANTI_SPAM);
			event.channel().sendMessage(locale.get("success/anti_spam_disable")).queue();

			String id = settings.getAutoModEntries().remove(AutoModType.SPAM);
			if (id != null) {
				event.guild().deleteAutoModRuleById(id).queue();
			}
		} else {
			settings.getFeatures().add(GuildFeature.ANTI_SPAM);
			event.channel().sendMessage(locale.get("success/anti_spam_enable")).queue();

			String ruleId = settings.getAutoModEntries().get(AutoModType.SPAM);
			if (ruleId == null) {
				AutoModRule rule = Pages.subGet(event.guild().createAutoModRule(
						AutoModRuleData
								.onMessage("Shiro anti-SPAM", TriggerConfig.antiSpam())
								.putResponses(AutoModResponse.blockMessage())
				));

				if (rule == null) {
					event.channel().sendMessage(locale.get("error/automod_full")).queue();
					return;
				}

				settings.getAutoModEntries().put(AutoModType.SPAM, rule.getId());
			}
		}

		settings.save();
	}
}
