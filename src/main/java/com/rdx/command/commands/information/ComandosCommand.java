package com.rdx.command.commands.information;

import com.rdx.Main;
import com.rdx.command.Category;
import com.rdx.command.Command;
import com.rdx.model.guildConfig;
import com.rdx.utils.Helper;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.Event;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ComandosCommand extends Command {

	public static Map<String, guildConfig> gcMap = new HashMap<>();

	public ComandosCommand() {
		super("comandos", new String[] {"cmds", "cmd", "comando"}, "Fornece uma lista de todos os comandos disponiveis no bot.", Category.INFO);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, Event event, String prefix) {

			EmbedBuilder eb = new EmbedBuilder();
			eb.setColor(Color.PINK);
			eb.setFooter(Main.getInfo().getFullName(), null);
			eb.setThumbnail(Main.getInfo().getSelfUser().getAvatarUrl());

			if(args.length == 0) {

				eb.setTitle("**Lista de Comandos**");

				//eb.setDescription("Prefixo: `" + gcMap.get(guild.getId()).getPrefix() + "`\n"
				eb.setDescription("Prefixo: `!`\n"
						+ Category.values().length + " categorias encontradas!" + "\n"
						+ Main.getCommandManager().getCommands().size() + " comandos encontrados!"
						+ "\n" + Helper.VOID);

				for(Category cat : Category.values()) {
					if(!cat.isEnabled())
						continue;
					if(cat.getCmds().size()==0) {
						eb.addField(cat.getName(), cat.getDescription() + "\n*Ainda não existem comandos nesta categoria.*", false);
						continue;
					}

					String cmds = "";

					for(Command cmd : cat.getCmds()) { cmds += "`" + cmd.getName() + "` "; }

					eb.addField(cat.getName(), cat.getDescription() + "\n" + cmds.trim(), false);
				}

				//eb.addField(Helper.VOID, "Para informações sobre um comando em especifico digite `" + gcMap.get(guild.getId()).getPrefix() + "cmds [comando]`.", false);
				eb.addField(Helper.VOID, "Para informações sobre um comando em especifico digite `!cmds [comando]`.", false);

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

			String aliases = "**Aliases**: ";

			for(String al : cmd.getAliases()) { aliases += "`"+al+"` "; }

			eb.setDescription(cmd.getDescription() + "\n"
					+ (cmd.getAliases().length!=0 ? aliases.trim() + "\n" : "")
					+ "**Categoria**: " + cmd.getCategory().getName()
					+ "\n");

			channel.sendMessage(eb.build()).queue();
		
	}

}
