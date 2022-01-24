/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2019-2021  Yago Gimenez (KuuHaKu)
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

package com.kuuhaku.handlers.games.tabletop.framework;

import com.kuuhaku.controller.postgresql.AccountDAO;
import com.kuuhaku.handlers.games.tabletop.framework.enums.BoardSize;
import com.kuuhaku.handlers.games.tabletop.framework.enums.Neighbor;
import com.kuuhaku.model.persistent.Account;
import com.kuuhaku.utils.InfiniteList;
import org.apache.commons.lang3.ArrayUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Board {
	private final BoardSize size;
	private final InfiniteList<Player> players;
	private final Piece[][] matrix;
	private final BufferedImage bi;
	private boolean awarded = false;

	public Board(BoardSize size, long bet, String... players) {
		this.size = size;
		this.players = Arrays.stream(players).map(s -> new Player(s, bet)).collect(Collectors.toCollection(InfiniteList::new));
		this.matrix = new Piece[size.getHeight()][size.getWidth()];

		if (size.getWidth() + size.getHeight() != 0) {
			this.bi = new BufferedImage(64 * size.getWidth(), 64 * size.getHeight(), BufferedImage.TYPE_INT_RGB);
		} else {
			this.bi = null;
		}

		Collections.reverse(this.players);
	}

	public BoardSize getSize() {
		return size;
	}

	public Piece[][] getMatrix() {
		return matrix;
	}

	public void setMatrix(Piece[][] matrix) {
		for (int y = 0; y < this.matrix.length; y++) {
			System.arraycopy(matrix[y], 0, this.matrix[y], 0, this.matrix.length);
		}
	}

	public Piece getPieceAt(Spot s) {
		Piece p = matrix[s.y()][s.x()];
		return p instanceof Decoy ? null : p;
	}

	public Piece getPieceOrDecoyAt(Spot s) {
		return matrix[s.y()][s.x()];
	}

	public void setPieceAt(Spot s, Piece p) {
		matrix[s.y()][s.x()] = p;
	}

	public Piece[] getLine(Spot from, Neighbor vector, boolean inclusive) {
		List<Piece> pieces = new ArrayList<>();

		int[] currentCoords = inclusive ? from.getCoords() : new int[]{from.x() + vector.getX(), from.y() + vector.getY()};
		while (true) {
			try {
				pieces.add(matrix[currentCoords[1]][currentCoords[0]]);

				currentCoords[0] += vector.getX();
				currentCoords[1] += vector.getY();
			} catch (ArrayIndexOutOfBoundsException e) {
				break;
			}
		}

		return pieces.toArray(Piece[]::new);
	}

	public void fillLine(Spot from, int distance, Piece with, Neighbor vector, boolean inclusive) {
		int[] currentCoords = inclusive ? from.getCoords() : new int[]{from.x() + vector.getX(), from.y() + vector.getY()};
		for (int i = 0; i <= distance; i++) {
			try {
				matrix[currentCoords[1]][currentCoords[0]] = with;

				currentCoords[0] += vector.getX();
				currentCoords[1] += vector.getY();
			} catch (ArrayIndexOutOfBoundsException e) {
				break;
			}
		}
	}

	public Piece[] getColumn(int index) {
		Piece[] column = new Piece[matrix.length];
		for (int i = 0; i < matrix.length; i++) {
			column[i] = matrix[i][index];
		}
		return column;
	}

	public Piece[] getRow(int row) {
		return matrix[row];
	}

	public Piece[] filter(Predicate<Piece> condition) {
		List<Piece> pieces = new ArrayList<>();

		for (Piece[] row : matrix) {
			for (Piece p : row) {
				if (condition.test(p)) pieces.add(p);
			}
		}

		return pieces.toArray(Piece[]::new);
	}

	public InfiniteList<Player> getPlayers() {
		return players;
	}

	public InfiniteList<Player> getInGamePlayers() {
		return players.stream().filter(Player::isInGame).collect(Collectors.toCollection(InfiniteList::new));
	}

	public void leaveGame() {
		Objects.requireNonNull(players.pollFirst()).setInGame(false);
	}

	public void awardWinner(Game game, String id) {
		List<Player> losers = players.stream().filter(p -> !p.getId().equals(id)).toList();

		Account wacc = AccountDAO.getAccount(id);
		wacc.addCredit(
				losers.stream()
						.mapToLong(Player::getBet)
						.sum(), game.getClass()
		);
		AccountDAO.saveAccount(wacc);

		for (Player l : losers) {
			Account lacc = AccountDAO.getAccount(l.getId());
			lacc.removeCredit(l.getBet(), game.getClass());
			AccountDAO.saveAccount(lacc);
		}

		awarded = true;
	}

	public void awardWinners(Game game, String... ids) {
		List<Player> losers = players.stream().filter(p -> ArrayUtils.contains(ids, p.getId())).toList();

		for (String id : ids) {
			Account wacc = AccountDAO.getAccount(id);
			wacc.addCredit(
					losers.stream()
							.mapToLong(Player::getBet)
							.sum() / ids.length, game.getClass()
			);
			AccountDAO.saveAccount(wacc);
		}

		for (Player l : losers) {
			Account lacc = AccountDAO.getAccount(l.getId());
			lacc.removeCredit(l.getBet(), game.getClass());
			AccountDAO.saveAccount(lacc);
		}

		awarded = true;
	}

	public void awardWinner(GlobalGame game, String id) {
		List<Player> losers = players.stream().filter(p -> !p.getId().equals(id)).toList();

		Account wacc = AccountDAO.getAccount(id);
		wacc.addCredit(
				losers.stream()
						.mapToLong(Player::getBet)
						.sum(), game.getClass()
		);
		AccountDAO.saveAccount(wacc);

		for (Player l : losers) {
			Account lacc = AccountDAO.getAccount(l.getId());
			lacc.removeCredit(l.getBet(), game.getClass());
			AccountDAO.saveAccount(lacc);
		}

		awarded = true;
	}

	public void awardWinners(GlobalGame game, String... ids) {
		List<Player> losers = players.stream().filter(p -> ArrayUtils.contains(ids, p.getId())).toList();

		for (String id : ids) {
			Account wacc = AccountDAO.getAccount(id);
			wacc.addCredit(
					losers.stream()
							.mapToLong(Player::getBet)
							.sum() / ids.length, game.getClass()
			);
			AccountDAO.saveAccount(wacc);
		}

		for (Player l : losers) {
			Account lacc = AccountDAO.getAccount(l.getId());
			lacc.removeCredit(l.getBet(), game.getClass());
			AccountDAO.saveAccount(lacc);
		}

		awarded = true;
	}

	public BufferedImage render() {
		Graphics2D g2d = bi.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 32));

		for (int y = 0; y < size.getHeight(); y++) {
			for (int x = 0; x < size.getWidth(); x++) {
				g2d.setColor((y + x) % 2 == 0 ? Color.decode("#dcc0b4") : Color.decode("#472d22"));
				g2d.fillRect(64 * x, 64 * y, 64, 64);
				g2d.setColor((y + x) % 2 == 0 ? Color.decode("#472d22") : Color.decode("#dcc0b4"));

				Piece p = matrix[y][x];
				if (p != null && !(p instanceof Decoy)) {
					g2d.drawImage(p.getIcon(), 64 * x, 64 * y, null);
				} else {
					g2d.drawString(String.valueOf(Spot.getAlphabet().toUpperCase(Locale.ROOT).charAt(x)) + (y + 1), 64 * x + 13, 64 * y + 44);
				}
			}
		}

		g2d.dispose();

		return bi;
	}

	public boolean isAwarded() {
		return awarded;
	}
}
