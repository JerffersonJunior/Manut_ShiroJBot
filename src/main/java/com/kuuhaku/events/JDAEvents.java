/*
 * This file is part of Shiro J Bot.
 *
 *     Shiro J Bot is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Shiro J Bot is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Shiro J Bot.  If not, see <https://www.gnu.org/licenses/>
 */

package com.kuuhaku.events;

import com.kuuhaku.Main;
import com.kuuhaku.command.commands.Reactions.*;
import com.kuuhaku.controller.SQLite;
import com.kuuhaku.model.DuelData;
import com.kuuhaku.model.guildConfig;
import com.kuuhaku.utils.Helper;
import com.kuuhaku.utils.LogLevel;
import de.androidpit.colorthief.ColorThief;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ShutdownEvent;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JDAEvents extends ListenerAdapter {
    public static List<DuelData> dd = new ArrayList<>();
    public static Map<String, DuelData> duels = new HashMap<>();

    @Override
    public void onReady(ReadyEvent event) {
        try {
            Helper.log(this.getClass(), LogLevel.INFO, "Estou pronta!");
        } catch (Exception e) {
            Helper.log(this.getClass(), LogLevel.ERROR, "Erro ao inicializar bot: " + e);
        }
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        Message message = event.getChannel().getMessageById(event.getMessageId()).complete();
        if (message.getAuthor() == Main.getInfo().getSelfUser() && event.getUser() != Main.getInfo().getAPI().getSelfUser()) {
            if (message.getContentRaw().contains("abraçou")) {
                User author = message.getMentionedUsers().get(0);
                MessageChannel channel = message.getChannel();

                new HugReaction(true).execute(author, null, null, null, message, channel, null, null, null);
            } else if (message.getContentRaw().contains("beijou")) {
                User author = message.getMentionedUsers().get(0);
                MessageChannel channel = message.getChannel();

                new KissReaction(true).execute(author, null, null, null, message, channel, null, null, null);
            } else if (message.getContentRaw().contains("fez cafuné em")) {
                User author = message.getMentionedUsers().get(0);
                MessageChannel channel = message.getChannel();

                new PatReaction(true).execute(author, null, null, null, message, channel, null, null, null);
            } else if (message.getContentRaw().contains("encarou")) {
                User author = message.getMentionedUsers().get(0);
                MessageChannel channel = message.getChannel();

                new StareReaction(true).execute(author, null, null, null, message, channel, null, null, null);
            } else if (message.getContentRaw().contains("deu um tapa em")) {
                User author = message.getMentionedUsers().get(0);
                MessageChannel channel = message.getChannel();

                new SlapReaction(true).execute(author, null, null, null, message, channel, null, null, null);
            } else if (message.getContentRaw().contains("socou")) {
                User author = message.getMentionedUsers().get(0);
                MessageChannel channel = message.getChannel();

                new PunchReaction(true).execute(author, null, null, null, message, channel, null, null, null);
            } else if (message.getContentRaw().contains("mordeu")) {
                User author = message.getMentionedUsers().get(0);
                MessageChannel channel = message.getChannel();

                new BiteReaction(true).execute(author, null, null, null, message, channel, null, null, null);
            }

            if (duels.containsKey(event.getMessageId()) && event.getUser() == duels.get((event.getMessageId())).getP2()) {
                dd.add(duels.get(event.getMessageId()));
                duels.remove(event.getMessageId());
                event.getChannel().sendMessage("O duelo começou!\nUsem `atacar` para atacar, `defender` para defender ou `especial` para tentar utilizar seu poder especial de alinhamento.\n\n**O desafiante começa primeiro!**").queue();
            }
        }
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        SQLite.addGuildToDB(event.getGuild());
        try {
            Helper.sendPM(event.getGuild().getOwner().getUser(), "Obrigada por me adicionar ao seu servidor!");
        } catch (Exception err) {
            TextChannel dch = event.getGuild().getDefaultChannel();
            if (dch != null) {
                if (dch.canTalk()) {
                    dch.sendMessage("Obrigada por me adicionar ao seu servidor!").queue();
                }
            }
        }
    }

    /*@Override
	public void onReconnect(ReconnectedEvent event) {
		MainANT.getInfo().getLogChannel().sendMessage(DiscordHelper.getCustomEmoteMention(MainANT.getInfo().getGuild(), "kawaii") + " | Fui desparalizada!").queue();
	}*/

    @Override
    public void onShutdown(ShutdownEvent event) {
        //com.kuuhaku.MainANT.getInfo().getLogChannel().sendMessage(DiscordHelper.getCustomEmoteMention(com.kuuhaku.MainANT.getInfo().getGuild(), "choro") + " | Nunca vos esquecerei... Faleci! " + DiscordHelper.getCustomEmoteMention(com.kuuhaku.MainANT.getInfo().getGuild(), "bruh")).queue();
    }
	
	/*@Override
	public void onDisconnect(DisconnectEvent event) {
		com.kuuhaku.MainANT.getInfo().getLogChannel().sendMessage(DiscordHelper.getCustomEmoteMention(com.kuuhaku.MainANT.getInfo().getGuild(), "kms") + " | Fui paraliz-... " + DiscordHelper.getCustomEmoteMention(com.kuuhaku.MainANT.getInfo().getGuild(), "yeetus")).queue();
	}*/

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        try {
            guildConfig gc = SQLite.getGuildById(event.getGuild().getId());

            if (!gc.getMsgBoasVindas().equals("")) {
                URL url = new URL(event.getUser().getAvatarUrl());
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestProperty("User-Agent", "Mozilla/5.0");
                BufferedImage image = ImageIO.read(con.getInputStream());

                EmbedBuilder eb = new EmbedBuilder();

                eb.setAuthor(event.getUser().getAsTag(), event.getUser().getAvatarUrl(), event.getUser().getAvatarUrl());
                eb.setColor(new Color(ColorThief.getColor(image)[0], ColorThief.getColor(image)[1], ColorThief.getColor(image)[2]));
                eb.setDescription(gc.getMsgBoasVindas().replace("%user%", event.getUser().getName()).replace("%guild%", event.getGuild().getName()));
                eb.setThumbnail(event.getUser().getAvatarUrl());
                eb.setFooter("ID do usuário: " + event.getUser().getId(), event.getGuild().getIconUrl());
                switch ((int) (Math.random() * 5)) {
                    case 0:
                        eb.setTitle("Opa, parece que temos um novo membro?");
                        break;
                    case 1:
                        eb.setTitle("Mais um membro para nosso lindo servidor!");
                        break;
                    case 2:
                        eb.setTitle("Um novo jogador entrou na partida, pressione start 2P!");
                        break;
                    case 3:
                        eb.setTitle("Agora podemos iniciar a teamfight, um novo membro veio nos ajudar!");
                        break;
                    case 4:
                        eb.setTitle("Bem-vindo ao nosso servidor, puxe uma cadeira e fique à vontade!");
                        break;
                }

                Main.getInfo().getAPI().getGuildById(event.getGuild().getId()).getTextChannelById(gc.getCanalBV()).sendMessage(event.getUser().getAsMention()).embed(eb.build()).queue();
            }
        } catch (Exception ignore) {
        }
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
        try {
            guildConfig gc = SQLite.getGuildById(event.getGuild().getId());

            if (!gc.getMsgAdeus().equals("")) {
                URL url = new URL(event.getUser().getAvatarUrl());
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestProperty("User-Agent", "Mozilla/5.0");
                BufferedImage image = ImageIO.read(con.getInputStream());

                int rmsg = (int) (Math.random() * 5);

                EmbedBuilder eb = new EmbedBuilder();

                eb.setAuthor(event.getUser().getAsTag(), event.getUser().getAvatarUrl(), event.getUser().getAvatarUrl());
                eb.setColor(new Color(ColorThief.getColor(image)[0], ColorThief.getColor(image)[1], ColorThief.getColor(image)[2]));
                eb.setThumbnail(event.getUser().getAvatarUrl());
                eb.setDescription(gc.getMsgAdeus().replace("%user%", event.getUser().getName()).replace("%guild%", event.getGuild().getName()));
                eb.setFooter("ID do usuário: " + event.getUser().getId() + "\n\nServidor gerenciado por " + event.getGuild().getOwner().getEffectiveName(), event.getGuild().getOwner().getUser().getAvatarUrl());
                switch (rmsg) {
                    case 0:
                        eb.setTitle("Nãããoo...um membro deixou este servidor!");
                        break;
                    case 1:
                        eb.setTitle("O quê? Temos um membro a menos neste servidor!");
                        break;
                    case 2:
                        eb.setTitle("Alguém saiu do servidor, deve ter acabado a pilha, só pode!");
                        break;
                    case 3:
                        eb.setTitle("Bem, alguém não está mais neste servidor, que pena!");
                        break;
                    case 4:
                        eb.setTitle("Saíram do servidor bem no meio de uma teamfight, da pra acreditar?");
                        break;
                }

                Main.getInfo().getAPI().getGuildById(event.getGuild().getId()).getTextChannelById(gc.getCanalAdeus()).sendMessage(eb.build()).queue();
            }
        } catch (Exception ignore) {
        }
    }
}
