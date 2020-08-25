/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2020  Yago Gimenez (KuuHaKu)
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

package com.kuuhaku.handlers.games.tabletop.games.chess;

import com.kuuhaku.handlers.games.tabletop.framework.Board;
import com.kuuhaku.handlers.games.tabletop.framework.Game;
import com.kuuhaku.handlers.games.tabletop.framework.Piece;
import com.kuuhaku.handlers.games.tabletop.framework.Spot;
import com.kuuhaku.handlers.games.tabletop.framework.enums.BoardSize;
import com.kuuhaku.handlers.games.tabletop.games.chess.pieces.*;
import com.kuuhaku.utils.Helper;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class Chess extends Game {
	private final Map<String, List<Piece>> pieces;
	private final TextChannel channel;
	private Message message;
	private final ListenerAdapter listener = new ListenerAdapter() {
		@Override
		public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
			if (canInteract(event)) play(event);
		}
	};

	public Chess(JDA handler, TextChannel channel, int bet, User... players) {
		super(handler, new Board(BoardSize.S_8X8, bet, Arrays.stream(players).map(User::getId).toArray(String[]::new)), channel);
		this.channel = channel;
		this.pieces = Map.of(
				players[0].getId(), List.of(
						new EligibleRook(players[0].getId(), false, "pieces/rook.png"),
						new Knight(players[0].getId(), false, "pieces/knight.png"),
						new Bishop(players[0].getId(), false, "pieces/bishop.png"),
						new Queen(players[0].getId(), false, "pieces/queen.png"),
						new EligibleKing(players[0].getId(), false, "pieces/king.png"),
						new Pawn(players[0].getId(), false, "pieces/pawn.png")
				),
				players[1].getId(), List.of(
						new EligibleRook(players[1].getId(), true, "pieces/rook.png"),
						new Knight(players[1].getId(), true, "pieces/knight.png"),
						new Bishop(players[1].getId(), true, "pieces/bishop.png"),
						new Queen(players[1].getId(), true, "pieces/queen.png"),
						new EligibleKing(players[1].getId(), true, "pieces/king.png"),
						new Pawn(players[1].getId(), true, "pieces/pawn.png")
				)
		);
		getBoard().setMatrix(new Piece[][]{
				new Piece[]{
						pieces.get(players[0].getId()).get(0),
						pieces.get(players[0].getId()).get(1),
						pieces.get(players[0].getId()).get(2),
						pieces.get(players[0].getId()).get(3),
						pieces.get(players[0].getId()).get(4),
						pieces.get(players[0].getId()).get(2),
						pieces.get(players[0].getId()).get(1),
						pieces.get(players[0].getId()).get(0)
				},
				Collections.nCopies(8, pieces.get(players[0].getId()).get(5)).toArray(Piece[]::new),
				new Piece[]{null, null, null, null, null, null, null, null},
				new Piece[]{null, null, null, null, null, null, null, null},
				new Piece[]{null, null, null, null, null, null, null, null},
				new Piece[]{null, null, null, null, null, null, null, null},
				Collections.nCopies(8, pieces.get(players[1].getId()).get(5)).toArray(Piece[]::new),
				new Piece[]{
						pieces.get(players[1].getId()).get(0),
						pieces.get(players[1].getId()).get(1),
						pieces.get(players[1].getId()).get(2),
						pieces.get(players[1].getId()).get(3),
						pieces.get(players[1].getId()).get(4),
						pieces.get(players[1].getId()).get(2),
						pieces.get(players[1].getId()).get(1),
						pieces.get(players[1].getId()).get(0)
				},
		});

		setActions(
				s -> close(),
				s -> getBoard().awardWinner(this, getBoard().getPlayers().getNext().getId())
		);
	}

	@Override
	public void start() {
		resetTimer();
		message = channel.sendMessage(getCurrent().getAsMention() + " você começa!").addFile(Helper.getBytes(getBoard().render()), "board.jpg").complete();
		getHandler().addEventListener(listener);
	}

	@Override
	public boolean canInteract(GuildMessageReceivedEvent evt) {
		Predicate<GuildMessageReceivedEvent> condition = e -> e.getChannel().getId().equals(channel.getId());

		return condition
				.and(e -> e.getAuthor().getId().equals(getCurrent().getId()))
				.and(e -> e.getMessage().getContentRaw().length() == 4)
				.or(e -> e.getMessage().getContentRaw().equalsIgnoreCase("ff"))
				.and(e -> {
					char[] chars = e.getMessage().getContentRaw().toCharArray();
					if (e.getMessage().getContentRaw().equalsIgnoreCase("ff")) return true;
					else return Character.isLetter(chars[0])
							&& Character.isDigit(chars[1])
							&& Character.isLetter(chars[2])
							&& Character.isDigit(chars[3]);
				})
				.test(evt);
	}

	@Override
	public void play(GuildMessageReceivedEvent evt) {
		Message message = evt.getMessage();
		String[] command = {message.getContentRaw().substring(0, 2), message.getContentRaw().substring(2)};

		if (command[0].equalsIgnoreCase("ff")) {
			channel.sendMessage(getCurrent().getAsMention() + " desistiu! (" + getRound() + " turnos)").queue();
			getBoard().awardWinner(this, getBoard().getPlayers().getNext().getId());
			close();
			return;
		}

		try {
			Spot from = Spot.of(command[0]);
			Spot to = Spot.of(command[1]);

			ChessPiece toMove = (ChessPiece) getBoard().getPieceAt(from);
			ChessPiece atSpot = (ChessPiece) getBoard().getPieceAt(to);
			if (toMove == null) {
				channel.sendMessage("❌ | Não há nenhuma peça nessa casa!").queue();
				return;
			} else if (!toMove.getOwnerId().equals(evt.getAuthor().getId())) {
				channel.sendMessage("❌ | Essa peça não é sua!").queue();
				return;
			}

			String winner = null;
			if (toMove.validate(getBoard(), from, to)) {
				if (atSpot instanceof King) {
					winner = getCurrent().getId();
				}

				if (toMove instanceof Pawn) {
					if (toMove.isWhite() && to.getY() == 0) {
						toMove = (ChessPiece) pieces.get(evt.getAuthor().getId()).get(3);
					} else if (to.getY() == 7) {
						toMove = (ChessPiece) pieces.get(evt.getAuthor().getId()).get(3);
					}
				} else if (toMove instanceof EligibleKing) {
					toMove = new King(toMove.getOwnerId(), toMove.isWhite(), toMove.getIconPath());
				} else if (toMove instanceof EligibleRook) {
					toMove = new Rook(toMove.getOwnerId(), toMove.isWhite(), toMove.getIconPath());
				}

				getBoard().setPieceAt(from, null);
				getBoard().setPieceAt(to, toMove);
			} else {
				channel.sendMessage("❌ | Movimento inválido!").queue();
				return;
			}

			int remaining = 0;
			for (Piece[] pieces : getBoard().getMatrix()) {
				for (Piece p : pieces) {
					if (p != null) remaining++;
				}
			}

			if (winner != null) {
				channel.sendMessage(getCurrent().getAsMention() + " venceu! (" + getRound() + " turnos)").addFile(Helper.getBytes(getBoard().render()), "board.jpg").queue();
				getBoard().awardWinner(this, winner);
			} else if (remaining == 2) {
				channel.sendMessage("Temos um empate! (" + getRound() + " turnos)").addFile(Helper.getBytes(getBoard().render()), "board.jpg").queue();
				close();
			} else {
				resetTimer();
				this.message.delete().queue();
				this.message = channel.sendMessage("Turno de " + getCurrent().getAsMention()).addFile(Helper.getBytes(getBoard().render()), "board.jpg").complete();
			}
		} catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
			channel.sendMessage("❌ | Coordenada inválida.").queue();
		}
	}

	@Override
	public void close() {
		super.close();
		getHandler().removeEventListener(listener);
	}
}