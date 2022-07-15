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

package com.kuuhaku.model.persistent.shoukan;

import com.kuuhaku.Constants;
import com.kuuhaku.controller.DAO;
import com.kuuhaku.interfaces.shoukan.Drawable;
import com.kuuhaku.interfaces.shoukan.EffectHolder;
import com.kuuhaku.model.common.shoukan.CardExtra;
import com.kuuhaku.model.common.shoukan.Hand;
import com.kuuhaku.model.enums.Fonts;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.enums.shoukan.Charm;
import com.kuuhaku.model.enums.shoukan.Lock;
import com.kuuhaku.model.enums.shoukan.Race;
import com.kuuhaku.model.enums.shoukan.TargetType;
import com.kuuhaku.model.persistent.converter.JSONArrayConverter;
import com.kuuhaku.model.persistent.shiro.Card;
import com.kuuhaku.model.records.shoukan.EffectParameters;
import com.kuuhaku.model.records.shoukan.Target;
import com.kuuhaku.model.records.shoukan.Targeting;
import com.kuuhaku.util.*;
import com.kuuhaku.util.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.intellij.lang.annotations.Language;

import javax.persistence.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.kuuhaku.model.enums.shoukan.Trigger.ACTIVATE;
import static com.kuuhaku.model.enums.shoukan.Trigger.SPELL_TARGET;

@Entity
@Table(name = "evogear")
public class Evogear extends DAO<Evogear> implements Drawable<Evogear>, EffectHolder {
	@Id
	@Column(name = "card_id", nullable = false)
	private String id;

	@OneToOne(optional = false, orphanRemoval = true)
	@PrimaryKeyJoinColumn(name = "card_id")
	@Fetch(FetchMode.JOIN)
	@MapsId("id")
	private Card card;

	@Column(name = "tier", nullable = false)
	private int tier;

