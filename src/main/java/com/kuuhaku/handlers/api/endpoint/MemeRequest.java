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

package com.kuuhaku.handlers.api.endpoint;

import com.kuuhaku.controller.mysql.TokenDAO;
import com.kuuhaku.handlers.api.ImageResponse;
import com.kuuhaku.handlers.api.exception.InternalErrorException;
import com.kuuhaku.handlers.api.exception.NotEnoughArgsException;
import com.kuuhaku.handlers.api.exception.UnauthorizedException;
import com.kuuhaku.model.common.Profile;
import com.kuuhaku.utils.Helper;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Objects;

@RestController
public class MemeRequest {
	@RequestMapping(value = "/meme/twobuttons", method = RequestMethod.POST)
	public @ResponseBody
	ImageResponse getTwoButtonsMeme(@RequestHeader(value = "token") String token,
									@RequestHeader(value = "field-a") String fieldA,
									@RequestHeader(value = "field-b") String fieldB) {
		if (!TokenDAO.validateToken(token)) throw new UnauthorizedException();
		else if (Helper.isEmpty(fieldA, fieldB)) {
			throw new NotEnoughArgsException();
		}

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			BufferedImage bi = ImageIO.read(Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("Two-Buttons.jpg")));
			Graphics2D g2d = bi.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g2d.setColor(Color.BLACK);
			g2d.setFont(new Font("Arial", Font.BOLD, 25));
			if (g2d.getFontMetrics().stringWidth(fieldA + fieldB) > 270) {
				Profile.drawStringMultiLine(g2d, fieldA, 215, 55, 135);
				Profile.drawStringMultiLine(g2d, fieldB, 215, 255, 100);
			} else {
				Profile.printCenteredString(fieldA, 215, 55, 135, g2d);
				Profile.printCenteredString(fieldB, 215, 255, 100, g2d);
			}

			g2d.dispose();

