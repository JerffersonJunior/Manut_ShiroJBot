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

package com.kuuhaku.command.profile;

import com.kuuhaku.interfaces.Executable;
import com.kuuhaku.interfaces.annotations.Command;
import com.kuuhaku.interfaces.annotations.Signature;
import com.kuuhaku.model.enums.Category;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.persistent.user.AccountSettings;
import com.kuuhaku.model.records.EventData;
import com.kuuhaku.model.records.MessageData;
import com.kuuhaku.util.IO;
import com.ygimenez.json.JSONObject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;

import java.util.Map;

@Command(
		name = "profile",
		path = "background",
		category = Category.MISC
)
@Signature(allowEmpty = true, value = "<text:text:r>")
public class ProfileBackgroundCommand implements Executable {
	@Override
	public void execute(JDA bot, I18N locale, EventData data, MessageData.Guild event, JSONObject args) {
		AccountSettings settings = data.profile().getAccount().getSettings();

		String url = args.getString("text");
		String text = url;
		if (text.isBlank()) {
			if (!event.message().getAttachments().isEmpty()) {
				for (Message.Attachment att : event.message().getAttachments()) {
					if (att.isImage()) {
						url = att.getUrl();
						text = new JSONObject(Map.of(
								"channel", event.channel().getId(),
								"message", event.message().getId()
						)).toString();
						break;
					}
				}
			} else {
				settings.setBackground(null);
				settings.save();

				event.channel().sendMessage(locale.get("success/profile_background_clear")).queue();
				return;
			}
		}

		if (url.length() > 255) {
			event.channel().sendMessage(locale.get("error/url_too_long")).queue();
			return;
		} else if (!UrlValidator.getInstance().isValid(url)) {
			event.channel().sendMessage(locale.get("error/invalid_url")).queue();
			return;
		}

		long size = IO.getImageSize(url);
		if (size == 0) {
			event.channel().sendMessage(locale.get("error/invalid_url")).queue();
			return;
		} else if (size > AccountSettings.MAX_BG_SIZE) {
			event.channel().sendMessage(locale.get("error/image_too_big", "4 MB")).queue();
			return;
		}

		settings.setBackground(text);
		settings.save();

		event.channel().sendMessage(locale.get("success/profile_background_set")).queue();
	}
}