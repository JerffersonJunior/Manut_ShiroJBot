package com.kuuhaku.model.records;

import net.dv8tion.jda.api.entities.TextChannel;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public record DuoLobby(RankedDuo duo, TextChannel channel, AtomicInteger threshold, AtomicBoolean unlocked) {
	public DuoLobby(RankedDuo duo, TextChannel channel) {
		this(duo, channel, new AtomicInteger(), new AtomicBoolean());
	}
}
