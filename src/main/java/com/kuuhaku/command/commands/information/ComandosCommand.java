package com.kuuhaku.command.commands.information;

import com.kuuhaku.Main;
import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import com.kuuhaku.utils.Helper;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.Event;

import java.awt.*;

public class ComandosCommand extends Command {

	public ComandosCommand() {
		super("comandos", new String[] {"cmds", "cmd", "comando", "ajuda"}, "Fornece uma lista de todos os comandos disponiveis no bot.", Category.INFO);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, Event event, String prefix) {

			EmbedBuilder eb = new EmbedBuilder();
			eb.setColor(Color.PINK);
			eb.setFooter(Main.getInfo().getFullName(), null);
			eb.setThumbnail("https://cdn.pixabay.com/photo/2012/04/14/16/26/question-34499_960_720.png");

			if(args.length == 0) {

				eb.setTitle("**Lista de Comandos**");

				eb.setDescription("Prefixo: `" + prefix + "`\n"
						+ Category.values().length + " categorias encontradas!" + "\n"
						+ Main.getCommandManager().getCommands().size() + " comandos encontrados!"
						+ "\n" + Helper.VOID);

				for(Category cat : Category.values()) {
					if(cat.isEnabled())
						continue;
					if(cat.getCmds().size()==0) {
						eb.addField(cat.getName(), cat.getDescription() + "\n*Ainda não existem comandos nesta categoria.*", false);
						continue;
					}

					StringBuilder cmds = new StringBuilder();

					for(Command cmd : cat.getCmds()) { cmds.append("`").append(cmd.getName()).append("` "); }

					eb.addField(cat.getName(), cat.getDescription() + "\n" + cmds.toString().trim(), false);
				}

				eb.addField(Helper.VOID, "Para informações sobre um comando em especifico digite `" + prefix + "cmds [comando]`.", false);

				channel.sendMessage(eb.build()).queue();
				return;
			}

			String cmdName = args[0];

			Command cmd = null;

			for(Command cmmd : Main.getCommandManager().getCommands()) {
				boolean found = false;
				if(cmmd.getName().equalsIgnoreCase(cmdName)) { found = true; }

				for(String alias : cmmd.getAliases()) { if(alias.equalsIgnoreCase(cmdName)) { found = true; } }

				if(found) {
					cmd = cmmd;
					break;
				}
			}

			if(cmd==null) {
				channel.sendMessage(":x: | Esse comando não foi encontrado!").queue();
				return;
			}

			eb.setTitle(cmd.getName() + (cmd.getUsage()!=null ? " " + cmd.getUsage() : ""));

			StringBuilder aliases = new StringBuilder("**Aliases**: ");

			for(String al : cmd.getAliases()) { aliases.append("`").append(al).append("` "); }

			eb.setDescription(cmd.getDescription() + "\n"
					+ (cmd.getAliases().length!=0 ? aliases.toString().trim() + "\n" : "")
					+ "**Categoria**: " + cmd.getCategory().getName()
					+ "\n");

			channel.sendMessage(eb.build()).queue();
		
	}
}
