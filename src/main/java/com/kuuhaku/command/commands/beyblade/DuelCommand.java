package com.kuuhaku.command.commands.beyblade;

import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import com.kuuhaku.controller.MySQL;
import com.kuuhaku.events.JDAEvents;
import com.kuuhaku.model.DuelData;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.Event;

public class DuelCommand extends Command {

    public DuelCommand() {
        super("bduel", new String[]{"bduelar", "desafiar"}, "<@usuário>", "Desafia um usuário para um duelo de Beyblades.", Category.BEYBLADE);
    }

    @Override
    public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, Event event, String prefix) {
        if (MySQL.getBeybladeById(author.getId()) == null) {
            channel.sendMessage(":x: | Você não possui uma Beyblade.").queue();
            return;
        } else if (message.getMentionedUsers().size() == 0) {
            channel.sendMessage(":x: | Você precisa mencionar um usuário.").queue();
        } else if (MySQL.getBeybladeById(message.getMentionedUsers().get(0).getId()) == null) {
            channel.sendMessage(":x: | Este usuário não possui uma Beyblade.").queue();
        }

        if (message.getMentionedUsers().size() > 0) {
            if (MySQL.getBeybladeById(message.getMentionedUsers().get(0).getId()) != null) {
                DuelData dd = new DuelData(message.getAuthor(), message.getMentionedUsers().get(0));
                if (JDAEvents.duels.containsValue(dd)) message.getChannel().sendTyping().queue(tm -> message.getChannel().sendMessage("Você já possui um duelo pendente!").queue());
                else message.getChannel().sendTyping().queue(tm -> message.getChannel().sendMessage(message.getMentionedMembers().get(0).getAsMention() + ", você foi desafiado a um duelo de Beyblades por " + message.getAuthor().getAsMention() + ". Se deseja aceitar, clique no botão abaixo:").queue(m -> {
                            m.addReaction("\u2694").queue();
                            JDAEvents.duels.put(m.getId(), dd);
                        }
                ));
            } else {
                message.getChannel().sendTyping().queue(tm -> message.getChannel().sendMessage("Este usuário ainda não possui uma Beyblade.").queue());
            }
        }
    }
}