	@Column(name = "spell", nullable = false)
	private boolean spell;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false)
	private TargetType targetType = TargetType.NONE;

	@Convert(converter = JSONArrayConverter.class)
	@Column(name = "charms", nullable = false)
	private JSONArray charms = new JSONArray();

	@Embedded
	private CardAttributes base;

	private transient Senshi equipper = null;
	private transient CardExtra stats = new CardExtra();
	private transient Hand hand = null;
	private transient byte state = 0b10;
	/*
	0x0F
	   └ 1111
	     │││└ solid
	     ││└─ available
	     │└── flipped
	     └─── special summon
	 */

	@Override
	public String getId() {
		return id;
	}

	@Override
	public Card getCard() {
		return card;
	}

	public int getTier() {
		return tier + stats.getTier();
	}

	public boolean isSpell() {
		return spell;
	}

	public TargetType getTargetType() {
		return targetType;
	}

	public JSONArray getCharms() {
		return charms;
	}

	public CardAttributes getBase() {
		return base;
	}

	public Senshi getEquipper() {
		return equipper;
	}

	public void setEquipper(Senshi equipper) {
		this.equipper = equipper;
	}

	public CardExtra getStats() {
		return stats;
	}

	public java.util.List<String> getTags() {
		List<String> out = new ArrayList<>();
		if (hasEffect()) {
			out.add("tag/effect");
		}
		for (Object tag : base.getTags()) {
			out.add("tag/" + tag);
		}

		return out;
	}

	@Override
	public Hand getHand() {
		return hand;
	}

	@Override
	public void setHand(Hand hand) {
		this.hand = hand;
	}

	@Override
	public String getDescription(I18N locale) {
		return Utils.getOr(stats.getDescription(locale), base.getDescription(locale));
	}

	@Override
	public int getMPCost() {
		return (int) Math.max(0, (base.getMana() + stats.getMana()) * getCostMult());
	}

	@Override
	public int getHPCost() {
		return (int) Math.max(0, (base.getBlood() + stats.getBlood()) * getCostMult());
	}

	@Override
	public int getSCCost() {
		return (int) Math.max(0, (base.getSacrifices() + stats.getSacrifices()) * getCostMult());
	}

	@Override
	public int getDmg() {
		int sum = base.getAtk() + stats.getAtk();

		return (int) Math.max(0, sum * getAttrMult());
	}

	@Override
	public int getDef() {
		int sum = base.getDef() + stats.getDef();

		return (int) Math.max(0, sum * getAttrMult());
	}

	@Override
	public int getDodge() {
		int sum = base.getDodge() + stats.getDodge();

		return (int) Math.max(0, sum * getAttrMult());
	}

	@Override
	public int getBlock() {
		int sum = base.getBlock() + stats.getBlock();

		int min = 0;
		if (hand != null && hand.getOrigin().synergy() == Race.CYBORG) {
			min += 2;
		}

		return (int) Math.max(0, (min + sum) * getAttrMult());
	}

	private double getCostMult() {
		double mult = 1;
		if (hand != null && spell && hand.getOrigin().minor() == Race.MYSTICAL) {
			mult *= 0.9 - (hand.getUserDeck().countRace(Race.MYSTICAL) * 0.01);
		}

		return mult;
	}

	private double getAttrMult() {
		double mult = 1;
		if (hand != null && !spell && hand.getOrigin().minor() == Race.MACHINE) {
			mult *= 1.1 + (hand.getUserDeck().countRace(Race.MACHINE) * 0.01);
		}

		return mult * stats.getAttrMult();
	}

	@Override
	public boolean isSolid() {
		return Bit.on(state, 0);
	}

	@Override
	public void setSolid(boolean solid) {
		state = (byte) Bit.set(state, 0, solid);
	}

	@Override
	public boolean isAvailable() {
		return Bit.on(state, 1);
	}

	@Override
	public void setAvailable(boolean available) {
		state = (byte) Bit.set(state, 1, available);
	}

	@Override
	public boolean isFlipped() {
		if (equipper != null) {
			return equipper.isFlipped() || Bit.on(state, 2);
		}

		return Bit.on(state, 2);
	}

	@Override
	public void setFlipped(boolean flipped) {
		state = (byte) Bit.set(state, 2, flipped);
	}

	@Override
	public boolean isSPSummon() {
		return Bit.on(state, 4);
	}

	@Override
	public void setSPSummon(boolean special) {
		state = (byte) Bit.set(state, 4, special);
	}

	public String getEffect() {
		return Utils.getOr(stats.getEffect(), base.getEffect());
	}

	public boolean hasEffect() {
		return !getEffect().isEmpty();
	}

	@Override
	public boolean execute(EffectParameters ep) {
		if (hand.getLockTime(Lock.EFFECT) > 0) return false;

		@Language("Groovy") String effect = Utils.getOr(stats.getEffect(), base.getEffect());
		if (effect.isBlank() || !effect.contains(ep.trigger().name())) return false;

		try {
			Utils.exec(effect, Map.of(
					"ep", ep,
					"self", this,
					"game", hand.getGame(),
					"side", hand.getSide()
			));

			return true;
		} catch (Exception e) {
			Constants.LOGGER.warn("Failed to execute " + card.getName() + " effect", e);
			return false;
		}
	}

	public EffectParameters toParameters(Targeting tgt) {
		return switch (targetType) {
			case NONE -> new EffectParameters(ACTIVATE);
			case ALLY -> new EffectParameters(ACTIVATE, asSource(ACTIVATE),
					new Target(tgt.ally(), SPELL_TARGET)
			);
			case ENEMY -> new EffectParameters(ACTIVATE, asSource(ACTIVATE),
					new Target(tgt.enemy(), SPELL_TARGET)
			);
			case BOTH -> new EffectParameters(ACTIVATE, asSource(ACTIVATE),
					new Target(tgt.ally(), SPELL_TARGET),
					new Target(tgt.enemy(), SPELL_TARGET)
			);
		};
	}

	@Override
	public void reset() {
		stats = new CardExtra();

		byte base = 0b10;
		base = (byte) Bit.set(base, 0, isSolid());
		base = (byte) Bit.set(base, 3, isSPSummon());

		state = base;
	}

	@Override
	public BufferedImage render(I18N locale, Deck deck) {
		if (isFlipped()) return deck.getFrame().getBack(deck);

		String desc = getDescription(locale);

		BufferedImage img = card.drawCardNoBorder(deck.isUsingChrome());
		BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = out.createGraphics();
		g2d.setRenderingHints(Constants.HD_HINTS);

		g2d.setClip(deck.getFrame().getBoundary());
		g2d.drawImage(img, 0, 0, null);
		g2d.setClip(null);

		g2d.drawImage(deck.getFrame().getFront(!desc.isEmpty()), 0, 0, null);
		g2d.drawImage(IO.getResourceAsImage("shoukan/icons/tier_" + getTier() + ".png"), 190, 12, null);

		g2d.setFont(new Font("Arial", Font.BOLD, 18));
		g2d.setColor(deck.getFrame().getPrimaryColor());
		String name = StringUtils.abbreviate(card.getName(), MAX_NAME_LENGTH);
		Graph.drawOutlinedString(g2d, name, 12, 30, 2, deck.getFrame().getBackgroundColor());

		if (!getCharms().isEmpty()) {
			List<BufferedImage> icons = charms.stream()
					.map(String::valueOf)
					.map(Charm::valueOf)
					.map(Charm::getIcon)
					.filter(Objects::nonNull)
					.limit(2)
					.toList();

			if (!icons.isEmpty()) {
				int y = desc.isBlank() ? 238 : 184;
				Graph.applyTransformed(g2d, 200 - 64, y, g -> {
					if (icons.size() == 1) {
						g.drawImage(icons.get(0), 0, 0, null);
					} else {
						BufferedImage mask = IO.getResourceAsImage("shoukan/charm/mask.png");
						assert mask != null;

						for (int i = 0; i < icons.size(); i++) {
							BufferedImage icon = icons.get(i);
							Graph.applyMask(icon, mask, i, true);
							g.drawImage(icon, 0, 0, null);
						}
						g.drawImage(IO.getResourceAsImage("shoukan/charm/div.png"), 0, 0, null);
					}
				});
			}
		}

		if (!desc.isEmpty()) {
			g2d.setColor(deck.getFrame().getSecondaryColor());
			g2d.setFont(Fonts.HAMMERSMITH_ONE.deriveFont(Font.PLAIN, 12));
			g2d.drawString(getTags().stream()
							.limit(4)
							.map(s -> getString(locale, s))
							.map(String::toUpperCase).toList().toString()
					, 7, 275
			);

			g2d.setFont(Fonts.HAMMERSMITH_ONE.deriveFont(Font.PLAIN, 11));
			Graph.drawMultilineString(g2d, desc,
					7, 287, 211, 3,
					parseValues(g2d, deck, this), highlightValues(g2d)
			);
		}

		drawCosts(g2d);
		drawAttributes(g2d, !desc.isEmpty());

		if (!isAvailable()) {
			RescaleOp op = new RescaleOp(0.5f, 0, null);
			op.filter(out, out);
		}

		g2d.dispose();

		return out;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Evogear evogear = (Evogear) o;
		return Objects.equals(id, evogear.id)
				&& Objects.equals(card, evogear.card)
				&& Objects.equals(equipper, evogear.equipper);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, card);
	}

	@Override
	public Evogear clone() throws CloneNotSupportedException {
		return (Evogear) super.clone();
	}

	@Override
	public String toString() {
		return card.getName();
	}

	public static Evogear getRandom() {
		String id = DAO.queryNative(String.class, "SELECT card_id FROM evogear ORDER BY RANDOM()");
		if (id == null) return null;

		return DAO.find(Evogear.class, id);
	}

	public static Evogear getRandom(String... filters) {
		XStringBuilder query = new XStringBuilder("SELECT card_id FROM evogear");
		for (String f : filters) {
			query.appendNewLine(f);
		}
		query.appendNewLine("ORDER BY RANDOM()");

		String id = DAO.queryNative(String.class, query.toString());
		if (id == null) return null;

		return DAO.find(Evogear.class, id);
	}
}
