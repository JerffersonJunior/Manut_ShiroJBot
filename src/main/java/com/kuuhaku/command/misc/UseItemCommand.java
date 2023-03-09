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

package com.kuuhaku.command.misc;

import com.kuuhaku.exceptions.PendingConfirmationException;
import com.kuuhaku.interfaces.Executable;
import com.kuuhaku.interfaces.annotations.Command;
import com.kuuhaku.interfaces.annotations.Signature;
import com.kuuhaku.model.enums.Category;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.persistent.user.Account;
import com.kuuhaku.model.persistent.user.UserItem;
import com.kuuhaku.model.records.EventData;
import com.kuuhaku.model.records.MessageData;
import com.kuuhaku.util.Utils;
import com.kuuhaku.util.json.JSONObject;
import kotlin.Pair;
import net.dv8tion.jda.api.JDA;
import org.apache.commons.collections4.bag.HashBag;

import java.util.List;

@Command(
		name = "items",
		subname = "use",
		category = Category.MISC
)
@Signature(allowEmpty = true, value = "<id:number:r> <args:text>")
public class UseItemCommand implements Executable {
	@Override
	public void execute(JDA bot, I18N locale, EventData data, MessageData.Guild event, JSONObject args) {
		Account acc = data.profile().getAccount();
		HashBag<UserItem> items = acc.getItems();
		UserItem item = items.stream()
				.filter(i -> i.getId().equals(args.getString("id").toUpperCase()))
				.findFirst().orElse(null);

		if (item == null) {
			List<String> names = items.stream().map(UserItem::getId).toList();

			Pair<String, Double> sug = Utils.didYouMean(args.getString("id").toUpperCase(), names);
			event.channel().sendMessage(locale.get("error/item_not_found", sug.getFirst())).queue();
			return;
		} else if (items.getCount(item) == 0) {
			event.channel().sendMessage(locale.get("error/item_not_have")).queue();
			return;
		}

		try {
			Utils.confirm(locale.get("question/item_use", item.getName(locale), items.getCount(item)), event.channel(), w -> {
						if (acc.consumeItem(item)) {
							event.channel().sendMessage(locale.get("error/item_not_have")).queue();
						}

						event.channel().sendMessage(locale.get("success/item_use", item.getName(locale))).queue();
						return true;
					}, event.user()
			);
		} catch (PendingConfirmationException e) {
			event.channel().sendMessage(locale.get("error/pending_confirmation")).queue();
		}
	}
}
