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

package com.kuuhaku.model.enums;

import com.kuuhaku.Main;
import com.kuuhaku.controller.postgresql.ClanDAO;
import com.kuuhaku.model.persistent.*;
import com.kuuhaku.model.records.ClanRanking;
import com.kuuhaku.utils.helpers.ImageHelper;
import com.kuuhaku.utils.helpers.MathHelper;
import com.kuuhaku.utils.helpers.MiscHelper;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;

public enum Tag {
	NIICHAN(TagIcons.NIICHAN, "Desenvolvedor inicial da Shiro, nominalmente KuuHaKu.",
			(user, member) -> MiscHelper.hasPermission(member, PrivilegeLevel.NIICHAN)),

	DESENVOLVEDOR(TagIcons.DEV, "Equipe de desenvolvimento da Shiro.",
			(user, member) -> MiscHelper.hasPermission(member, PrivilegeLevel.DEV)),

	SUPORTE(TagIcons.SUPPORT, "Equipe de suporte da Shiro.",
			(user, member) -> MiscHelper.hasPermission(member, PrivilegeLevel.SUPPORT)),

	REDATOR(TagIcons.EDITOR, "Equipe de redação da Shiro.",
			(user, member) -> false),

	MODERADOR(TagIcons.MODERATOR, "Equipe de moderação desse servidor.",
			(user, member) -> MiscHelper.hasPermission(member, PrivilegeLevel.MOD)),

	CASADO(TagIcons.MARRIED, "Usuário que possui uma waifu/husbando UwU.",
			(user, member) -> Couple.query(Couple.class, "SELECT c FROM Couple c WHERE :uid IN (c.husbando, c.waifu)", user.getId()) != null),

	RICO(TagIcons.RICH, "Usuário que possui 500 mil CR ou mais.",
			(user, member) -> Account.find(Account.class, user.getId()).getBalance() > 500000),

	CARTAS_NORMAIS_25(TagIcons.COLLECTION25, "Usuário que completou 25% da coleção de Kawaipons normais.",
			(user, member) -> {
				int total = Card.queryNative(Number.class, "SELECT COUNT(1) FROM Card").intValue();
				return MathHelper.between(Kawaipon.find(Kawaipon.class, user.getId()).getNormalCards().size() / (float) total, 0.25, 0.5);
			}),

	CARTAS_NORMAIS_50(TagIcons.COLLECTION50, "Usuário que completou 50% da coleção de Kawaipons normais.",
			(user, member) -> {
				int total = Card.queryNative(Number.class, "SELECT COUNT(1) FROM Card").intValue();
				return MathHelper.between(Kawaipon.find(Kawaipon.class, user.getId()).getNormalCards().size() / (float) total, 0.5, 0.75);
			}),

	CARTAS_NORMAIS_75(TagIcons.COLLECTION75, "Usuário que completou 75% da coleção de Kawaipons normais.",
			(user, member) -> {
				int total = Card.queryNative(Number.class, "SELECT COUNT(1) FROM Card").intValue();
				return MathHelper.between(Kawaipon.find(Kawaipon.class, user.getId()).getNormalCards().size() / (float) total, 0.75, 1);
			}),

	CARTAS_NORMAIS_100(TagIcons.COLLECTION100, "Usuário que completou 100% da coleção de Kawaipons normais.",
			(user, member) -> {
				int total = Card.queryNative(Number.class, "SELECT COUNT(1) FROM Card").intValue();
				return Kawaipon.find(Kawaipon.class, user.getId()).getNormalCards().size() / (float) total >= 1;
			}),

	CARTAS_CROMADAS_25(TagIcons.FOIL25, "Usuário que completou 25% da coleção de Kawaipons cromados.",
			(user, member) -> {
				int total = Card.queryNative(Number.class, "SELECT COUNT(1) FROM Card").intValue();
				return MathHelper.between(Kawaipon.find(Kawaipon.class, user.getId()).getFoilCards().size() / (float) total, 0.25, 0.5);
			}),

	CARTAS_CROMADAS_50(TagIcons.FOIL50, "Usuário que completou 50% da coleção de Kawaipons cromados.",
			(user, member) -> {
				int total = Card.queryNative(Number.class, "SELECT COUNT(1) FROM Card").intValue();
				return MathHelper.between(Kawaipon.find(Kawaipon.class, user.getId()).getFoilCards().size() / (float) total, 0.5, 0.75);
			}),

	CARTAS_CROMADAS_75(TagIcons.FOIL75, "Usuário que completou 75% da coleção de Kawaipons cromados.",
			(user, member) -> {
				int total = Card.queryNative(Number.class, "SELECT COUNT(1) FROM Card").intValue();
				return MathHelper.between(Kawaipon.find(Kawaipon.class, user.getId()).getFoilCards().size() / (float) total, 0.75, 1);
			}),

	CARTAS_CROMADAS_100(TagIcons.FOIL100, "Usuário que completou 100% da coleção de Kawaipons cromados.",
			(user, member) -> {
				int total = Card.queryNative(Number.class, "SELECT COUNT(1) FROM Card").intValue();
				return Kawaipon.find(Kawaipon.class, user.getId()).getFoilCards().size() / (float) total >= 1;
			}),

	CLA_VITORIOSO(TagIcons.CLAN_CHAMPION, "Seu clã está em primeiro lugar.",
			(user, member) -> {
				Clan c = ClanDAO.getUserClan(user.getId());
				ClanRanking cr = ClanDAO.getClanChampion();
				return c != null && cr != null && cr.id() == c.getId();
			}),

	BUG_HUNTER(TagIcons.BUGHUNTER, "Você ajudou a corrigir muitos bugs na Shiro.",
			(user, member) -> Account.find(Account.class, user.getId()).getBugs() > 25),

	PADORU_PADORU(TagIcons.PADORU, "Você cantou tão bem que Nero te deu um emblema.",
			(user, member) -> !DynamicParameter.find(DynamicParameter.class, "padoru_" + user.getId()).getValue().isBlank());

	private final TagIcons emote;
	private final String description;
	private final BiFunction<User, Member, Boolean> condition;

	Tag(TagIcons emote, String description, BiFunction<User, Member, Boolean> condition) {
		this.emote = emote;
		this.description = description;
		this.condition = condition;
	}

	public InputStream getPath(com.kuuhaku.model.persistent.Member mb) throws IOException, NullPointerException {
		Emote e = Main.getShiro().getEmoteById(getEmote().getId(mb.getLevel()));

		if (e != null)
			return ImageHelper.getImage(e.getImageUrl());
		else
			return null;
	}

	public TagIcons getEmote() {
		return emote;
	}

	public String getDescription() {
		return description;
	}

	public static Set<Tag> getTags(Member m) {
		Set<Tag> tags = new TreeSet<>(Comparator.comparing(Tag::toString));
		for (Tag t : values()) {
			if (t.condition.apply(m.getUser(), m)) tags.add(t);
		}
		return tags;
	}

	@Override
	public String toString() {
		return StringUtils.capitalize(name().toLowerCase(Locale.ROOT).replace("_", " "));
	}
}
