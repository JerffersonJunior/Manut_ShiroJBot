package com.kuuhaku.command.commands.dev;

import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import net.dv8tion.jda.api.entities.*;

import java.io.File;

public class LogCommand extends Command {

	public LogCommand() {
		super("log", "Recupera o log da Shiro.", Category.DEVS);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, String prefix) {
		File log = new File("logs/stacktrace.log");
		try {
			if (log.exists()) channel.sendMessage("Aqui está!").addFile(log).queue();
			else channel.sendMessage(":x: | Arquivo de log não encontrado.").queue();
		} catch (Exception e) {
			channel.sendMessage("Arquivo de log muito grande, por favor faça a leitura diretamente no VPS.").queue();
		}
	}
}
