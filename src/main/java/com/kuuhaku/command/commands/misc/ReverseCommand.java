package com.kuuhaku.command.commands.misc;

import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.Event;

public class ReverseCommand extends Command {
	
	public ReverseCommand() {
		super("reverse", new String[] {"inverter"}, "<texto>", "Inverte o texto fornecido.", Category.MISC);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, Event event, String prefix) {
		
		if(args.length < 1) { channel.sendMessage(":x: | Voc� precisa de indicar o texto que deseja inverter.").queue(); return; }
		
        String txt = "";

        for(String arg : args) {
        	txt += arg + " ";
        }
        
        txt = new StringBuilder(txt.trim()).reverse().toString();
        channel.sendMessage(txt).queue();
	}

}
