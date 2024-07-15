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

package com.kuuhaku.interfaces.annotations;

import net.dv8tion.jda.api.entities.Message;
import org.intellij.lang.annotations.Language;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Signature {
	@Language("ShiroSig")
	String[] value();
	SigPattern[] patterns() default {};
	boolean allowEmpty() default false;

	enum Type {
		ANY("\\S+"),
		WORD("[\\w-.]+"),
		NUMBER("-?[\\d.]+"),
		TEXT("\"[^\"]+\""),

		USER(Message.MentionType.USER.getPattern().pattern()),
		ROLE(Message.MentionType.ROLE.getPattern().pattern()),
		CHANNEL(Message.MentionType.CHANNEL.getPattern().pattern()),
		EMOTE(Message.MentionType.EMOJI.getPattern().pattern()),

		CUSTOM;

		private final Pattern pattern;

		Type() {
			this.pattern =  null;
		}

		Type(@Language("RegExp") String regex) {
			this.pattern = Pattern.compile("\\b" + regex + "\\b");
		}

		public boolean validate(String value) {
			return pattern == null || (!value.isBlank() && pattern.matcher(value).matches());
		}

		public String getRegex() {
			return pattern.toString();
		}

		public Pattern getPattern() {
			return pattern;
		}
	}
}
