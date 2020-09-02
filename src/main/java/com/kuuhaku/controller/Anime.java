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

package com.kuuhaku.controller;

import com.kuuhaku.utils.Helper;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Anime {
	public static JSONObject getData(String anime, String query) {
		JSONObject json = new JSONObject() {{
			put("query", query);
			put("variables", new JSONObject() {{
				put("anime", anime);
			}});
		}};

		return Helper.post("https://graphql.anilist.co", json, Map.of(
				"Content-Type", "application/json; charset=UTF-8",
				"Accept", "application/json",
				"User-Agent", "Mozilla/5.0"
				)
		);
	}

	public static JSONObject getDAData(String name) throws IOException {
		URL url = new URL("https://www.dreamanimes.com.br/api/anime-info/" + name);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		con.setRequestProperty("Accept", "application/json");
		con.addRequestProperty("Accept-Charset", "UTF-8");
		con.addRequestProperty("User-Agent", "Mozilla/5.0");
		con.addRequestProperty("Authorization", System.getenv("DA_TOKEN"));
		con.setInstanceFollowRedirects(false);

		String redir = con.getHeaderField("Location");

		if (redir != null) {
			return getDAData(redir.replace("/anime-info/", ""));
		}

		JSONObject resposta = new JSONObject();

		try {
			resposta = new JSONObject(IOUtils.toString(con.getInputStream(), StandardCharsets.UTF_8));
			if (con.getResponseCode() == HttpURLConnection.HTTP_OK) resposta.put("url", con.getURL().toString());
		} catch (IOException ignore) {
		}

		con.disconnect();

		Helper.logger(Anime.class).debug(resposta);
		return resposta;
	}
}
