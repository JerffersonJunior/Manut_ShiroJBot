/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2019-2022  Yago Gimenez (KuuHaKu)
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

package com.kuuhaku.model.common.shoukan;

import com.kuuhaku.Constants;
import com.kuuhaku.Main;
import com.kuuhaku.controller.DAO;
import com.kuuhaku.interfaces.shoukan.Drawable;
import com.kuuhaku.model.common.BondedLinkedList;
import com.kuuhaku.model.common.BondedList;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.enums.shoukan.Lock;
import com.kuuhaku.model.enums.shoukan.Side;
import com.kuuhaku.model.persistent.shoukan.Deck;
import com.kuuhaku.model.persistent.shoukan.Evogear;
import com.kuuhaku.model.persistent.shoukan.Senshi;
import com.kuuhaku.model.persistent.user.Account;
import com.kuuhaku.model.records.shoukan.BaseValues;
import com.kuuhaku.model.records.shoukan.Origin;
import com.kuuhaku.model.records.shoukan.Timed;
import com.kuuhaku.util.Graph;
import com.kuuhaku.util.Utils;
import net.dv8tion.jda.api.entities.User;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Hand {
	private final long timestamp = System.currentTimeMillis();

	private final String uid;
	private final Deck userDeck;

	private final Side side;
	private final Origin origin;

	private final List<Drawable<?>> cards = new BondedList<>(d -> d.setHand(this));
	private final LinkedList<Drawable<?>> deck = new BondedLinkedList<>(d -> d.setHand(this));
	private final LinkedList<Drawable<?>> graveyard = new BondedLinkedList<>(Drawable::reset);
	private final List<Drawable<?>> discard = new BondedList<>(d -> d.setAvailable(false));
	private final Set<Timed<Lock>> locks = new HashSet<>();

	private final BaseValues base;

	private String name;

	private int hp = 5000;
	private int regen = 0;
	private int mp = 0;

	private transient Account account;
	private transient String lastMessage;
	private transient boolean forfeit;

	public Hand(String uid, Side side) {
		this.uid = uid;
		this.userDeck = DAO.find(Account.class, uid).getCurrentDeck();
		this.side = side;
		this.origin = userDeck.getOrigins();
		this.base = new BaseValues();

		deck.addAll(
				Stream.of(userDeck.getSenshi(), userDeck.getEvogear(), userDeck.getFields())
						.parallel()
						.flatMap(List::stream)
						.map(d -> d.copy())
						.peek(d -> d.setSolid(true))
						.collect(Utils.toShuffledList(Constants.DEFAULT_RNG))
		);

		manualDraw(5);
	}

	public String getUid() {
		return uid;
	}

	public User getUser() {
		return Main.getApp().getShiro().getUserById(uid);
	}

	public Deck getUserDeck() {
		return userDeck;
	}

	public Side getSide() {
		return side;
	}

	public Origin getOrigin() {
		return origin;
	}

	public List<Drawable<?>> getCards() {
		return cards;
	}

	public int getHandCount() {
		return (int) cards.stream().filter(Drawable::isSolid).count();
	}

	public int getRemainingDraws() {
		return Math.max(0, base.handCapacity() - getHandCount());
	}

	public LinkedList<Drawable<?>> getDeck() {
		return deck;
	}

	public boolean manualDraw(int value) {
		if (deck.isEmpty()) return false;

		if (cards.stream().noneMatch(d -> d instanceof Senshi)) {
			for (int i = 0; i < deck.size() && value > 0; i++) {
				if (deck.get(i) instanceof Senshi) {
					cards.add(deck.remove(i));
					value--;
					break;
				}
			}
		}

		for (int i = 0; i < value; i++) {
			cards.add(deck.removeFirst());
		}

		return true;
	}

	public void draw(int value) {
		for (int i = 0; i < value; i++) {
			cards.add(deck.removeFirst());
		}
	}

	public void drawSenshi(int value) {
		for (int i = 0; i < deck.size() && value > 0; i++) {
			if (deck.get(i) instanceof Senshi) {
				cards.add(deck.remove(i));
				value--;
			}
		}
	}

	public void drawEvogear(int value) {
		for (int i = 0; i < deck.size() && value > 0; i++) {
			if (deck.get(i) instanceof Evogear) {
				cards.add(deck.remove(i));
				value--;
			}
		}
	}

	public LinkedList<Drawable<?>> getGraveyard() {
		return graveyard;
	}

	public List<Drawable<?>> getDiscard() {
		return discard;
	}

	public void flushDiscard() {
		discard.removeIf(d -> !d.isSolid());
		graveyard.addAll(discard);
		discard.clear();
	}

	public Set<Timed<Lock>> getLocks() {
		return locks;
	}

	public int getLockTime(Lock lock) {
		return locks.stream()
				.filter(t -> t.obj().equals(lock))
				.map(Timed::time)
				.mapToInt(AtomicInteger::get)
				.findFirst().orElse(0);
	}

	public BaseValues getBase() {
		return base;
	}

	public String getName() {
		if (name == null) {
			name = Utils.getOr(DAO.find(Account.class, uid).getName(), "???");
		}

		return name;
	}

	public int getHP() {
		return hp;
	}

	public void setHP(int hp) {
		this.hp = Math.max(0, hp);
	}

	public void modHP(int value) {
		this.hp = Math.max(0, this.hp + value);
	}

	public double getHPPrcnt() {
		return hp / (double) base.hp();
	}

	public int getRegen() {
		return regen;
	}

	public void setRegen(int regen) {
		this.regen = regen;
	}

	public double getRegenPrcnt() {
		return regen / (double) base.hp();
	}

	public int getMP() {
		return mp;
	}

	public void setMP(int mp) {
		this.mp = Math.max(0, mp);
	}

	public void modMP(int value) {
		this.mp = Math.max(0, this.mp + value);
	}

	public Account getAccount() {
		if (account == null) {
			account = DAO.find(Account.class, uid);
		}

		return account;
	}

	public String getLastMessage() {
		return lastMessage;
	}

	public void setLastMessage(String lastMessage) {
		this.lastMessage = lastMessage;
	}

	public boolean isForfeit() {
		return forfeit;
	}

	public void setForfeit(boolean forfeit) {
		this.forfeit = forfeit;
	}

	public BufferedImage render(I18N locale) {
		BufferedImage bi = new BufferedImage((225 + 20) * Math.max(5, cards.size()), 450, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = bi.createGraphics();
		g2d.setRenderingHints(Constants.HD_HINTS);
		g2d.setFont(new Font("Arial", Font.BOLD, 90));

		int offset = bi.getWidth() / 2 - ((225 + 20) * cards.size()) / 2;
		for (int i = 0; i < cards.size(); i++) {
			int x = offset + 10 + (225 + 10) * i;

			Drawable<?> d = cards.get(i);
			g2d.drawImage(d.render(locale, userDeck), x, bi.getHeight() - 350, null);
			if (d.isAvailable()) {
				Graph.drawOutlinedString(g2d, String.valueOf(i + 1),
						x + (225 / 2 - g2d.getFontMetrics().stringWidth(String.valueOf(i + 1)) / 2), 90,
						6, Color.BLACK
				);
			}
		}

		g2d.dispose();

		return bi;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Hand hand = (Hand) o;
		return timestamp == hand.timestamp && Objects.equals(uid, hand.uid) && side == hand.side && Objects.equals(origin, hand.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(timestamp, uid, side, origin);
	}
}
