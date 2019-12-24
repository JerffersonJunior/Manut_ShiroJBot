package com.kuuhaku.command.commands.dev;

import com.kuuhaku.Main;
import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.Event;

public class RelaysCommand extends Command {

	public RelaysCommand() {
		super("relays", new String[]{"relist"}, "Mostra os IDs dos clientes do relay.", Category.DEVS);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, Event event, String prefix) {
		StringBuilder sb = new StringBuilder();
		sb.append("```\n");
		for (String s : Main.getRelay().getRelayMap().values()) {
			sb.append(s).append("\n");
		}
		sb.append("```\n");
		channel.sendMessage(sb.toString()).queue();
	}
}
