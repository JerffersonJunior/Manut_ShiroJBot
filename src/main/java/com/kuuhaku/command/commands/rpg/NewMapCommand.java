package com.kuuhaku.command.commands.rpg;

import com.kuuhaku.Main;
import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import com.kuuhaku.handlers.games.RPG.Handlers.MapRegisterHandler;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.Event;

public class NewMapCommand extends Command {

	public NewMapCommand() {
		super("rnovomapa", new String[]{"rnewmap"}, "Inicia o cadastro de um novo mapa.", Category.RPG);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, Event event, String prefix) {
		if (Main.getInfo().getGames().get(guild.getId()).getMaster() == author) new MapRegisterHandler(message.getTextChannel(), Main.getTet(), author);
	}
}
