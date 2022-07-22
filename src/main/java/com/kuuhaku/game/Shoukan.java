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

package com.kuuhaku.game;

import com.github.ygimenez.method.Pages;
import com.github.ygimenez.model.ButtonWrapper;
import com.github.ygimenez.model.ThrowingConsumer;
import com.kuuhaku.Constants;
import com.kuuhaku.Main;
import com.kuuhaku.command.misc.SynthesizeCommand;
import com.kuuhaku.controller.DAO;
import com.kuuhaku.game.engine.GameInstance;
import com.kuuhaku.game.engine.GameReport;
import com.kuuhaku.game.engine.PhaseConstraint;
import com.kuuhaku.game.engine.PlayerAction;
import com.kuuhaku.interfaces.shoukan.Drawable;
import com.kuuhaku.model.common.shoukan.*;
import com.kuuhaku.model.enums.CardType;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.enums.shoukan.*;
import com.kuuhaku.model.persistent.id.LocalizedDescId;
import com.kuuhaku.model.persistent.shoukan.Evogear;
import com.kuuhaku.model.persistent.shoukan.Field;
import com.kuuhaku.model.persistent.shoukan.LocalizedString;
import com.kuuhaku.model.persistent.shoukan.Senshi;
import com.kuuhaku.model.persistent.user.StashedCard;
import com.kuuhaku.model.records.shoukan.*;
import com.kuuhaku.model.records.shoukan.snapshot.Player;
import com.kuuhaku.model.records.shoukan.snapshot.Slot;
import com.kuuhaku.model.records.shoukan.snapshot.StateSnap;
import com.kuuhaku.util.Calc;
import com.kuuhaku.util.IO;
import com.kuuhaku.util.Utils;
import com.kuuhaku.util.json.JSONArray;
import com.kuuhaku.util.json.JSONObject;
import com.kuuhaku.util.json.JSONUtils;
import kotlin.Pair;
import net.dv8tion.jda.api.entities.*;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static com.kuuhaku.model.enums.shoukan.Trigger.*;

public class Shoukan extends GameInstance<Phase> {
	private final long seed = Constants.DEFAULT_RNG.nextLong();

	private final I18N locale;
	private final String[] players;
	private final Map<Side, Hand> hands;
	private final Arena arena;
	private final Map<String, Pair<String, String>> messages = new HashMap<>();
	private final Set<EffectOverTime> eots = new TreeSet<>();

	private final boolean singleplayer;
	private StateSnap snapshot = null;
	private boolean restoring = false;

	public Shoukan(I18N locale, User p1, User p2) {
		this(locale, p1.getId(), p2.getId());
	}

	public Shoukan(I18N locale, String p1, String p2) {
		this.locale = locale;
		this.players = new String[]{p1, p2};
		this.hands = Map.of(
				Side.TOP, new Hand(p1, this, Side.TOP),
				Side.BOTTOM, new Hand(p2, this, Side.BOTTOM)
		);
		this.arena = new Arena(this);
		this.singleplayer = p1.equals(p2);

		setTimeout(turn -> {
			reportResult("str/game_wo", "<@" + getCurrent().getUid() + ">");
			close(GameReport.GAME_TIMEOUT);
		}, 5, TimeUnit.MINUTES);
	}

	@Override
	protected boolean validate(Message message) {
		return ((Predicate<Message>) m -> Utils.equalsAny(m.getAuthor().getId(), players))
				.and(m -> singleplayer || getTurn() % 2 == ArrayUtils.indexOf(players, m.getAuthor().getId()))
				.test(message);
	}

	@Override
	protected void begin() {
		for (Hand h : hands.values()) {
			h.manualDraw(5);
		}

		setPhase(Phase.PLAN);

		Hand curr = getCurrent();
		curr.modMP(curr.getBase().mpGain().apply(getTurn() - (curr.getSide() == Side.TOP ? 1 : 0)));

		reportEvent("str/game_start", "<@" + curr.getUid() + ">");
		sendPlayerHand(curr);

		takeSnapshot();
	}

	@Override
	protected void runtime(String value) throws InvocationTargetException, IllegalAccessException {
		Pair<Method, JSONObject> action = toAction(value.toLowerCase(Locale.ROOT).replace(" ", ""));
		if (action != null) {
			if ((boolean) action.getFirst().invoke(this, getCurrentSide(), action.getSecond())) {
				sendPlayerHand(getCurrent());
			}
		}
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\d+),(?<mode>[adb]),(?<inField>[1-5])(?<notCombat>,nc)?")
	private boolean placeCard(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (!Utils.between(args.getInt("inHand"), 1, curr.getCards().size() + 1)) {
			getChannel().sendMessage(locale.get("error/invalid_hand_index")).queue();
			return false;
		}

		if (curr.getCards().get(args.getInt("inHand") - 1) instanceof Senshi chosen) {
			if (!chosen.isAvailable()) {
				getChannel().sendMessage(locale.get("error/card_unavailable")).queue();
				return false;
			} else if (chosen.getHPCost() >= curr.getHP()) {
				getChannel().sendMessage(locale.get("error/not_enough_hp")).queue();
				return false;
			} else if (chosen.getMPCost() > curr.getMP()) {
				getChannel().sendMessage(locale.get("error/not_enough_mp")).queue();
				return false;
			} else if (chosen.getSCCost() > curr.getDiscard().size()) {
				getChannel().sendMessage(locale.get("error/not_enough_sc")).queue();
				return false;
			}
		} else {
			getChannel().sendMessage(locale.get("error/wrong_card_type")).queue();
			return false;
		}

		Senshi copy;
		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);
		if (slot.isLocked()) {
			int time = slot.getLock();

			if (time == -1) {
				getChannel().sendMessage(locale.get("error/slot_locked_perma")).queue();
			} else {
				getChannel().sendMessage(locale.get("error/slot_locked", time)).queue();
			}
			return false;
		}