			ImageIO.write(bi, "png", baos);
			return new ImageResponse(200, Base64.getEncoder().encodeToString(baos.toByteArray()));
		} catch (IOException e) {
			Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
			throw new InternalErrorException();
		}
	}

	@RequestMapping(value = "/meme/expandingbrain", method = RequestMethod.POST)
	public @ResponseBody
	ImageResponse getExpandingBrainMeme(@RequestHeader(value = "token") String token,
										@RequestHeader(value = "field-a") String fieldA,
										@RequestHeader(value = "field-b") String fieldB,
										@RequestHeader(value = "field-c") String fieldC,
										@RequestHeader(value = "field-d") String fieldD) {
		if (!TokenDAO.validateToken(token)) throw new UnauthorizedException();
		else if (Helper.isEmpty(fieldA, fieldB, fieldC, fieldD)) {
			throw new NotEnoughArgsException();
		}

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			BufferedImage bi = ImageIO.read(Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("Expanding-Brain.png")));
			Graphics2D g2d = bi.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g2d.setColor(Color.BLACK);
			g2d.setFont(new Font("Arial", Font.BOLD, 40));
			Profile.drawStringMultiLineNO(g2d, fieldA, 390, 20, 40);
			Profile.drawStringMultiLineNO(g2d, fieldB, 390, 20, 340);
			Profile.drawStringMultiLineNO(g2d, fieldC, 390, 20, 650);
			Profile.drawStringMultiLineNO(g2d, fieldD, 390, 20, 930);

			g2d.dispose();

			ImageIO.write(bi, "png", baos);
			return new ImageResponse(200, Base64.getEncoder().encodeToString(baos.toByteArray()));
		} catch (IOException e) {
			Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
			throw new InternalErrorException();
		}
	}

	@RequestMapping(value = "/meme/drake", method = RequestMethod.POST)
	public @ResponseBody
	ImageResponse getDrakeMeme(@RequestHeader(value = "token") String token,
							   @RequestHeader(value = "field-a") String fieldA,
							   @RequestHeader(value = "field-b") String fieldB) {
		if (!TokenDAO.validateToken(token)) throw new UnauthorizedException();
		else if (Helper.isEmpty(fieldA, fieldB)) {
			throw new NotEnoughArgsException();
		}

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			BufferedImage bi = ImageIO.read(Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("drake.jpg")));
			Graphics2D g2d = bi.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g2d.setColor(Color.BLACK);
			g2d.setFont(new Font("Arial", Font.BOLD, 40));
			Profile.drawStringMultiLineNO(g2d, fieldA, 313, 362, 55);
			Profile.drawStringMultiLineNO(g2d, fieldB, 313, 362, 337);

			g2d.dispose();

			ImageIO.write(bi, "png", baos);
			return new ImageResponse(200, Base64.getEncoder().encodeToString(baos.toByteArray()));
		} catch (IOException e) {
			Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
			throw new InternalErrorException();
		}
	}

	@RequestMapping(value = "/meme/sadreality", method = RequestMethod.POST)
	public @ResponseBody
	ImageResponse getSadRealityMeme(@RequestHeader(value = "token") String token,
									@RequestHeader(value = "field-a") String fieldA) {
		if (!TokenDAO.validateToken(token)) throw new UnauthorizedException();
		else if (Helper.isEmpty(fieldA)) {
			throw new NotEnoughArgsException();
		}

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			BufferedImage bi = ImageIO.read(Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("sadbuttrue.png")));
			Graphics2D g2d = bi.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g2d.setColor(Color.BLACK);
			g2d.setFont(new Font("Arial", Font.BOLD, 20));
			if (g2d.getFontMetrics().stringWidth(fieldA) > 270) {
				Profile.drawStringMultiLineNO(g2d, fieldA, 263, 75, 554);
			} else {
				Profile.printCenteredString(fieldA, 270, 63, 554, g2d);
			}

			g2d.dispose();

			ImageIO.write(bi, "png", baos);
			return new ImageResponse(200, Base64.getEncoder().encodeToString(baos.toByteArray()));
		} catch (IOException e) {
			Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
			throw new InternalErrorException();
		}
	}

	@RequestMapping(value = "/meme/stonks", method = RequestMethod.POST)
	public @ResponseBody
	ImageResponse getStonksMeme(@RequestHeader(value = "token") String token,
								@RequestHeader(value = "field-a") String fieldA) {
		if (!TokenDAO.validateToken(token)) throw new UnauthorizedException();
		else if (Helper.isEmpty(fieldA)) {
			throw new NotEnoughArgsException();
		}

		try {
			BufferedImage bi = ImageIO.read(Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("stonks.jpg")));

			ByteArrayOutputStream baos = Helper.renderMeme(fieldA, bi);

			ImageResponse img = new ImageResponse(200, Base64.getEncoder().encodeToString(baos.toByteArray()));
			baos.close();

			return img;
		} catch (IOException e) {
			Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
			throw new InternalErrorException();
		}
	}

	@RequestMapping(value = "/meme/stinks", method = RequestMethod.POST)
	public @ResponseBody
	ImageResponse getStinksMeme(@RequestHeader(value = "token") String token,
								@RequestHeader(value = "field-a") String fieldA) {
		if (!TokenDAO.validateToken(token)) throw new UnauthorizedException();
		else if (Helper.isEmpty(fieldA)) {
			throw new NotEnoughArgsException();
		}

		try {
			BufferedImage bi = ImageIO.read(Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("stinks.jpg")));

			ByteArrayOutputStream baos = Helper.renderMeme(fieldA, bi);

			ImageResponse img = new ImageResponse(200, Base64.getEncoder().encodeToString(baos.toByteArray()));
			baos.close();

			return img;
		} catch (IOException e) {
			Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
			throw new InternalErrorException();
		}
	}

	@RequestMapping(value = "/meme/spiderman", method = RequestMethod.POST)
	public @ResponseBody
	ImageResponse getSpidermanMeme(@RequestHeader(value = "token") String token,
								   @RequestHeader(value = "field-a") String fieldA) {
		if (!TokenDAO.validateToken(token)) throw new UnauthorizedException();
		else if (Helper.isEmpty(fieldA)) {
			throw new NotEnoughArgsException();
		}

		try {
			BufferedImage bi = ImageIO.read(Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("miranha.jpg")));

			ByteArrayOutputStream baos = Helper.renderMeme(fieldA, bi);

			ImageResponse img = new ImageResponse(200, Base64.getEncoder().encodeToString(baos.toByteArray()));
			baos.close();

			return img;
		} catch (IOException e) {
			Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
			throw new InternalErrorException();
		}
	}

	@RequestMapping(value = "/meme/tomcruise", method = RequestMethod.POST)
	public @ResponseBody
	ImageResponse getTomCruiseMeme(@RequestHeader(value = "token") String token,
								   @RequestHeader(value = "field-a") String fieldA) {
		if (!TokenDAO.validateToken(token)) throw new UnauthorizedException();
		else if (Helper.isEmpty(fieldA)) {
			throw new NotEnoughArgsException();
		}

		try {
			BufferedImage bi = ImageIO.read(Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("tomcruise.jpg")));

			ByteArrayOutputStream baos = Helper.renderMeme(fieldA, bi);

			ImageResponse img = new ImageResponse(200, Base64.getEncoder().encodeToString(baos.toByteArray()));
			baos.close();

			return img;
		} catch (IOException e) {
			Helper.logger(this.getClass()).error(e + " | " + e.getStackTrace()[0]);
			throw new InternalErrorException();
		}
	}
}
