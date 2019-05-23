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

package com.kuuhaku.model;

import com.kuuhaku.controller.Database;
import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

public class Profile {
    public static InputStream makeProfile(Member m, net.dv8tion.jda.core.entities.Member u, Map<String, Tags> tags) throws IOException {
        final BufferedImage avatar = Thumbnails.of(new URL(u.getUser().getAvatarUrl())).size(108, 108).asBufferedImage();
        final BufferedImage card = ImageIO.read(new URL("https://i.imgur.com/VUJDwlq.png"));
        BufferedImage defBg;
        if (m.getBg() == null || m.getBg().isEmpty())
            defBg = Thumbnails.of(new URL("https://i.imgur.com/lsSjE50.jpg")).size(512, 256).asBufferedImage();
        else defBg = Thumbnails.of(new URL(m.getBg())).size(512, 256).asBufferedImage();
        BufferedImage dev;
        BufferedImage partner;
        BufferedImage toxic;
        if (tags.containsKey(u.getUser().getId())) {
            dev = Thumbnails.of(new URL(tags.get(u.getUser().getId()).isStaff() ? "https://i.imgur.com/tIoRtFg.png" : "https://i.imgur.com/j8HUZpp.png")).size(26, 26).asBufferedImage();
            partner = Thumbnails.of(new URL(tags.get(u.getUser().getId()).isPartner() ? "https://i.imgur.com/veKLeGW.png" : "https://i.imgur.com/wE4k5DG.png")).size(26, 26).asBufferedImage();
            toxic = Thumbnails.of(new URL(tags.get(u.getUser().getId()).isToxic() ? "https://i.imgur.com/kJpl4Af.png" : "https://i.imgur.com/lvbHhih.png")).size(26, 26).asBufferedImage();
        } else {
            dev = Thumbnails.of(new URL("https://i.imgur.com/j8HUZpp.png")).size(26, 26).asBufferedImage();
            partner = Thumbnails.of(new URL("https://i.imgur.com/wE4k5DG.png")).size(26, 26).asBufferedImage();
            toxic = Thumbnails.of(new URL("https://i.imgur.com/lvbHhih.png")).size(26, 26).asBufferedImage();
        }
        BufferedImage bi = new BufferedImage(512, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.BLUE);

        g2d.drawImage(defBg, null, 0, 0);
        g2d.fillRect(263, 213, Math.round(m.getXp() * 239 / (int) Math.pow(m.getLevel(), 2)), 5);
        g2d.drawImage(avatar, null, 11, 67);
        g2d.drawImage(card, null, 0, 0);
        g2d.drawImage(dev, null, 160, 42);
        g2d.drawImage(partner, null, 188, 42);
        g2d.drawImage(toxic, null, 216, 42);

        g2d.setColor(Color.YELLOW);
        g2d.setFont(new Font("LilyUPC", Font.PLAIN, 40));
        g2d.drawString("Tags ", 135 - g2d.getFontMetrics().stringWidth("Tags:"), 55);
        g2d.setFont(new Font("LilyUPC", Font.PLAIN, 40));
        g2d.drawString("Level: " + m.getLevel() + " ("+m.getXp() * (int) Math.pow(m.getLevel(), 2)+"%)", 264, 246);
        g2d.drawString("Perfil de " + u.getEffectiveName(), 10, 33);

        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bi, "jpg", baos);
        return new ByteArrayInputStream(baos.toByteArray());
    }
}
