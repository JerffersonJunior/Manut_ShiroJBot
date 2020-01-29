/*
 * This file is part of Shiro J Bot.
 *
 * Shiro J Bot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Shiro J Bot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Shiro J Bot.  If not, see <https://www.gnu.org/licenses/>
 */

package com.kuuhaku.command.commands.fun;

import com.kuuhaku.command.Category;
import com.kuuhaku.command.Command;
import com.kuuhaku.model.common.Profile;
import com.kuuhaku.utils.Helper;
import net.dv8tion.jda.api.entities.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

public class HardDecisionCommand extends Command {

	public HardDecisionCommand() {
		super("doisbotoes", new String[]{"tb", "twobuttons", "buttons"}, "<opção 1>;<opção 2>", "Gera um meme no formato \"Dois botões\"", Category.FUN);
	}

	@Override
	public void execute(User author, Member member, String rawCmd, String[] args, Message message, MessageChannel channel, Guild guild, String prefix) {

		if (args.length < 1) {
			channel.sendMessage(":x: | Você tem que escrever a mensagem que deseja que apareca no meme.").queue();
			return;
		} else if (String.join(" ", args).split(";").length < 2) {
			channel.sendMessage(":x: | Você precisa escrever duas opções para o meme (separados por ponto-e-vírgula).").queue();
			return;
		}

		try {
			BufferedImage bi = ImageIO.read(Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("Two-Buttons.jpg")));
			Graphics2D g2d = bi.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g2d.setColor(Color.BLACK);
			g2d.setFont(new Font("Impact", Font.BOLD, 25));
			if (g2d.getFontMetrics().getStringBounds(String.join(" ", args), g2d).getWidth() > 270) {
				Profile.drawStringMultiLine(g2d, String.join(" ", args).split(";")[0], 215, 55, 135);
				Profile.drawStringMultiLine(g2d, String.join(" ", args).split(";")[1], 215, 255, 100);
			} else {
				Profile.printCenteredString(String.join(" ", args).split(";")[0], 215, 55, 135, g2d);
				Profile.printCenteredString(String.join(" ", args).split(";")[1], 215, 255, 100, g2d);
			}

			g2d.dispose();

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(bi, "png", baos);

			channel.sendMessage("Aqui está seu meme " + author.getAsMention() + "!").addFile(baos.toByteArray(), "tb.jpg").queue();
		} catch (IOException e) {
			Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
		}
	}

}