		if (args.getBoolean("notCombat")) {
			if (slot.hasBottom()) {
				getChannel().sendMessage(locale.get("error/slot_occupied")).queue();
				return false;
			}

			curr.consumeHP(chosen.getHPCost());
			curr.consumeMP(chosen.getMPCost());
			curr.consumeSC(chosen.getSCCost());
			chosen.setAvailable(curr.getOrigin().synergy() == Race.HERALD && Calc.chance(2));
			slot.setBottom(copy = chosen.withCopy(s -> {
				switch (args.getString("mode")) {
					case "d" -> s.setDefending(true);
					case "b" -> s.setFlipped(true);
				}
			}));
		} else {
			if (slot.hasTop()) {
				getChannel().sendMessage(locale.get("error/slot_occupied")).queue();
				return false;
			}

			curr.consumeHP(chosen.getHPCost());
			curr.consumeMP(chosen.getMPCost());
			curr.consumeSC(chosen.getSCCost());
			chosen.setAvailable(curr.getOrigin().synergy() == Race.HERALD && Calc.chance(2));
			slot.setTop(copy = chosen.withCopy(s -> {
				switch (args.getString("mode")) {
					case "d" -> s.setDefending(true);
					case "b" -> s.setFlipped(true);
				}
			}));
		}

		reportEvent("str/place_card",
				curr.getName(),
				copy.isFlipped() ? locale.get("str/a_card") : copy,
				copy.getState().toString(locale)
		);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\d+),(?<inField>[1-5])")
	private boolean equipCard(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (!Utils.between(args.getInt("inHand"), 1, curr.getCards().size() + 1)) {
			getChannel().sendMessage(locale.get("error/invalid_hand_index")).queue();
			return false;
		}

		if (curr.getCards().get(args.getInt("inHand") - 1) instanceof Evogear chosen && !chosen.isSpell()) {
			if (!chosen.isAvailable()) {
				getChannel().sendMessage(locale.get("error/card_unavailable")).queue();
				return false;
			} else if (chosen.getHPCost() >= curr.getHP()) {
				getChannel().sendMessage(locale.get("error/not_enough_hp")).queue();
				return false;
			} else if (chosen.getMPCost() > curr.getMP()) {
				getChannel().sendMessage(locale.get("error/not_enough_mp")).queue();
				return false;
			} else if (chosen.getSCCost() > curr.getDiscard().size()) {
				getChannel().sendMessage(locale.get("error/not_enough_sc")).queue();
				return false;
			}
		} else {
			getChannel().sendMessage(locale.get("error/wrong_card_type")).queue();
			return false;
		}

		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);
		if (!slot.hasTop()) {
			getChannel().sendMessage(locale.get("error/missing_card", slot.getIndex() + 1)).queue();
			return false;
		}

		Senshi target = slot.getTop();
		curr.consumeHP(chosen.getHPCost());
		curr.consumeMP(chosen.getMPCost());
		curr.consumeSC(chosen.getSCCost());
		chosen.setAvailable(false);

		Evogear copy = chosen.copy();
		target.getEquipments().add(copy);
		reportEvent("str/equip_card",
				curr.getName(),
				copy.isFlipped() ? locale.get("str/an_equipment") : copy,
				target.isFlipped() ? locale.get("str/a_card") : target
		);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\d+)f")
	private boolean placeField(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (!Utils.between(args.getInt("inHand"), 1, curr.getCards().size() + 1)) {
			getChannel().sendMessage(locale.get("error/invalid_hand_index")).queue();
			return false;
		}

		if (curr.getCards().get(args.getInt("inHand") - 1) instanceof Field chosen) {
			if (!chosen.isAvailable()) {
				getChannel().sendMessage(locale.get("error/card_unavailable")).queue();
				return false;
			}
		} else {
			getChannel().sendMessage(locale.get("error/wrong_card_type")).queue();
			return false;
		}

		chosen.setAvailable(false);
		arena.setField(chosen.copy());
		reportEvent("str/place_field", curr.getName(), chosen);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inField>[1-5]),f(?<notCombat>,nc)?")
	private boolean flipCard(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);

		boolean nc = args.getBoolean("notCombat");
		if ((nc && !slot.hasBottom()) || (!nc && !slot.hasTop())) {
			getChannel().sendMessage(locale.get("error/missing_card", slot.getIndex() + 1)).queue();
			return false;
		}

		Senshi chosen = nc ? slot.getBottom() : slot.getTop();
		if (!chosen.isAvailable()) {
			getChannel().sendMessage(locale.get("error/card_unavailable")).queue();
			return false;
		} else if (chosen.isFlipped()) {
			chosen.setFlipped(false);
		} else {
			chosen.setDefending(!chosen.isDefending());
		}

		reportEvent("str/flip_card", curr.getName(), chosen, chosen.getState().toString(locale));
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inField>[1-5]),p")
	private boolean promoteCard(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);

		if (!slot.hasBottom()) {
			getChannel().sendMessage(locale.get("error/missing_card", slot.getIndex() + 1)).queue();
			return false;
		} else if (slot.hasTop()) {
			getChannel().sendMessage(locale.get("error/promote_blocked")).queue();
			return false;
		}

		Senshi chosen = slot.getBottom();
		slot.setBottom(null);
		slot.setTop(chosen);

		reportEvent("str/promote_card", curr.getName(), chosen);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inField>[1-5]),s(?<notCombat>,nc)?")
	private boolean sacrificeCard(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);

		boolean nc = args.getBoolean("notCombat");
		if ((nc && !slot.hasBottom()) || (!nc && !slot.hasTop())) {
			getChannel().sendMessage(locale.get("error/missing_card", slot.getIndex() + 1)).queue();
			return false;
		}

		Senshi chosen = nc ? slot.getBottom() : slot.getTop();
		if (chosen.getTags().contains("FIXED")) {
			getChannel().sendMessage(locale.get("error/card_fixed")).queue();
			return false;
		} else if (chosen.getHPCost() / 2 >= curr.getHP()) {
			getChannel().sendMessage(locale.get("error/not_enough_hp_sacrifice")).queue();
			return false;
		} else if (chosen.getMPCost() / 2 > curr.getMP()) {
			getChannel().sendMessage(locale.get("error/not_enough_mp_sacrifice")).queue();
			return false;
		}

		curr.consumeHP(chosen.getHPCost() / 2);
		curr.consumeMP(chosen.getMPCost() / 2);
		curr.getGraveyard().add(chosen);
		trigger(ON_SACRIFICE, side);

		reportEvent("str/sacrifice_card", curr.getName(), chosen);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inField>\\[[1-5](,[1-5])*]),s(?<notCombat>,nc)?")
	private boolean sacrificeBatch(Side side, JSONObject args) {
		Hand curr = hands.get(side);

		int hp = 0;
		int mp = 0;

		List<Drawable<?>> cards = new ArrayList<>();
		JSONArray batch = args.getJSONArray("inField");
		for (Object o : batch) {
			int idx = ((Number) o).intValue();
			SlotColumn slot = arena.getSlots(curr.getSide()).get(idx - 1);

			boolean nc = args.getBoolean("notCombat");
			if ((nc && !slot.hasBottom()) || (!nc && !slot.hasTop())) {
				getChannel().sendMessage(locale.get("error/missing_card", slot.getIndex() + 1)).queue();
				return false;
			}

			Senshi chosen = nc ? slot.getBottom() : slot.getTop();
			if (chosen.getHPCost() / 2 >= curr.getHP()) {
				getChannel().sendMessage(locale.get("error/not_enough_hp_sacrifice")).queue();
				return false;
			} else if (chosen.getMPCost() / 2 > curr.getMP()) {
				getChannel().sendMessage(locale.get("error/not_enough_mp_sacrifice")).queue();
				return false;
			}

			hp += chosen.getHPCost() / 2;
			mp += chosen.getMPCost() / 2;
			cards.add(chosen);
		}

		curr.consumeHP(hp);
		curr.consumeMP(mp);
		curr.getGraveyard().addAll(cards);
		for (Drawable<?> ignored : cards) {
			trigger(ON_SACRIFICE, side);
		}

		reportEvent("str/sacrifice_card", curr.getName(),
				Utils.properlyJoin(locale.get("str/and")).apply(cards.stream().map(Drawable::toString).toList())
		);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\d+),d")
	private boolean discardCard(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (!Utils.between(args.getInt("inHand"), 1, curr.getCards().size() + 1)) {
			getChannel().sendMessage(locale.get("error/invalid_hand_index")).queue();
			return false;
		}

		Drawable<?> chosen = curr.getCards().get(args.getInt("inHand") - 1);
		if (!chosen.isAvailable()) {
			getChannel().sendMessage(locale.get("error/card_unavailable")).queue();
			return false;
		}

		curr.getDiscard().add(chosen);
		trigger(ON_DISCARD, side);

		if (curr.getOrigin().synergy() == Race.FAMILIAR) {
			for (Drawable<?> d : curr.getCards()) {
				if (d.isAvailable() && Calc.chance(25)) {
					if (d instanceof Senshi s) {
						s.getStats().setMana(-1);
					} else if (d instanceof Evogear e) {
						e.getStats().setMana(-1);
					}
				}
			}
		}

		reportEvent("str/discard_card", curr.getName(), chosen);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\[\\d+(,\\d+)*]),d")
	private boolean discardBatch(Side side, JSONObject args) {
		Hand curr = hands.get(side);

		List<Drawable<?>> cards = new ArrayList<>();
		JSONArray batch = args.getJSONArray("inHand");
		for (Object o : batch) {
			int idx = ((Number) o).intValue();
			if (!Utils.between(idx, 1, curr.getCards().size() + 1)) {
				getChannel().sendMessage(locale.get("error/invalid_hand_index")).queue();
				return false;
			}

			Drawable<?> chosen = curr.getCards().get(idx - 1);
			if (!chosen.isAvailable()) {
				getChannel().sendMessage(locale.get("error/card_unavailable")).queue();
				return false;
			}

			cards.add(chosen);
		}

		curr.getDiscard().addAll(cards);
		for (Drawable<?> ignored : cards) {
			trigger(ON_DISCARD, side);
		}

		if (curr.getOrigin().synergy() == Race.FAMILIAR) {
			for (Drawable<?> d : curr.getCards()) {
				if (d.isAvailable() && Calc.chance(25)) {
					if (d instanceof Senshi s) {
						s.getStats().setMana(s, -cards.size());
					} else if (d instanceof Evogear e) {
						e.getStats().setMana(e, -cards.size());
					}
				}
			}
		}

		reportEvent("str/discard_card", curr.getName(),
				Utils.properlyJoin(locale.get("str/and")).apply(cards.stream().map(Drawable::toString).toList())
		);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\d+)(?:,(?<target1>[1-5]))?(?:,(?<target2>[1-5]))?")
	private boolean activate(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (!Utils.between(args.getInt("inHand"), 1, curr.getCards().size() + 1)) {
			getChannel().sendMessage(locale.get("error/invalid_hand_index")).queue();
			return false;
		}

		if (curr.getCards().get(args.getInt("inHand") - 1) instanceof Evogear chosen && chosen.isSpell()) {
			if (!chosen.isAvailable()) {
				getChannel().sendMessage(locale.get("error/card_unavailable")).queue();
				return false;
			} else if (chosen.getHPCost() >= curr.getHP()) {
				getChannel().sendMessage(locale.get("error/not_enough_hp")).queue();
				return false;
			} else if (chosen.getMPCost() > curr.getMP()) {
				getChannel().sendMessage(locale.get("error/not_enough_mp")).queue();
				return false;
			} else if (chosen.getSCCost() > curr.getDiscard().size()) {
				getChannel().sendMessage(locale.get("error/not_enough_sc")).queue();
				return false;
			}

			int locktime = curr.getLockTime(Lock.SPELL);
			if (locktime > 0) {
				getChannel().sendMessage(locale.get("error/spell_locked", locktime)).queue();
				return false;
			}
		} else {
			getChannel().sendMessage(locale.get("error/wrong_card_type")).queue();
			return false;
		}

		Targeting tgt = switch (chosen.getTargetType()) {
			case NONE -> new Targeting(null, null);
			case ALLY -> new Targeting(curr, args.getInt("target1"), -1);
			case ENEMY -> new Targeting(curr, -1, args.getInt("target1"));
			case BOTH -> new Targeting(curr, args.getInt("target1"), args.getInt("target2"));
		};

		if (!tgt.validate(chosen.getTargetType())) {
			getChannel().sendMessage(locale.get("error/missing_target")).queue();
			return false;
		}

		curr.consumeHP(chosen.getHPCost());
		curr.consumeMP(chosen.getMPCost());
		curr.consumeSC(chosen.getSCCost());
		chosen.setAvailable(false);
		chosen.withCopy(e -> {
			curr.getGraveyard().add(e);
			e.execute(e.toParameters(tgt));
		});

		reportEvent("str/activate_card", curr.getName(), chosen);
		return true;
	}

	@PhaseConstraint({"PLAN", "COMBAT"})
	@PlayerAction("(?<inField>[1-5]),a")
	private boolean special(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);

		if (!slot.hasTop()) {
			getChannel().sendMessage(locale.get("error/missing_card", slot.getIndex() + 1)).queue();
			return false;
		}

		int locktime = curr.getLockTime(Lock.EFFECT);
		if (locktime > 0) {
			getChannel().sendMessage(locale.get("error/effect_locked", locktime)).queue();
			return false;
		}

		Senshi chosen = slot.getTop();
		if (!chosen.isAvailable()) {
			getChannel().sendMessage(locale.get("error/card_unavailable")).queue();
			return false;
		} else if (chosen.isFlipped()) {
			getChannel().sendMessage(locale.get("error/card_flipped")).queue();
			return false;
		} else if (chosen.getCooldown() > 0) {
			getChannel().sendMessage(locale.get("error/card_cooldown", chosen.getCooldown())).queue();
			return false;
		} else if (curr.getMP() < 1) {
			getChannel().sendMessage(locale.get("error/not_enough_mp")).queue();
			return false;
		} else if (!chosen.getEffect().contains(ACTIVATE.name())) {
			getChannel().sendMessage(locale.get("error/card_no_special")).queue();
			return false;
		}

		curr.consumeMP(1);
		trigger(ACTIVATE, chosen.asSource(ACTIVATE));
		if (getPhase() != Phase.PLAN) {
			chosen.setAvailable(false);
		}

		reportEvent("str/card_special", curr.getName(), chosen);
		return true;
	}

	@PhaseConstraint("COMBAT")
	@PlayerAction("(?<inField>[1-5])(?:,(?<target>[1-5]))?")
	private boolean attack(Side side, JSONObject args) {
		Hand you = hands.get(side);
		SlotColumn yourSlot = arena.getSlots(you.getSide()).get(args.getInt("inField") - 1);

		if (!yourSlot.hasTop()) {
			getChannel().sendMessage(locale.get("error/missing_card", yourSlot.getIndex() + 1)).queue();
			return false;
		}

		Senshi ally = yourSlot.getTop();
		if (!ally.canAttack()) {
			getChannel().sendMessage(locale.get("error/card_cannot_attack")).queue();
			return false;
		}

		Hand op = hands.get(side.getOther());
		Senshi enemy = null;
		if (args.getBoolean("target")) {
			SlotColumn opSlot = arena.getSlots(op.getSide()).get(args.getInt("target") - 1);

			if (!opSlot.hasTop()) {
				if (!opSlot.hasBottom()) {
					getChannel().sendMessage(locale.get("error/missing_card", opSlot.getIndex() + 1)).queue();
					return false;
				}

				enemy = opSlot.getBottom();
			} else {
				enemy = opSlot.getTop();
			}

			enemy.setFlipped(false);
		}

		if (enemy == null && !arena.isFieldEmpty(op.getSide()) && !ally.getStats().popFlag(Flag.DIRECT)) {
			getChannel().sendMessage(locale.get("error/field_not_empty")).queue();
			return false;
		} else if (enemy != null && enemy.isStasis()) {
			getChannel().sendMessage(locale.get("error/card_untargetable")).queue();
			return false;
		}

		int pHP = op.getHP();
		int dmg = ally.getDmg();
		int attacks = 1 + ally.getEquipments().stream()
				.filter(e -> e.getCharms().contains(Charm.MULTISTRIKE.name()))
				.mapToInt(e -> Charm.MULTISTRIKE.getValue(e.getTier()))
				.sum();

		String outcome = "str/combat_skip";
		for (int i = 0; i < attacks; i++) {
			int eDmg = (int) (dmg * Math.pow(0.5, i));

			for (Evogear e : ally.getEquipments()) {
				JSONArray charms = e.getCharms();

				for (Object o : charms) {
					Charm c = Charm.valueOf(String.valueOf(o));
					switch (c) {
						case PIERCING -> op.modHP(-eDmg * c.getValue(e.getTier()) / 100);
						case WOUNDING -> op.getRegDeg().add(new Degen(eDmg * c.getValue(e.getTier()) / 100, 0.1));
						case DRAIN -> {
							int toDrain = Math.min(c.getValue(e.getTier()), op.getMP());
							if (toDrain > 0) {
								you.modMP(toDrain);
								op.modMP(-toDrain);
							}
						}
					}
				}
			}

			switch (you.getOrigin().synergy()) {
				case SHIKI -> {
					List<SlotColumn> slts = arena.getSlots(op.getSide());
					for (SlotColumn slt : slts) {
						if (slt.hasTop()) {
							slt.getTop().getStats().setDodge(-1);
						}
					}
				}
				case FALLEN -> {
					int degen = (int) Math.min(op.getRegDeg().peek() * 0.05, 0);
					op.modHP(degen);
					op.getRegDeg().reduce(Degen.class, degen);
				}
				case SPAWN -> op.getRegDeg().add(new Degen((int) (op.getBase().hp() * 0.05), 0.2));
			}

			Target t;
			if (enemy != null && i == 0) {
				t = enemy.asTarget(ON_DEFEND);
			} else {
				t = new Target();
			}
			trigger(ON_ATTACK, ally.asSource(ON_ATTACK), t);

			if (i == 0) {
				combat:
				if (enemy != null) {
					if (ally.getStats().popFlag(Flag.NO_COMBAT) || enemy.getStats().popFlag(Flag.IGNORE_COMBAT)) {
						break combat;
					}

					if (enemy.isSupporting()) {
						you.addKill();
						if (you.getKills() % 7 == 0 && you.getOrigin().synergy() == Race.SHINIGAMI) {
							arena.getBanned().add(enemy);
						} else {
							op.getGraveyard().add(enemy);
						}

						outcome = "str/combat_direct";
					} else {
						boolean dbl = op.getOrigin().synergy() == Race.WARBEAST && Calc.chance(2);
						int enemyStats = enemy.getActiveAttr(dbl);
						int eEquipStats = enemy.getActiveEquips(dbl);
						int eCombatStats = enemyStats;
						if (ally.getStats().popFlag(Flag.IGNORE_EQUIP)) {
							eCombatStats -= eEquipStats;
						}

						if (ally.getDmg() < eCombatStats) {
							trigger(ON_SUICIDE, ally.asSource(ON_SUICIDE), enemy.asTarget(ON_BLOCK));
							pHP = you.getHP();

							op.addKill();
							if (op.getKills() % 7 == 0 && op.getOrigin().synergy() == Race.SHINIGAMI) {
								arena.getBanned().add(ally);
							} else {
								you.getGraveyard().add(ally);
							}

							you.modHP(-(enemyStats - ally.getDmg()));
							reportEvent("str/combat", ally, enemy, locale.get("str/combat_defeat", pHP - you.getHP()));
							return true;
						} else {
							int block = enemy.getBlock();
							int dodge = enemy.getDodge();

							if (ally.getStats().popFlag(Flag.BLIND) && Calc.chance(50)) {
								trigger(ON_MISS, ally.asSource(ON_MISS));

								reportEvent("str/combat", ally, enemy, locale.get("str/combat_miss"));
								return true;
							} else if (!ally.getStats().popFlag(Flag.TRUE_STRIKE) && (enemy.getStats().popFlag(Flag.TRUE_BLOCK) || Calc.chance(block))) {
								trigger(ON_SUICIDE, ally.asSource(ON_SUICIDE), enemy.asTarget(ON_BLOCK));

								op.addKill();
								if (op.getKills() % 7 == 0 && op.getOrigin().synergy() == Race.SHINIGAMI) {
									arena.getBanned().add(ally);
								} else {
									you.getGraveyard().add(ally);
								}

								reportEvent("str/combat", ally, enemy, locale.get("str/combat_block", block));
								return true;
							} else if (!ally.getStats().popFlag(Flag.TRUE_STRIKE) && (enemy.getStats().popFlag(Flag.TRUE_DODGE) || Calc.chance(dodge))) {
								trigger(ON_MISS, ally.asSource(ON_MISS), enemy.asTarget(ON_DODGE));

								if (you.getOrigin().synergy() == Race.FABLED) {
									op.modHP((int) -(ally.getDmg() * 0.02));
								}

								reportEvent("str/combat", ally, enemy, locale.get("str/combat_dodge", dodge));
								return true;
							}

							if (ally.getDmg() > eCombatStats) {
								trigger(ON_HIT, ally.asSource(ON_HIT), enemy.asTarget(ON_LOSE));
								if (enemy.isDefending()) {
									dmg = 0;
								} else {
									dmg -= enemyStats;
								}

								you.addKill();
								if (you.getKills() % 7 == 0 && you.getOrigin().synergy() == Race.SHINIGAMI) {
									arena.getBanned().add(enemy);
								} else {
									op.getGraveyard().add(enemy);
								}

								outcome = "str/combat_success";
							} else {
								trigger(ON_CLASH, ally.asSource(ON_SUICIDE), enemy.asTarget(ON_LOSE));

								you.addKill();
								if (you.getKills() % 7 == 0 && you.getOrigin().synergy() == Race.SHINIGAMI) {
									arena.getBanned().add(enemy);
								} else {
									op.getGraveyard().add(enemy);
								}

								op.addKill();
								if (op.getKills() % 7 == 0 && op.getOrigin().synergy() == Race.SHINIGAMI) {
									arena.getBanned().add(ally);
								} else {
									you.getGraveyard().add(ally);
								}

								dmg = 0;
								outcome = "str/combat_clash";
							}
						}
					}
				} else {
					outcome = "str/combat_direct";
				}
			}

			eDmg = (int) (dmg * Math.pow(0.5, i));
			if (ally.getSlot() != null) {
				ally.setAvailable(false);
			}

			op.modHP(-eDmg);
			if (you.getOrigin().synergy() == Race.LICH) {
				you.modHP((int) ((pHP - op.getHP()) * 0.01));
			}
		}

		reportEvent("str/combat", ally, Utils.getOr(enemy, op.getName()), locale.get(outcome, pHP - op.getHP()));
		return false;
	}

	public I18N getLocale() {
		return locale;
	}

	public Map<Side, Hand> getHands() {
		return hands;
	}

	public Hand getCurrent() {
		return hands.get(getTurn() % 2 == 0 ? Side.TOP : Side.BOTTOM);
	}

	public Side getCurrentSide() {
		return getTurn() % 2 == 0 ? Side.TOP : Side.BOTTOM;
	}

	public Hand getOther() {
		return hands.get(getTurn() % 2 == 1 ? Side.TOP : Side.BOTTOM);
	}

	public Side getOtherSide() {
		return getTurn() % 2 == 1 ? Side.TOP : Side.BOTTOM;
	}

	public Arena getArena() {
		return arena;
	}

	public StateSnap getSnapshot() {
		return snapshot;
	}

	public StateSnap takeSnapshot() {
		try {
			snapshot = new StateSnap(this);
		} catch (IOException e) {
			Constants.LOGGER.warn("Failed to take snapshot", e);
		}

		return snapshot;
	}

	@SuppressWarnings("unchecked")
	public void restoreSnapshot(StateSnap snap) {
		restoring = true;

		try {
			arena.getBanned().clear();
			JSONArray banned = new JSONArray(IO.uncompress(snap.global().banned()));
			for (Object o : banned) {
				JSONObject jo = new JSONObject(o);
				Class<Drawable<?>> klass = (Class<Drawable<?>>) Class.forName(jo.getString("KLASS"));

				arena.getBanned().add(JSONUtils.fromJSON(String.valueOf(o), klass));
			}

			arena.setField(JSONUtils.fromJSON(IO.uncompress(snap.global().field()), Field.class));

			for (Map.Entry<Side, Hand> entry : hands.entrySet()) {
				Hand h = entry.getValue();
				Player p = snap.players().get(entry.getKey());

				h.getCards().clear();
				JSONArray cards = new JSONArray(IO.uncompress(p.cards()));
				for (Object o : cards) {
					JSONObject jo = new JSONObject(o);
					Class<Drawable<?>> klass = (Class<Drawable<?>>) Class.forName(jo.getString("KLASS"));

					h.getCards().add(JSONUtils.fromJSON(jo.toString(), klass));
				}

				h.getRealDeck().clear();
				JSONArray deck = new JSONArray(IO.uncompress(p.cards()));
				for (Object o : deck) {
					JSONObject jo = new JSONObject(o);
					Class<Drawable<?>> klass = (Class<Drawable<?>>) Class.forName(jo.getString("KLASS"));

					h.getRealDeck().add(JSONUtils.fromJSON(jo.toString(), klass));
				}
			}

			for (Map.Entry<Side, List<SlotColumn>> entry : getArena().getSlots().entrySet()) {
				List<SlotColumn> slts = entry.getValue();
				List<Slot> slots = snap.slots().get(entry.getKey());

				for (int i = 0; i < slts.size(); i++) {
					SlotColumn slt = slts.get(i);
					Slot slot = slots.get(i);

					slt.setState(slot.state());
					slt.setTop(JSONUtils.fromJSON(IO.uncompress(slot.top()), Senshi.class));
					if (slt.hasTop()) {
						JSONArray equips = new JSONArray(IO.uncompress(slot.equips()));
						for (Object o : equips) {
							JSONObject jo = new JSONObject(o);

							slt.getTop().getEquipments().add(JSONUtils.fromJSON(jo.toString(), Evogear.class));
						}
					}

					slt.setBottom(JSONUtils.fromJSON(IO.uncompress(slot.bottom()), Senshi.class));
				}
			}
		} catch (IOException | ClassNotFoundException e) {
			Constants.LOGGER.warn("Failed to restore snapshot", e);
		} finally {
			restoring = false;
		}
	}

	public List<SlotColumn> getSlots(Side s) {
		return arena.getSlots(s);
	}

	public void trigger(Trigger trigger) {
		if (restoring) return;

		List<Side> sides = List.of(getCurrentSide(), getOtherSide());

		for (Side side : sides) {
			trigger(trigger, side);
		}
	}

	public void trigger(Trigger trigger, Side side) {
		if (restoring) return;

		List<SlotColumn> slts = getSlots(side);
		for (SlotColumn slt : slts) {
			Senshi s = slt.getTop();
			if (s != null) {
				s.execute(new EffectParameters(trigger, s.asSource(trigger)));
			}

			s = slt.getBottom();
			if (s != null) {
				s.execute(new EffectParameters(trigger, s.asSource(trigger)));
			}
		}

		triggerEOTs(new EffectParameters(trigger));
	}

	public void trigger(Trigger trigger, Source source) {
		if (restoring) return;

		EffectParameters ep = new EffectParameters(trigger, source);
		source.execute(ep);

		triggerEOTs(new EffectParameters(trigger, source));
	}

	public void trigger(Trigger trigger, Source source, Target target) {
		if (restoring) return;

		EffectParameters ep = new EffectParameters(trigger, source, target);
		source.execute(ep);
		target.execute(ep);

		triggerEOTs(new EffectParameters(trigger, source, target));
	}

	public Set<EffectOverTime> getEOTs() {
		return eots;
	}

	public void triggerEOTs(EffectParameters ep) {
		Iterator<EffectOverTime> it = eots.iterator();
		while (it.hasNext()) {
			EffectOverTime effect = it.next();
			if (effect.lock().get()) continue;
			else if (effect.expired()) {
				it.remove();
				continue;
			}

			Predicate<Side> checkSide = s -> effect.side() == null || effect.side() == s;
			if (ep.size() == 0) {
				if (checkSide.test(getCurrentSide()) && effect.triggers().contains(ep.trigger())) {
					effect.effect().accept(effect, new EffectParameters(ep.trigger()));
					effect.decrease();

					if (effect.side() == null) {
						effect.lock().set(true);
					}
				}
			} else {
				if (ep.source() != null && checkSide.test(ep.source().side()) && effect.triggers().contains(ep.source().trigger())) {
					effect.effect().accept(effect, new EffectParameters(ep.source().trigger(), ep.source(), ep.targets()));
					effect.decrease();
				}

				for (Target t : ep.targets()) {
					if (checkSide.test(t.side()) && effect.triggers().contains(t.trigger())) {
						effect.effect().accept(effect, new EffectParameters(t.trigger(), ep.source(), ep.targets()));
						effect.decrease();
					}
				}
			}
		}
	}

	private void sendPlayerHand(Hand hand) {
		hand.getUser().openPrivateChannel()
				.flatMap(chn -> chn.sendFile(IO.getBytes(hand.render(locale), "png"), "hand.png"))
				.queue(m -> {
					if (hand.getLastMessage() != null) {
						m.getChannel().retrieveMessageById(hand.getLastMessage())
								.flatMap(Objects::nonNull, Message::delete)
								.queue();
					}

					hand.setLastMessage(m.getId());
				});
	}

	private BiFunction<String, Pair<String, String>, Pair<String, String>> replaceMessages(Message msg) {
		resetTimer();
		addButtons(msg);

		return (gid, tuple) -> {
			if (tuple != null) {
				Guild guild = Main.getApp().getShiro().getGuildById(gid);
				if (guild != null) {
					TextChannel channel = guild.getTextChannelById(tuple.getFirst());
					if (channel != null) {
						channel.retrieveMessageById(tuple.getSecond())
								.flatMap(Objects::nonNull, Message::delete)
								.queue();
					}
				}
			}

			return new Pair<>(msg.getChannel().getId(), msg.getId());
		};
	}

	private void reportEvent(String message, Object... args) {
		resetTimer();
		trigger(ON_TICK);

		List<Side> sides = List.of(getCurrentSide(), getOtherSide());
		for (Side side : sides) {
			Hand hand = hands.get(side);
			if (hand.getHP() == 0) {
				trigger(ON_WIN, side.getOther());
				trigger(ON_DEFEAT, side);

				if (hand.getOrigin().major() == Race.UNDEAD && hand.getMajorCooldown() == 0) {
					hand.setHP(1);
					hand.getRegDeg().add(new Regen((int) (hand.getBase().hp() * 0.5), 1 / 3d));
					hand.setMajorCooldown(4);
					continue;
				}

				reportResult("str/game_end",
						"<@" + hand.getUid() + ">",
						"<@" + hands.get(side.getOther()).getUid() + ">"
				);
				close(GameReport.SUCCESS);
				return;
			}

			List<SlotColumn> slts = getSlots(side);
			for (SlotColumn slt : slts) {
				Senshi s = slt.getTop();
				if (s != null) {
					s.getStats().removeExpired(AttrMod::isExpired);
					for (Evogear e : s.getEquipments()) {
						e.getStats().removeExpired(AttrMod::isExpired);
					}
				}

				s = slt.getBottom();
				if (s != null) {
					s.getStats().removeExpired(AttrMod::isExpired);
				}
			}
		}

		getChannel().sendMessage(locale.get(message, args))
				.addFile(IO.getBytes(arena.render(locale), "webp"), "game.webp")
				.queue(m -> messages.compute(m.getGuild().getId(), replaceMessages(m)));
	}

	private void reportResult(String message, Object... args) {
		getChannel().sendMessage(locale.get(message, args))
				.addFile(IO.getBytes(arena.render(locale), "webp"), "game.webp")
				.queue(m -> {
					for (Map.Entry<String, Pair<String, String>> entry : messages.entrySet()) {
						Pair<String, String> tuple = entry.getValue();
						if (tuple != null) {
							Guild guild = Main.getApp().getShiro().getGuildById(entry.getKey());
							if (guild != null) {
								TextChannel channel = guild.getTextChannelById(tuple.getFirst());
								if (channel != null) {
									channel.retrieveMessageById(tuple.getSecond())
											.flatMap(Objects::nonNull, Message::delete)
											.queue();
								}
							}
						}
					}
				});
	}

	private void addButtons(Message msg) {
		Hand curr = getCurrent();
		Map<Emoji, ThrowingConsumer<ButtonWrapper>> buttons = new LinkedHashMap<>() {{
			put(Utils.parseEmoji("▶"), w -> {
				if (getPhase() == Phase.COMBAT || getTurn() == 1) {
					nextTurn();
					return;
				}

				setPhase(Phase.COMBAT);
				reportEvent("str/game_combat_phase");
			});
			if (getPhase() == Phase.PLAN) {
				put(Utils.parseEmoji("⏩"), w -> nextTurn());

				int rem = curr.getRemainingDraws();
				if (rem > 0 && !curr.getRealDeck().isEmpty()) {
					put(Utils.parseEmoji("📤"), w -> {
						curr.manualDraw(1);
						reportEvent("str/draw_card", curr.getName(), 1, "");
						sendPlayerHand(curr);
					});

					if (rem > 1) {
						put(Utils.parseEmoji("📦"), w -> {
							curr.manualDraw(curr.getRemainingDraws());
							reportEvent("str/draw_card", curr.getName(), rem, "s");
							sendPlayerHand(curr);
						});
					}
				}
				if (curr.getOrigin().major() == Race.SPIRIT && !curr.getGraveyard().isEmpty() && curr.getMajorCooldown() == 0) {
					put(Utils.parseEmoji("\uD83C\uDF00"), w -> {
						List<StashedCard> cards = new ArrayList<>();
						Iterator<Drawable<?>> it = curr.getGraveyard().iterator();
						while (it.hasNext()) {
							Drawable<?> d = it.next();

							CardType type;
							if (d instanceof Senshi) {
								type = CardType.KAWAIPON;
							} else if (d instanceof Evogear) {
								type = CardType.EVOGEAR;
							} else {
								type = CardType.FIELD;
							}

							cards.add(new StashedCard(null, d.getCard(), type));
							arena.getBanned().add(d);
							it.remove();
						}

						curr.getCards().add(SynthesizeCommand.rollSynthesis(cards));
						curr.setMajorCooldown(3);
						reportEvent("str/spirit_synth", curr.getName());
						sendPlayerHand(curr);
					});
				}
				put(Utils.parseEmoji("🏳"), w -> {
					if (curr.isForfeit()) {
						reportResult("str/game_forfeit", "<@" + getCurrent().getUid() + ">");
						close(GameReport.SUCCESS);
						return;
					}

					curr.setForfeit(true);
					w.getHook().setEphemeral(true)
							.sendMessage(locale.get("str/confirm_forfeit"))
							.queue();
				});
			}
		}};

		Pages.buttonize(msg, buttons, true, false, u -> u.getId().equals(curr.getUid()));
	}

	public List<SlotColumn> getOpenSlots(Side side, boolean top) {
		List<SlotColumn> slts = new ArrayList<>(getSlots(side));
		slts.removeIf(sc -> sc.isLocked() || (top ? sc.hasTop() : sc.hasBottom()));

		return slts;
	}

	public String getString(String key, Object... params) {
		LocalizedString str = DAO.find(LocalizedString.class, new LocalizedDescId(key, locale));
		if (str != null) {
			return str.getValue().formatted(params);
		} else {
			return "";
		}
	}

	@Override
	protected void nextTurn() {
		Hand curr = getCurrent();
		trigger(ON_TURN_END, curr.getSide());

		curr.getCards().removeIf(d -> !d.isAvailable());
		curr.flushDiscard();

		if (curr.getOrigin().synergy() == Race.ANGEL) {
			curr.modHP(curr.getMP() * 10);
		}

		List<SlotColumn> slts = getSlots(curr.getSide());
		for (SlotColumn slt : slts) {
			Senshi s = slt.getTop();
			if (s != null) {
				s.setAvailable(true);

				s.reduceStasis(1);
				s.reduceSleep(1);
				s.reduceStun(1);
				s.reduceCooldown(1);

				s.getStats().expireMods();
				for (Evogear e : s.getEquipments()) {
					e.getStats().expireMods();
				}
			}

			s = slt.getBottom();
			if (s != null) {
				s.getStats().expireMods();
			}
		}

		super.nextTurn();
		setPhase(Phase.PLAN);
		curr = getCurrent();
		curr.modMP(curr.getBase().mpGain().apply(getTurn() - (curr.getSide() == Side.TOP ? 1 : 0)));
		curr.applyVoTs();
		curr.reduceMinorCooldown(1);
		curr.reduceMajorCooldown(1);

		trigger(ON_TURN_BEGIN, curr.getSide());
		reportEvent("str/game_turn_change", "<@" + curr.getUid() + ">", getTurn());
		sendPlayerHand(curr);

		takeSnapshot();
	}

	@Override
	protected void resetTimer() {
		super.resetTimer();
		getCurrent().setForfeit(false);

		for (EffectOverTime eot : eots) {
			eot.lock().set(false);
		}
	}

	@Override
	public void setPhase(Phase phase) {
		super.setPhase(phase);
		Trigger trigger = switch (phase) {
			case PLAN -> ON_PLAN;
			case COMBAT -> ON_COMBAT;
		};

		trigger(trigger, getCurrentSide());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Shoukan shoukan = (Shoukan) o;
		return seed == shoukan.seed && singleplayer == shoukan.singleplayer;
	}

	@Override
	public int hashCode() {
		return Objects.hash(seed, singleplayer);
	}
}
