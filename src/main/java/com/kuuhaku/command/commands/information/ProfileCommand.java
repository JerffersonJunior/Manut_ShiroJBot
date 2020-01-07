package com.kuuhaku.command.commands.information;

import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import com.kuuhaku.model.Profile;
import com.kuuhaku.utils.Helper;
import net.dv8tion.jda.api.entities.*;

import java.io.IOException;

public class ProfileCommand extends Command {

    public ProfileCommand() {
        super("perfil", new String[]{"xp", "profile", "pf"}, "Mostra dados sobre você neste servidor.", Category.INFO);
    }

    @Override
    public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, String prefix) {
        channel.sendMessage("<a:Loading:598500653215645697> Gerando perfil...").queue(m -> {
            try {
                channel.sendMessage(":video_game: Perfil de " + author.getAsMention()).addFile(Profile.makeProfile(member, guild).toByteArray(), "perfil.png").queue(s -> m.delete().queue());
            } catch (IOException e) {
                m.editMessage(":x: | Epa, teve um errinho aqui enquanto eu gerava o perfil, meus criadores já foram notificados!").queue();
                Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
            }
        });
    }
}
