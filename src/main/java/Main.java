import com.kuuhaku.commands.*;
import com.kuuhaku.controller.Database;
import com.kuuhaku.model.CustomAnswers;
import com.kuuhaku.model.Member;
import com.kuuhaku.model.guildConfig;
import net.dv8tion.jda.core.*;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ReconnectedEvent;
import net.dv8tion.jda.core.events.ShutdownEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class Main extends ListenerAdapter implements JobListener, Job {
    private static JDA bot;
    private static User owner;
    private static TextChannel homeLog;
    private static Map<String, guildConfig> gcMap = new HashMap<>();
    private static Map<String, Member> memberMap = new HashMap<>();
    private static JobDetail backup;
    private static Scheduler sched;
    private static boolean ready = false;
    private static List<CustomAnswers> customAnswersList;

    private static void initBot() throws LoginException {
        JDABuilder jda = new JDABuilder(AccountType.BOT);
        String token = System.getenv("BOT_TOKEN");
        jda.setToken(token);
        jda.addEventListener(new Main());
        jda.build();
        gcMap = Database.getGuildConfigs();
        memberMap = Database.getMembersData();
        customAnswersList = Database.getCustomAnswers();
        if (customAnswersList == null) customAnswersList = new ArrayList<>();
        try {
            if (backup == null) {
                backup = JobBuilder.newJob(Main.class).withIdentity("backup", "1").build();
            }
            Trigger cron = TriggerBuilder.newTrigger().withIdentity("backup", "1").withSchedule(CronScheduleBuilder.cronSchedule("0 0 0/3 ? * * *")).build();
            SchedulerFactory sf = new StdSchedulerFactory();
            try {
                sched = sf.getScheduler();
                sched.scheduleJob(backup, cron);
            } catch (Exception ignore) {
            } finally {
                sched.start();
                System.out.println("Cronograma inicializado com sucesso!");
            }
        } catch (SchedulerException e) {
            System.out.println("Erro ao inicializar cronograma: " + e);
        }
    }

    public static void main(String[] args) {
        try {
            initBot();
            System.out.println("Estou pronta!");
            ready = true;
        } catch (Exception e) {
            System.out.println("Erro ao inicializar bot: " + e);
        }
    }

    @Override
    public void execute(JobExecutionContext context) {
        try {
            Database.sendAllGuildConfigs(gcMap.values());
            Database.sendAllMembersData(memberMap.values());
            System.out.println("Guardar configurações no banco de dados...PRONTO!");
            bot.getPresence().setGame(Owner.getRandomGame(bot));
            gcMap.forEach((k, v) -> {
                if (v.getCanalav() != null && bot.getGuildById(v.getGuildId()).getTextChannelById(v.getCanalav()).canTalk())
                    bot.getGuildById(v.getGuildId()).getTextChannelById(v.getCanalav()).sendMessage(("Opa, está gostando de me utilizar em seu servidor? Caso sim, se puder votar me ajudaria **MUITO** a me tornar cada vez mais popular e ser chamada para mais servidores!\n https://discordbots.org/bot/572413282653306901")).queue();
            });
        } catch (Exception e) {
            System.out.println("Guardar configurações no banco de dados...ERRO!\nErro: " + e);
        }
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        System.out.println("Iniciando backup automático...");
        homeLog.sendMessage("Iniciando backup automático...").queue();
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {

    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        OffsetDateTime odt = OffsetDateTime.now();
        System.out.println("Backup executado com sucesso em " + odt.minusHours(3));
        homeLog.sendMessage("Backup executado com sucesso em " + odt.minusHours(3)).queue();
    }

    @Override
    public void onReady(ReadyEvent event) {
        bot = event.getJDA();
        owner = bot.getUserById("350836145921327115");
        homeLog = bot.getGuildById("421495229594730496").getTextChannelById("573861751884349479");
        bot.getPresence().setGame(Owner.getRandomGame(bot));
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent guild) {
        gcMap.remove(guild.getGuild().getId());
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent user) {
        try {
            if (gcMap.get(user.getGuild().getId()).getCanalbv() != null) {
                Embeds.welcomeEmbed(user, gcMap.get(user.getGuild().getId()).getMsgBoasVindas(), user.getGuild().getTextChannelById(gcMap.get(user.getGuild().getId()).getCanalbv()));
                Map<String, Object> roles = gcMap.get(user.getGuild().getId()).getCargoNew();
                List<Role> list = new ArrayList<>();
                roles.values().forEach(r -> list.add(user.getGuild().getRoleById(r.toString())));
                if (gcMap.get(user.getGuild().getId()).getCargoNew().size() > 0)
                    user.getGuild().getController().addRolesToMember(user.getMember(), list).queue();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent user) {
        try {
            if (gcMap.get(user.getGuild().getId()).getCanalbv() != null) {
                Embeds.byeEmbed(user, gcMap.get(user.getGuild().getId()).getMsgAdeus(), user.getGuild().getTextChannelById(gcMap.get(user.getGuild().getId()).getCanalbv()));
                if (memberMap.get(user.getUser().getId() + user.getGuild().getId()) != null)
                    memberMap.remove(user.getUser().getId() + user.getGuild().getId());
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        User user = event.getUser();
        Message message = event.getChannel().getMessageById(event.getMessageId()).complete();
        List<User> ment = message.getMentionedUsers();
        if (event.getReactionEmote().getName().equals("\ud83d\udc4d")) {
            if (message.getReactions().get(0).getCount() >= 5) message.pin().queue();
        } else if (event.getReactionEmote().getName().equals("\ud83d\udc4e")) {
            if (message.getReactions().get(0).getCount() >= 5) message.delete().queue();
        }
        if (ment.size() > 1) {
            User target = ment.get(0);
            if (ment.get(1) == user && event.getReactionEmote().getName().equals("\u21aa")) {
                System.out.println("Nova reação na mensagem " + event.getMessageId() + " por " + event.getUser().getName() + " | " + event.getGuild().getName());
                MessageBuilder msg = new MessageBuilder();
                msg.setContent(ment.get(1).getAsMention());
                try {
                    if (message.getContentRaw().contains("abraçou")) {
                        Reactions.hug(bot, msg.build(), user, message.getTextChannel(), true, target);
                    } else if (message.getContentRaw().contains("deu um tapa em")) {
                        Reactions.slap(bot, msg.build(), user, message.getTextChannel(), true, target);
                    } else if (message.getContentRaw().contains("destruiu")) {
                        Reactions.smash(bot, msg.build(), user, message.getTextChannel(), true, target);
                    } else if (message.getContentRaw().contains("beijou")) {
                        Reactions.kiss(bot, msg.build(), user, message.getTextChannel(), true, target);
                    } else if (message.getContentRaw().contains("encarou")) {
                        Reactions.stare(bot, user, message.getTextChannel(), true, target);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onShutdown(ShutdownEvent event) {
        System.out.println("Iniciando sequencia de encerramento...");
        try {
            Database.sendAllGuildConfigs(gcMap.values());
            Database.sendAllMembersData(memberMap.values());
            Database.sendAllCustomAnswers(customAnswersList);
            System.out.println("Guardar configurações no banco de dados...PRONTO!");
            System.out.println("Desligando instância...");
            sched.shutdown();
            System.exit(0);
        } catch (Exception e) {
            JDABuilder jda = new JDABuilder(AccountType.BOT);
            String token = System.getenv("BOT_TOKEN");
            jda.setToken(token);
            jda.addEventListener(new Main());
            try {
                jda.build();
            } catch (LoginException ex) {
                ex.printStackTrace();
            }
            System.out.println("Guardar configurações no banco de dados...ERRO!");
            System.out.println("Erro: " + e);
        }
    }

    @Override
    public void onReconnect(ReconnectedEvent event) {
        System.out.println("Voltei!");
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent message) {
        if (ready) {
            try {
                if (message.getChannel().getId().equals(gcMap.get(message.getGuild().getId()).getCanalsug()) && !message.getMessage().getAuthor().isBot()) {
                    message.getMessage().addReaction("\ud83d\udc4d").queue();
                    message.getMessage().addReaction("\ud83d\udc4e").queue();
                }
            } catch (NullPointerException ignore) {
            }
            if (message.getAuthor() == bot.getSelfUser()) {
                try {
                    if (message.getMessage().getContentRaw().contains("abraçou") ||
                            message.getMessage().getContentRaw().contains("deu um tapa em") ||
                            message.getMessage().getContentRaw().contains("destruiu") ||
                            message.getMessage().getContentRaw().contains("beijou") ||
                            message.getMessage().getContentRaw().contains("encarou"))
                        message.getMessage().addReaction("\u21aa").queue();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            try {
                if (message.getAuthor().isBot() || !message.isFromType(ChannelType.TEXT)) return;

                if (message.getMessage().getContentRaw().equals("!init") && gcMap.get(message.getGuild().getId()) == null) {
                    guildConfig gct = new guildConfig();
                    gct.setGuildId(message.getGuild().getId());
                    gcMap.put(message.getGuild().getId(), gct);
                    for (int i = 0; i < message.getGuild().getTextChannels().size(); i++) {
                        if (message.getGuild().getTextChannels().get(i).canTalk()) {
                            message.getGuild().getTextChannels().get(i).sendMessage("Seu servidor está prontinho, estarei a partir de agora ouvindo seus comandos!").queue();
                            break;
                        }
                    }
                } else if (message.getMessage().getContentRaw().equals("!init") && gcMap.get(message.getGuild().getId()) != null) {
                    message.getChannel().sendMessage("As configurações deste servidor ja foram inicializadas!").queue();
                }
                if (gcMap.get(message.getGuild().getId()) != null && message.getTextChannel().canTalk()) {
                    if (gcMap.get(message.getGuild().getId()).isAnyPlace()) {
                        try {
                            List<CustomAnswers> ca = customAnswersList.stream().filter(a -> a.getGuildID().equals(message.getGuild().getId()) && StringUtils.containsIgnoreCase(message.getMessage().getContentRaw(), a.getTrigger())).collect(Collectors.toList());
                            message.getChannel().sendMessage(ca.get(new Random().nextInt(ca.size())).getAnswer()).queue();
                        } catch (Exception ignore) {
                        }
                    } else {
                        try {
                            List<CustomAnswers> ca = customAnswersList.stream().filter(a -> a.getGuildID().equals(message.getGuild().getId()) && message.getMessage().getContentRaw().equalsIgnoreCase(a.getTrigger())).collect(Collectors.toList());
                            message.getChannel().sendMessage(ca.get(new Random().nextInt(ca.size())).getAnswer()).queue();
                        } catch (Exception ignore) {
                        }
                    }
                    if (memberMap.get(message.getAuthor().getId() + message.getGuild().getId()) != null && !message.getMessage().getContentRaw().startsWith(gcMap.get(message.getGuild().getId()).getPrefix())) {
                        boolean lvlUp;
                        lvlUp = memberMap.get(message.getAuthor().getId() + message.getGuild().getId()).addXp();
                        if (lvlUp) {
                            if (gcMap.get(message.getGuild().getId()).getLvlNotif())
                                message.getChannel().sendMessage(message.getAuthor().getAsMention() + " subiu para o level " + memberMap.get(message.getAuthor().getId() + message.getGuild().getId()).getLevel() + ". GGWP!! :tada:").queue();
                            if (gcMap.get(message.getGuild().getId()).getCargoslvl().containsKey(Integer.toString(memberMap.get(message.getAuthor().getId() + message.getGuild().getId()).getLevel()))) {
                                Member member = memberMap.get(message.getAuthor().getId() + message.getGuild().getId());
                                String roleID = (String) gcMap.get(message.getGuild().getId()).getCargoslvl().get(Integer.toString(member.getLevel()));

                                message.getGuild().getController().addRolesToMember(message.getMember(), message.getGuild().getRoleById(roleID)).queue();
                                message.getChannel().sendMessage(message.getAuthor().getAsMention() + " ganhou o cargo " + message.getGuild().getRoleById(roleID).getAsMention() + ". Parabéns!").queue();
                            }
                        }
                    }
                    if (message.getMessage().getContentRaw().startsWith(gcMap.get(message.getGuild().getId()).getPrefix())) {

                        if (memberMap.get(message.getAuthor().getId() + message.getGuild().getId()) == null) {
                            Member m = new Member();
                            m.setId(message.getAuthor().getId() + message.getGuild().getId());
                            memberMap.put(message.getAuthor().getId() + message.getGuild().getId(), m);
                        }

                        System.out.println("Comando recebido de " + message.getAuthor().getName() + "#" + message.getAuthor().getDiscriminator() + " | " + message.getGuild().getName() + " -> " + message.getMessage().getContentDisplay());

                        String[] cmd = message.getMessage().getContentRaw().split(" ");

                        //GERAL--------------------------------------------------------------------------------->

                        if (hasPrefix(message, "ping")) {
                            message.getChannel().sendMessage("Pong! :ping_pong: " + bot.getPing() + " ms").queue();
                        } else if (hasPrefix(message, "bug")) {
                            owner.openPrivateChannel().queue(channel -> channel.sendMessage(Embeds.bugReport(message, gcMap.get(message.getGuild().getId()).getPrefix())).queue());
                        } else if (hasPrefix(message, "uptime")) {
                            Misc.uptime(message);
                        } else if (hasPrefix(message, "ajuda")) {
                            Misc.help(message, gcMap.get(message.getGuild().getId()).getPrefix(), owner);
                        } else if (hasPrefix(message, "avatar")) {
                            if (message.getMessage().getMentionedUsers().size() > 0) {
                                message.getChannel().sendMessage("Avatar de " + message.getMessage().getMentionedUsers().get(0).getAsMention() + ":\n" + message.getMessage().getMentionedUsers().get(0).getAvatarUrl()).queue();
                            } else if (message.getMessage().getContentRaw().replace(gcMap.get(message.getGuild().getId()).getPrefix() + "avatar", "").trim().equals("guild")) {
                                message.getChannel().sendMessage("Avatar do server:\n" + message.getMessage().getGuild().getIconUrl()).queue();
                            } else {
                                message.getChannel().sendMessage("Você precisa mencionar alguém!").queue();
                            }
                        } else if (hasPrefix(message, "imagem")) {
                            Misc.image(message, cmd);
                        } else if (hasPrefix(message, "pergunta")) {
                            Misc.yesNo(message);
                        } else if (hasPrefix(message, "escolha")) {
                            Misc.choose(message, cmd[0]);
                        } else if (hasPrefix(message, "anime")) {
                            try {
                                Embeds.animeEmbed(message, cmd[0]);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else if (hasPrefix(message, "xp")) {
                            try {
                                Embeds.levelEmbed(message, memberMap.get(message.getAuthor().getId() + message.getGuild().getId()), gcMap.get(message.getGuild().getId()).getPrefix());
                            } catch (IOException e) {
                                System.out.println(e.toString());
                            }
                        } else if (hasPrefix(message, "conquista")) {
                            if (message.getGuild().getId().equals("421495229594730496")) {
                                Misc.badges(message);
                            } else {
                                message.getChannel().sendMessage(":x: Você está no servidor errado, este comando é exclusivo do servidor OtagamerZ!").queue();
                            }
                        } else if (hasPrefix(message, "conquistas")) {
                            if (message.getGuild().getId().equals("421495229594730496")) {
                                try {
                                    Embeds.myBadgesEmbed(message, memberMap.get(message.getAuthor().getId() + message.getGuild().getId()));
                                } catch (IOException e) {
                                    System.out.println(e.toString());
                                }
                            } else {
                                message.getChannel().sendMessage(":x: Você está no servidor errado, este comando é exclusivo do servidor OtagamerZ!").queue();
                            }
                        } else if (hasPrefix(message, "vemca")) {
                            if (message.getMessage().getMentionedUsers().size() != 0) {
                                Reactions.hug(bot, message.getMessage(), message.getMessage().getMentionedUsers().get(0), message.getTextChannel(), false, message.getAuthor());
                            } else {
                                message.getChannel().sendMessage(":x: Você precisa mencionar um usuário!").queue();
                            }
                        } else if (hasPrefix(message, "meee")) {
                            Reactions.facedesk(message.getMessage());
                        } else if (hasPrefix(message, "sqn")) {
                            Reactions.nope(message.getMessage());
                        } else if (hasPrefix(message, "corre")) {
                            Reactions.run(message.getMessage());
                        } else if (hasPrefix(message, "baka")) {
                            Reactions.blush(message.getMessage());
                        } else if (hasPrefix(message, "kkk")) {
                            Reactions.laugh(message.getMessage());
                        } else if (hasPrefix(message, "triste")) {
                            Reactions.sad(message.getMessage());
                        } else if (hasPrefix(message, "tapa")) {
                            if (message.getMessage().getMentionedUsers().size() != 0) {
                                Reactions.slap(bot, message.getMessage(), message.getMessage().getMentionedUsers().get(0), message.getTextChannel(), false, message.getAuthor());
                            } else {
                                message.getChannel().sendMessage(":x: Você precisa mencionar um usuário!").queue();
                            }
                        } else if (hasPrefix(message, "chega")) {
                            if (message.getMessage().getMentionedUsers().size() != 0) {
                                Reactions.smash(bot, message.getMessage(), message.getMessage().getMentionedUsers().get(0), message.getTextChannel(), false, message.getAuthor());
                            } else {
                                message.getChannel().sendMessage(":x: Você precisa mencionar um usuário!").queue();
                            }
                        } else if (hasPrefix(message, "encarar")) {
                            if (message.getMessage().getMentionedUsers().size() != 0) {
                                Reactions.stare(bot, message.getMessage().getMentionedUsers().get(0), message.getTextChannel(), false, message.getAuthor());
                            } else {
                                message.getChannel().sendMessage(":x: Você precisa mencionar um usuário!").queue();
                            }
                        } else if (hasPrefix(message, "beijar")) {
                            if (message.getMessage().getMentionedUsers().size() != 0) {
                                Reactions.kiss(bot, message.getMessage(), message.getMessage().getMentionedUsers().get(0), message.getTextChannel(), false, message.getAuthor());
                            } else {
                                message.getChannel().sendMessage(":x: Você precisa mencionar um usuário!").queue();
                            }
                        } else if (hasPrefix(message, "embed")) {
                            try {
                                Embeds.makeEmbed(message, message.getMessage().getContentRaw().replace(gcMap.get(message.getGuild().getId()).getPrefix() + "embed ", ""));
                            } catch (Exception e) {
                                message.getChannel().sendMessage("Ops, me parece que o link imagem não está correto, veja bem se incluiu tudo!").queue();
                            }
                        }

                        //DONO--------------------------------------------------------------------------------->

                        if (message.getAuthor() == owner) {
                            if (hasPrefix(message, "restart")) {
                                message.getChannel().sendMessage("Sayonara, Nii-chan!").queue();
                                bot.shutdown();
                            } else if (hasPrefix(message, "servers")) {
                                Owner.getServers(bot, message);
                            } else if (hasPrefix(message, "gmap")) {
                                Owner.getGuildMap(message, gcMap);
                            } else if (hasPrefix(message, "mmap")) {
                                Owner.getMemberMap(message, memberMap);
                            } else if (hasPrefix(message, "amap")) {
                                Owner.getAnswersMap(message, customAnswersList);
                            } else if (hasPrefix(message, "broadcast")) {
                                Owner.broadcast(gcMap, bot, message.getMessage().getContentRaw().replace(gcMap.get(message.getGuild().getId()).getPrefix() + "broadcast ", ""), message.getTextChannel());
                            } else if (hasPrefix(message, "perms")) {
                                Owner.listPerms(bot, message);
                            } else if (hasPrefix(message, "leave")) {
                                Owner.leave(bot, message);
                            } else if (hasPrefix(message, "dar")) {
                                try {
                                    memberMap.get(message.getMessage().getMentionedUsers().get(0).getId() + message.getGuild().getId()).giveBadge(cmd[2]);
                                    message.getChannel().sendMessage("Parabéns, " + message.getMessage().getMentionedUsers().get(0).getAsMention() + " completou a conquista Nº " + cmd[2]).queue();
                                } catch (Exception e) {
                                    message.getChannel().sendMessage(":x: Ué, não estou conseguindo marcar a conquista como completa. Tenha certeza de digitar o comando neste formato: " + gcMap.get(message.getGuild().getId()).getPrefix() + "dar [MEMBRO] [Nº]").queue();
                                }
                            } else if (hasPrefix(message, "tirar")) {
                                try {
                                    memberMap.get(message.getMessage().getMentionedUsers().get(0).getId() + message.getGuild().getId()).removeBadge(cmd[2]);
                                    message.getChannel().sendMessage("Meeee, " + message.getMessage().getMentionedUsers().get(0).getAsMention() + " teve a conquista Nº " + cmd[2] + " retirada de sua posse!").queue();
                                } catch (Exception e) {
                                    message.getChannel().sendMessage(":x: Ué, não estou conseguindo marcar a conquista como incompleta. Tenha certeza de digitar o comando neste formato: " + gcMap.get(message.getGuild().getId()).getPrefix() + "tirar [MEMBRO] [Nº]").queue();
                                }
                            }
                        }

                        //ADMIN--------------------------------------------------------------------------------->
                        if (message.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
                            if (hasPrefix(message, "definir")) {
                                Admin.config(cmd, message, gcMap.get(message.getGuild().getId()));
                            } else if (hasPrefix(message, "configs")) {
                                Embeds.configsEmbed(message, gcMap.get(message.getGuild().getId()));
                            } else if (hasPrefix(message, "punir")) {
                                if (message.getMessage().getMentionedUsers() != null) {
                                    memberMap.get(message.getMessage().getMentionedUsers().get(0).getId() + message.getGuild().getId()).resetXp();
                                    message.getChannel().sendMessage(message.getMessage().getMentionedUsers().get(0).getAsMention() + " teve seus XP e leveis resetados!").queue();
                                } else {
                                    message.getChannel().sendMessage(":x: Você precisa me dizer de quem devo resetar o XP.").queue();
                                }
                            } else if (hasPrefix(message, "alertar")) {
                                if (message.getMessage().getMentionedUsers() != null && cmd.length >= 3) {
                                    Admin.addWarn(message, message.getMessage().getContentRaw().replace(gcMap.get(message.getGuild().getId()).getPrefix(), ""), memberMap);
                                } else {
                                    message.getChannel().sendMessage(":x: Você precisa mencionar um usuário e dizer o motivo do alerta.").queue();
                                }
                            } else if (hasPrefix(message, "perdoar")) {
                                if (message.getMessage().getMentionedUsers() != null && cmd.length >= 3) {
                                    Admin.takeWarn(message, memberMap);
                                } else {
                                    message.getChannel().sendMessage(":x: Você precisa mencionar um usuário e dizer o Nº do alerta a ser removido.").queue();
                                }
                            } else if (hasPrefix(message, "rcargolvl")) {
                                if (cmd.length == 2) {
                                    Map<String, Object> cargos = gcMap.get(message.getGuild().getId()).getCargoslvl();
                                    cargos.remove(cmd[1]);
                                    gcMap.get(message.getGuild().getId()).setCargoslvl(new JSONObject(cargos));
                                    message.getChannel().sendMessage("Retirada a recompensa de cargo do level " + cmd[1] + " com sucesso!").queue();
                                } else {
                                    message.getChannel().sendMessage("Opa, algo deu errado, lembre-se de especificar apenas o level.").queue();
                                }
                            } else if (hasPrefix(message, "lvlnotif")) {
                                if (gcMap.get(message.getGuild().getId()).getLvlNotif()) {
                                    message.getChannel().sendMessage("Não irei mais avisar quando um membro passar de nível!").queue();
                                    gcMap.get(message.getGuild().getId()).setLvlNotif(false);
                                } else {
                                    message.getChannel().sendMessage("Agora irei avisar quando um membro passar de nível!").queue();
                                    gcMap.get(message.getGuild().getId()).setLvlNotif(true);
                                }
                            } else if (hasPrefix(message, "faleseachar")) {
                                gcMap.get(message.getGuild().getId()).setAnyPlace(!gcMap.get(message.getGuild().getId()).isAnyPlace());
                                message.getChannel().sendMessage(gcMap.get(message.getGuild().getId()).isAnyPlace() ? "Irei responder sempre que eu achar as palavras-chave, mesmo dentro de uma frase" : "Só irei responder se a mensagem for EXATAMENTE a palavra-chave!").queue();
                            } else if (hasPrefix(message, "ouçatodos")) {
                                gcMap.get(message.getGuild().getId()).setAnyTell(!gcMap.get(message.getGuild().getId()).isAnyTell());
                                message.getChannel().sendMessage(gcMap.get(message.getGuild().getId()).isAnyTell() ? "Irei ouvir novas respostas da comunidade agora!" : "Só irei ouvir novas respostas de um moderador!").queue();
                            }
                        }

                        if (message.getMember().hasPermission(Permission.MANAGE_CHANNEL) || gcMap.get(message.getGuild().getId()).isAnyTell()) {
                            if (hasPrefix(message, "fale")) {
                                if (message.getMessage().getContentRaw().contains(";") && cmd.length > 1) {
                                    CustomAnswers ca = new CustomAnswers();
                                    String com = message.getMessage().getContentRaw().replace(gcMap.get(message.getGuild().getId()).getPrefix() + "fale", "").trim();

                                    ca.setGuildID(message.getGuild().getId());
                                    ca.setTrigger(com.split(";")[0]);
                                    ca.setAnswer(com.split(";")[1]);
                                    message.getChannel().sendMessage("Quando alguém falar `" + com.split(";")[0] + "` irei responder `" + com.split(";")[1] + "`.").queue();
                                    customAnswersList.add(ca);
                                } else {
                                    message.getChannel().sendMessage("Você não me passou argumentos suficientes!").queue();
                                }
                            } else if (hasPrefix(message, "nãofale")) {
                                if (cmd.length > 1) {
                                    try {
                                        if (customAnswersList.stream().anyMatch(a -> a.getGuildID().equals(message.getGuild().getId()) && a.getId() == Integer.parseInt(cmd[1]))) {
                                            String answer = customAnswersList.stream().filter(a -> a.getGuildID().equals(message.getGuild().getId()) && a.getId() == Integer.parseInt(cmd[1])).collect(Collectors.toList()).get(0).getAnswer();
                                            String trigger = customAnswersList.stream().filter(a -> a.getGuildID().equals(message.getGuild().getId()) && a.getId() == Integer.parseInt(cmd[1])).collect(Collectors.toList()).get(0).getTrigger();
                                            message.getChannel().sendMessage("Não irei mais responder `" + answer + "` quando alguém dizer `" + trigger + "`.").queue();
                                            customAnswersList.remove(Integer.parseInt(cmd[1]));
                                        }
                                    } catch (NumberFormatException e) {
                                        message.getChannel().sendMessage("Você não me passou um ID válido!").queue();
                                    }
                                } else {
                                    message.getChannel().sendMessage("Você precisa me passar um ID para que eu possa excluir uma resposta!").queue();
                                }
                            } else if (hasPrefix(message, "falealista")) {
                                if (cmd.length > 1)
                                    Embeds.answerList(message, customAnswersList.stream().filter(a -> a.getGuildID().equals(message.getGuild().getId())).collect(Collectors.toList()));
                                else
                                    message.getChannel().sendMessage("Você precisa me dizer uma página (serão mostradas 5 respostas por página)!").queue();
                            }
                        }
                    }
                } else if (message.getTextChannel().canTalk()) {
                    message.getChannel().sendMessage("Por favor, digite __**!init**__ para inicializar as configurações da Shiro em seu servidor!").queue();
                }
            } catch (NullPointerException | InsufficientPermissionException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean hasPrefix(MessageReceivedEvent message, String cmd) {
        return message.getMessage().getContentRaw().split(" ")[0].equalsIgnoreCase(gcMap.get(message.getGuild().getId()).getPrefix() + cmd);
    }
}
