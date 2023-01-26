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

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.AllowedMentions;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.github.ygimenez.method.Pages;
import com.github.ygimenez.model.ButtonWrapper;
import com.github.ygimenez.model.ThrowingConsumer;
import com.kuuhaku.Constants;
import com.kuuhaku.Main;
import com.kuuhaku.command.misc.SynthesizeCommand;
import com.kuuhaku.controller.DAO;
import com.kuuhaku.exceptions.ActivationException;
import com.kuuhaku.game.engine.GameInstance;
import com.kuuhaku.game.engine.GameReport;
import com.kuuhaku.game.engine.PhaseConstraint;
import com.kuuhaku.game.engine.PlayerAction;
import com.kuuhaku.interfaces.shoukan.Drawable;
import com.kuuhaku.interfaces.shoukan.EffectHolder;
import com.kuuhaku.model.common.BondedList;
import com.kuuhaku.model.common.shoukan.*;
import com.kuuhaku.model.enums.CardType;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.enums.shoukan.*;
import com.kuuhaku.model.persistent.id.LocalizedId;
import com.kuuhaku.model.persistent.shoukan.*;
import com.kuuhaku.model.persistent.user.StashedCard;
import com.kuuhaku.model.records.PseudoUser;
import com.kuuhaku.model.records.shoukan.*;
import com.kuuhaku.model.records.shoukan.history.Match;
import com.kuuhaku.model.records.shoukan.history.Turn;
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
import kotlin.Triple;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.collections4.list.TreeList;
import org.apache.commons.lang3.ArrayUtils;
import org.intellij.lang.annotations.MagicConstant;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static com.kuuhaku.model.enums.shoukan.Trigger.*;

public class Shoukan extends GameInstance<Phase> {
	private final long seed = ThreadLocalRandom.current().nextLong();
	private final String GIF_PATH = "https://raw.githubusercontent.com/OtagamerZ/ShoukanAssets/master/gifs/";

	private final ShoukanParams params;
	private final Arena arena;
	private final Map<Side, Hand> hands;
	private final Map<String, String> messages = new HashMap<>();
	private final Set<EffectOverTime> eots = new HashSet<>();
	private final List<Turn> turns = new TreeList<>();

	private final boolean singleplayer;
	private StateSnap snapshot = null;
	private boolean restoring = true;
	private boolean history = false;

	public Shoukan(I18N locale, ShoukanParams params, User p1, User p2) {
		this(locale, params, p1.getId(), p2.getId());
	}

	public Shoukan(I18N locale, ShoukanParams params, String p1, String p2) {
		super(locale, new String[]{p1, p2});

		this.params = Utils.getOr(params, new ShoukanParams());
		this.arena = new Arena(this);
		this.hands = Map.of(
				Side.TOP, new Hand(p1, this, Side.TOP),
				Side.BOTTOM, new Hand(p2, this, Side.BOTTOM)
		);
		this.singleplayer = p1.equals(p2);

		setTimeout(turn -> reportResult(GameReport.GAME_TIMEOUT, "str/game_wo", "<@" + getOther().getUid() + ">"), 5, TimeUnit.MINUTES);
	}

	@Override
	protected boolean validate(Message message) {
		return ((Predicate<Message>) m -> Utils.equalsAny(m.getAuthor().getId(), getPlayers()))
				.and(m -> singleplayer
						|| getTurn() % 2 == ArrayUtils.indexOf(getPlayers(), m.getAuthor().getId())
						|| hands.values().stream().anyMatch(h -> h.getUid().equals(m.getAuthor().getId()) && h.selectionPending())
				)
				.test(message);
	}

	@Override
	protected void begin() {
		PLAYERS.addAll(Arrays.asList(getPlayers()));
		restoring = false;

		for (Hand h : hands.values()) {
			h.manualDraw(5);
		}

		setPhase(Phase.PLAN);

		Hand curr = getCurrent();
		curr.modMP(curr.getBase().mpGain().apply(getTurn() - (curr.getSide() == Side.TOP ? 1 : 0)));

		curr.showHand();
		reportEvent("str/game_start", "<@" + curr.getUid() + ">");

		takeSnapshot();
	}

	@Override
	protected void runtime(User user, String value) throws InvocationTargetException, IllegalAccessException {
		Pair<Method, JSONObject> action = toAction(value.toLowerCase().replace(" ", ""));
		if (action != null) {
			Method m = action.getFirst();

			for (Hand h : hands.values()) {
				if (h.getUid().equals(user.getId()) && h.selectionPending()) {
					if (!m.getName().equals("select")) {
						reportEvent("error/pending_choice");
						return;
					}

					m.invoke(this, h.getSide(), action.getSecond());
					return;
				}
			}

			if ((boolean) m.invoke(this, getCurrentSide(), action.getSecond())) {
				getCurrent().showHand();
			}
		}
	}

	@PlayerAction("reload")
	private boolean reload(Side side, JSONObject args) {
		reportEvent("str/game_reload", getCurrent().getName());
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\d+),(?<mode>[adb]),(?<inField>[1-5])(?<notCombat>,nc)?")
	private boolean placeCard(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (!Utils.between(args.getInt("inHand"), 1, curr.getCards().size())) {
			getChannel().sendMessage(getLocale().get("error/invalid_hand_index")).queue();
			return false;
		}

		if (curr.getCards().get(args.getInt("inHand") - 1) instanceof Senshi chosen) {
			if (!chosen.isAvailable()) {
				getChannel().sendMessage(getLocale().get("error/card_unavailable")).queue();
				return false;
			} else if (chosen.getHPCost() >= curr.getHP()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_hp")).queue();
				return false;
			} else if (chosen.getMPCost() > curr.getMP()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_mp")).queue();
				return false;
			} else if (chosen.getSCCost() > curr.getDiscard().size()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_sc")).queue();
				return false;
			}
		} else {
			if (args.getString("mode").equals("b") && placeProxy(curr, args)) return true;

			getChannel().sendMessage(getLocale().get("error/wrong_card_type")).queue();
			return false;
		}

		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);
		if (slot.isLocked()) {
			int time = slot.getLock();

			if (time == -1) {
				getChannel().sendMessage(getLocale().get("error/slot_locked_perma")).queue();
			} else {
				getChannel().sendMessage(getLocale().get("error/slot_locked", time)).queue();
			}
			return false;
		}

		Senshi copy;
		if (args.getBoolean("notCombat")) {
			if (slot.hasBottom()) {
				getChannel().sendMessage(getLocale().get("error/slot_occupied")).queue();
				return false;
			}

			curr.consumeHP(chosen.getHPCost());
			curr.consumeMP(chosen.getMPCost());
			List<Drawable<?>> consumed = curr.consumeSC(chosen.getSCCost());

			chosen.setAvailable(curr.getOrigin().synergy() == Race.HERALD && Calc.chance(5));
			slot.setBottom(copy = chosen.withCopy(s -> {
				switch (args.getString("mode")) {
					case "d" -> s.setDefending(true);
					case "b" -> s.setFlipped(true);
				}

				if (!consumed.isEmpty()) {
					s.getStats().getData().put("consumed", consumed);
				}
			}));
		} else {
			if (slot.hasTop()) {
				getChannel().sendMessage(getLocale().get("error/slot_occupied")).queue();
				return false;
			}

			curr.consumeHP(chosen.getHPCost());
			curr.consumeMP(chosen.getMPCost());
			List<Drawable<?>> consumed = curr.consumeSC(chosen.getSCCost());

			chosen.setAvailable(curr.getOrigin().synergy() == Race.HERALD && Calc.chance(5));
			slot.setTop(copy = chosen.withCopy(s -> {
				switch (args.getString("mode")) {
					case "d" -> s.setDefending(true);
					case "b" -> s.setFlipped(true);
				}

				if (!consumed.isEmpty()) {
					s.getStats().getData().put("consumed", consumed);
				}
			}));
		}

		curr.getData().put("last_summon", copy);
		reportEvent("str/place_card",
				curr.getName(),
				copy.isFlipped() ? getLocale().get("str/a_card") : copy,
				copy.getState().toString(getLocale())
		);
		return true;
	}

	private boolean placeProxy(Hand hand, JSONObject args) {
		if (hand.getCards().get(args.getInt("inHand") - 1) instanceof Evogear chosen && chosen.isSpell()) {
			if (!chosen.isAvailable()) {
				getChannel().sendMessage(getLocale().get("error/card_unavailable")).queue();
				return false;
			} else if (chosen.getHPCost() >= hand.getHP()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_hp")).queue();
				return false;
			} else if (chosen.getMPCost() > hand.getMP()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_mp")).queue();
				return false;
			} else if (chosen.getSCCost() > hand.getDiscard().size()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_sc")).queue();
				return false;
			}

			int locktime = hand.getLockTime(Lock.SPELL);
			if (locktime > 0) {
				getChannel().sendMessage(getLocale().get("error/spell_locked", locktime)).queue();
				return false;
			}
		} else {
			getChannel().sendMessage(getLocale().get("error/wrong_card_type")).queue();
			return false;
		}

		SlotColumn slot = arena.getSlots(hand.getSide()).get(args.getInt("inField") - 1);
		if (slot.isLocked()) {
			int time = slot.getLock();

			if (time == -1) {
				getChannel().sendMessage(getLocale().get("error/slot_locked_perma")).queue();
			} else {
				getChannel().sendMessage(getLocale().get("error/slot_locked", time)).queue();
			}
			return false;
		}

		Senshi proxy = new CardProxy(chosen);

		if (args.getBoolean("notCombat")) {
			if (slot.hasBottom()) {
				getChannel().sendMessage(getLocale().get("error/slot_occupied")).queue();
				return false;
			}

			hand.consumeHP(chosen.getHPCost());
			hand.consumeMP(chosen.getMPCost());
			List<Drawable<?>> consumed = hand.consumeSC(chosen.getSCCost());

			if (!consumed.isEmpty()) {
				proxy.getStats().getData().put("consumed", consumed);
			}

			chosen.setAvailable(false);
			slot.setBottom(proxy);
		} else {
			if (slot.hasTop()) {
				getChannel().sendMessage(getLocale().get("error/slot_occupied")).queue();
				return false;
			}

			hand.consumeHP(chosen.getHPCost());
			hand.consumeMP(chosen.getMPCost());
			List<Drawable<?>> consumed = hand.consumeSC(chosen.getSCCost());

			if (!consumed.isEmpty()) {
				proxy.getStats().getData().put("consumed", consumed);
			}

			chosen.setAvailable(false);
			slot.setTop(proxy);
		}

		reportEvent("str/place_card",
				hand.getName(),
				proxy.isFlipped() ? getLocale().get("str/a_card") : proxy,
				proxy.getState().toString(getLocale())
		);
		return true;
	}

	public boolean activateProxy(Senshi proxy, EffectParameters ep) {
		if (!(proxy instanceof CardProxy p)) return false;

		Evogear e = p.getOriginal();
		Hand hand = e.getHand();

		Targeting tgt = switch (e.getTargetType()) {
			case NONE -> new Targeting(hand, -1, -1);
			case ALLY -> {
				if (ep.allies().length == 0) {
					yield null;
				}

				yield new Targeting(hand, ep.allies()[0].index(), -1);
			}
			case ENEMY -> {
				if (ep.enemies().length == 0) {
					yield null;
				}

				yield new Targeting(hand, -1, ep.enemies()[0].index());
			}
			case BOTH -> {
				if (ep.allies().length == 0 || ep.enemies().length == 0) {
					yield null;
				}

				yield new Targeting(hand, ep.allies()[0].index(), ep.enemies()[0].index());
			}
		};

		if (tgt == null) return false;

		Senshi enemy = tgt.enemy();
		if (enemy != null && enemy.isProtected()) {
			hand.getGraveyard().add(p);
			hand.getData().put("last_spell", e);
			trigger(ON_SPELL, hand.getSide());
			getChannel().sendMessage(getLocale().get("str/spell_shield")).queue();
			return false;
		} else if (!tgt.validate(e.getTargetType())) {
			getChannel().sendMessage(getLocale().get("error/target", getLocale().get("str/target_" + e.getTargetType()))).queue();
			return false;
		}

		if (e.execute(ep)) {
			hand.getGraveyard().add(p);
			hand.getData().put("last_spell", e);
			trigger(ON_SPELL, hand.getSide());
			return true;
		}

		return false;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\d+),(?<inField>[1-5])")
	private boolean equipCard(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (!Utils.between(args.getInt("inHand"), 1, curr.getCards().size())) {
			getChannel().sendMessage(getLocale().get("error/invalid_hand_index")).queue();
			return false;
		}

		if (curr.getCards().get(args.getInt("inHand") - 1) instanceof Evogear chosen && !chosen.isSpell()) {
			if (!chosen.isAvailable()) {
				getChannel().sendMessage(getLocale().get("error/card_unavailable")).queue();
				return false;
			} else if (chosen.getHPCost() >= curr.getHP()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_hp")).queue();
				return false;
			} else if (chosen.getMPCost() > curr.getMP()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_mp")).queue();
				return false;
			} else if (chosen.getSCCost() > curr.getDiscard().size()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_sc")).queue();
				return false;
			}
		} else {
			getChannel().sendMessage(getLocale().get("error/wrong_card_type")).queue();
			return false;
		}

		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);
		if (!slot.hasTop()) {
			getChannel().sendMessage(getLocale().get("error/missing_card", slot.getIndex() + 1)).queue();
			return false;
		}

		Senshi target = slot.getTop();
		curr.consumeHP(chosen.getHPCost());
		curr.consumeMP(chosen.getMPCost());
		List<Drawable<?>> consumed = curr.consumeSC(chosen.getSCCost());

		chosen.setAvailable(false);
		Evogear copy = chosen.copy();
		if (!consumed.isEmpty()) {
			copy.getStats().getData().put("consumed", consumed);
		}

		target.getEquipments().add(copy);
		curr.getData().put("last_equipment", copy);
		reportEvent("str/equip_card",
				curr.getName(),
				copy.isFlipped() ? getLocale().get("str/an_equipment") : copy,
				target.isFlipped() ? getLocale().get("str/a_card") : target
		);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\d+)f")
	private boolean placeField(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (!Utils.between(args.getInt("inHand"), 1, curr.getCards().size())) {
			getChannel().sendMessage(getLocale().get("error/invalid_hand_index")).queue();
			return false;
		}

		if (curr.getCards().get(args.getInt("inHand") - 1) instanceof Field chosen) {
			if (!chosen.isAvailable()) {
				getChannel().sendMessage(getLocale().get("error/card_unavailable")).queue();
				return false;
			}
		} else {
			getChannel().sendMessage(getLocale().get("error/wrong_card_type")).queue();
			return false;
		}

		chosen.setAvailable(false);
		arena.setField(chosen.copy());
		curr.getData().put("last_field", chosen);
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
			getChannel().sendMessage(getLocale().get("error/missing_card", slot.getIndex() + 1)).queue();
			return false;
		}

		Senshi chosen = nc ? slot.getBottom() : slot.getTop();
		if (!chosen.isAvailable()) {
			getChannel().sendMessage(getLocale().get("error/card_unavailable")).queue();
			return false;
		} else if (chosen.hasSwitched()) {
			getChannel().sendMessage(getLocale().get("error/card_switched")).queue();
			return false;
		}

		if (chosen.isFlipped()) {
			chosen.setFlipped(false);
		} else {
			chosen.setDefending(!chosen.isDefending());
		}

		chosen.setSwitched(true);
		reportEvent("str/flip_card", curr.getName(), chosen, chosen.getState().toString(getLocale()));
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inField>[1-5]),p")
	private boolean promoteCard(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);

		if (!slot.hasBottom()) {
			getChannel().sendMessage(getLocale().get("error/missing_card", slot.getIndex() + 1)).queue();
			return false;
		} else if (slot.hasTop()) {
			getChannel().sendMessage(getLocale().get("error/promote_blocked")).queue();
			return false;
		}

		Senshi chosen = slot.getBottom();
		if (!chosen.isAvailable()) {
			getChannel().sendMessage(getLocale().get("error/card_unavailable")).queue();
			return false;
		}

		slot.swap();

		reportEvent("str/promote_card",
				curr.getName(),
				chosen.isFlipped() ? getLocale().get("str/a_card") : chosen
		);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inField>[1-5]),s(?<notCombat>,nc)?")
	private boolean sacrificeCard(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);

		boolean nc = args.getBoolean("notCombat");
		if ((nc && !slot.hasBottom()) || (!nc && !slot.hasTop())) {
			getChannel().sendMessage(getLocale().get("error/missing_card", slot.getIndex() + 1)).queue();
			return false;
		}

		Senshi chosen = nc ? slot.getBottom() : slot.getTop();
		if (chosen.getTags().contains("tag/fixed")) {
			getChannel().sendMessage(getLocale().get("error/card_fixed")).queue();
			return false;
		} else if (chosen.getHPCost() / 2 >= curr.getHP()) {
			getChannel().sendMessage(getLocale().get("error/not_enough_hp_sacrifice")).queue();
			return false;
		} else if (chosen.getMPCost() / 2 > curr.getMP()) {
			getChannel().sendMessage(getLocale().get("error/not_enough_mp_sacrifice")).queue();
			return false;
		}

		curr.consumeHP(chosen.getHPCost() / 2);
		curr.consumeMP(chosen.getMPCost() / 2);

		trigger(ON_SACRIFICE, chosen.asSource(ON_SACRIFICE));

		curr.getGraveyard().add(chosen);

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
				getChannel().sendMessage(getLocale().get("error/missing_card", slot.getIndex() + 1)).queue();
				return false;
			}

			Senshi chosen = nc ? slot.getBottom() : slot.getTop();
			if (chosen.getHPCost() / 2 >= curr.getHP()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_hp_sacrifice")).queue();
				return false;
			} else if (chosen.getMPCost() / 2 > curr.getMP()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_mp_sacrifice")).queue();
				return false;
			}

			hp += chosen.getHPCost() / 2;
			mp += chosen.getMPCost() / 2;
			cards.add(chosen);
		}

		curr.consumeHP(hp);
		curr.consumeMP(mp);

		for (Drawable<?> chosen : cards) {
			trigger(ON_SACRIFICE, chosen.asSource(ON_SACRIFICE));
		}

		curr.getGraveyard().addAll(cards);

		reportEvent("str/sacrifice_card", curr.getName(),
				Utils.properlyJoin(getLocale().get("str/and")).apply(cards.stream().map(Drawable::toString).toList())
		);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\d+),d")
	private boolean discardCard(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (!Utils.between(args.getInt("inHand"), 1, curr.getCards().size())) {
			getChannel().sendMessage(getLocale().get("error/invalid_hand_index")).queue();
			return false;
		}

		Drawable<?> chosen = curr.getCards().get(args.getInt("inHand") - 1);
		if (!chosen.isAvailable()) {
			getChannel().sendMessage(getLocale().get("error/card_unavailable")).queue();
			return false;
		}

		curr.getDiscard().add(chosen);

		if (curr.getOrigin().synergy() == Race.FAMILIAR) {
			if (Calc.chance(25)) {
				List<? extends EffectHolder<?>> available = curr.getCards().stream()
						.filter(d -> d instanceof EffectHolder<?>)
						.filter(Drawable::isAvailable)
						.map(d -> (EffectHolder<?>) d)
						.toList();

				Utils.getRandomEntry(available).getStats().setMana(-1);
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
			if (!Utils.between(idx, 1, curr.getCards().size())) {
				getChannel().sendMessage(getLocale().get("error/invalid_hand_index")).queue();
				return false;
			}

			Drawable<?> chosen = curr.getCards().get(idx - 1);
			if (!chosen.isAvailable()) {
				getChannel().sendMessage(getLocale().get("error/card_unavailable")).queue();
				return false;
			}

			cards.add(chosen);
		}

		curr.getDiscard().addAll(cards);

		if (curr.getOrigin().synergy() == Race.FAMILIAR) {
			if (Calc.chance(25)) {
				List<? extends EffectHolder<?>> available = curr.getCards().stream()
						.filter(d -> d instanceof EffectHolder<?>)
						.filter(Drawable::isAvailable)
						.map(d -> (EffectHolder<?>) d)
						.toList();

				Utils.getRandomEntry(available).getStats().setMana(-1);
			}
		}

		reportEvent("str/discard_card", curr.getName(),
				Utils.properlyJoin(getLocale().get("str/and")).apply(cards.stream().map(Drawable::toString).toList())
		);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\d+)s(?:,(?<target1>[1-5]))?(?:,(?<target2>[1-5]))?")
	private boolean activate(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (!Utils.between(args.getInt("inHand"), 1, curr.getCards().size())) {
			getChannel().sendMessage(getLocale().get("error/invalid_hand_index")).queue();
			return false;
		}

		if (curr.getCards().get(args.getInt("inHand") - 1) instanceof Evogear chosen && chosen.isSpell()) {
			if (!chosen.isAvailable()) {
				getChannel().sendMessage(getLocale().get("error/card_unavailable")).queue();
				return false;
			} else if (chosen.getHPCost() >= curr.getHP()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_hp")).queue();
				return false;
			} else if (chosen.getMPCost() > curr.getMP()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_mp")).queue();
				return false;
			} else if (chosen.getSCCost() > curr.getDiscard().size()) {
				getChannel().sendMessage(getLocale().get("error/not_enough_sc")).queue();
				return false;
			}

			int locktime = curr.getLockTime(Lock.SPELL);
			if (locktime > 0) {
				getChannel().sendMessage(getLocale().get("error/spell_locked", locktime)).queue();
				return false;
			}
		} else {
			getChannel().sendMessage(getLocale().get("error/wrong_card_type")).queue();
			return false;
		}

		Targeting tgt = switch (chosen.getTargetType()) {
			case NONE -> new Targeting(curr, -1, -1);
			case ALLY -> new Targeting(curr, args.getInt("target1") - 1, -1);
			case ENEMY -> new Targeting(curr, -1, args.getInt("target1") - 1);
			case BOTH -> new Targeting(curr, args.getInt("target1") - 1, args.getInt("target2") - 1);
		};

		List<Drawable<?>> stack = (chosen.getTier() > 3 ? arena.getBanned() : curr.getGraveyard());

		Senshi enemy = tgt.enemy();
		if (enemy != null && enemy.isProtected()) {
			curr.consumeHP(chosen.getHPCost());
			curr.consumeMP(chosen.getMPCost());
			List<Drawable<?>> consumed = curr.consumeSC(chosen.getSCCost());

			Evogear copy = chosen.copy();
			if (!consumed.isEmpty()) {
				copy.getStats().getData().put("consumed", consumed);
			}

			if (!chosen.getStats().popFlag(Flag.FREE_ACTION)) {
				chosen.setAvailable(false);
				stack.add(copy);
			}

			curr.getData().put("last_spell", copy);
			trigger(ON_SPELL, side);
			reportEvent("str/spell_shield");
			return false;
		} else if (!tgt.validate(chosen.getTargetType())) {
			getChannel().sendMessage(getLocale().get("error/target", getLocale().get("str/target_" + chosen.getTargetType()))).queue();
			return false;
		}

		curr.consumeHP(chosen.getHPCost());
		curr.consumeMP(chosen.getMPCost());
		List<Drawable<?>> consumed = curr.consumeSC(chosen.getSCCost());

		Evogear copy = chosen.copy();
		if (!consumed.isEmpty()) {
			copy.getStats().getData().put("consumed", consumed);
		}

		if (!chosen.execute(chosen.toParameters(tgt))) {
			stack.remove(copy);
			chosen.setAvailable(true);
			return false;
		}

		if (!chosen.getStats().popFlag(Flag.FREE_ACTION)) {
			chosen.setAvailable(false);
			stack.add(copy);
		}

		curr.getData().put("last_spell", copy);
		trigger(ON_SPELL, side);
		reportEvent("str/activate_card",
				curr.getName(),
				chosen.getTags().contains("tag/secret") ? getLocale().get("str/a_spell") : chosen
		);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<choice>\\d+)")
	private boolean select(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (!curr.selectionPending()) return false;

		Triple<List<Drawable<?>>, Boolean, CompletableFuture<Drawable<?>>> selection = curr.getSelection();
		if (!Utils.between(args.getInt("choice"), 1, selection.getFirst().size())) {
			getChannel().sendMessage(getLocale().get("error/invalid_selection_index")).queue();
			return false;
		}

		Drawable<?> chosen = selection.getFirst().get(args.getInt("choice") - 1);
		selection.getThird().complete(chosen);

		reportEvent("str/select_card", curr.getName(), args.getInt("choice"));
		return true;
	}

	@PhaseConstraint({"PLAN", "COMBAT"})
	@PlayerAction("(?<inField>[1-5])a(?:,(?<target1>[1-5]))?(?:,(?<target2>[1-5]))?")
	private boolean special(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);

		if (!slot.hasTop()) {
			getChannel().sendMessage(getLocale().get("error/missing_card", slot.getIndex() + 1)).queue();
			return false;
		}

		int locktime = curr.getLockTime(Lock.EFFECT);
		if (locktime > 0) {
			getChannel().sendMessage(getLocale().get("error/effect_locked", locktime)).queue();
			return false;
		}

		Senshi chosen = slot.getTop();
		if (!chosen.isAvailable()) {
			getChannel().sendMessage(getLocale().get("error/card_unavailable")).queue();
			return false;
		} else if (chosen.isFlipped()) {
			getChannel().sendMessage(getLocale().get("error/card_flipped")).queue();
			return false;
		} else if (chosen.getCooldown() > 0) {
			getChannel().sendMessage(getLocale().get("error/card_cooldown", chosen.getCooldown())).queue();
			return false;
		} else if (curr.getMP() < 1) {
			getChannel().sendMessage(getLocale().get("error/not_enough_mp")).queue();
			return false;
		} else if (!chosen.hasAbility()) {
			getChannel().sendMessage(getLocale().get("error/card_no_special")).queue();
			return false;
		} else if (chosen.isSealed()) {
			getChannel().sendMessage(getLocale().get("error/card_sealed")).queue();
			return false;
		}

		TargetType type = chosen.getStats().getData().getEnum(TargetType.class, "targeting", TargetType.NONE);
		Targeting tgt = switch (type) {
			case NONE -> new Targeting(curr, -1, -1);
			case ALLY -> new Targeting(curr, args.getInt("target1") - 1, -1);
			case ENEMY -> new Targeting(curr, -1, args.getInt("target1") - 1);
			case BOTH -> new Targeting(curr, args.getInt("target1") - 1, args.getInt("target2") - 1);
		};

		Senshi enemy = tgt.enemy();
		if (enemy != null) {
			if (chosen.getTarget() != null && !Objects.equals(chosen.getTarget(), enemy)) {
				getChannel().sendMessage(getLocale().get("error/card_taunted", chosen.getTarget(), chosen.getTarget().getIndex() + 1)).queue();
				return false;
			}

			if (enemy.isProtected()) {
				curr.consumeMP(1);
				if (!chosen.popFlag(Flag.FREE_ACTION)) {
					chosen.setAvailable(false);
				}

				curr.getData().put("last_ability", chosen);
				trigger(ON_ABILITY, side);
				reportEvent("str/spell_shield");
				return false;
			}
		}

		if (!tgt.validate(type)) {
			getChannel().sendMessage(getLocale().get("error/target", getLocale().get("str/target_" + type))).queue();
			return false;
		} else if (!trigger(ON_ACTIVATE, chosen.asSource(ON_ACTIVATE), tgt.targets(ON_EFFECT_TARGET))) {
			return false;
		}

		curr.consumeMP(1);
		if (getPhase() != Phase.PLAN && !chosen.popFlag(Flag.FREE_ACTION)) {
			chosen.setAvailable(false);
		}

		curr.getData().put("last_ability", chosen);
		trigger(ON_ABILITY, side);
		reportEvent("str/card_special", curr.getName(), chosen);
		return !curr.selectionPending();
	}

	@PhaseConstraint("COMBAT")
	@PlayerAction("(?<inField>[1-5]),self")
	private boolean selfDamage(Side side, JSONObject args) {
		Hand you = hands.get(side);
		if (you.getLockTime(Lock.TAUNT) == 0) {
			getChannel().sendMessage(getLocale().get("error/not_taunted")).queue();
			return false;
		}

		SlotColumn yourSlot = arena.getSlots(you.getSide()).get(args.getInt("inField") - 1);

		if (!yourSlot.hasTop()) {
			getChannel().sendMessage(getLocale().get("error/missing_card", yourSlot.getIndex() + 1)).queue();
			return false;
		}

		Senshi ally = yourSlot.getTop();
		if (!ally.canAttack()) {
			getChannel().sendMessage(getLocale().get("error/card_cannot_attack")).queue();
			return false;
		}

		int pHP = you.getHP();
		int dmg = ally.getActiveAttr();

		for (Evogear e : ally.getEquipments()) {
			JSONArray charms = e.getCharms();

			for (Object o : charms) {
				Charm c = Charm.valueOf(String.valueOf(o));
				switch (c) {
					case PIERCING -> you.modHP((int) -(dmg * 0.5 * c.getValue(e.getTier()) / 100));
					case WOUNDING -> {
						int val = (int) (dmg * 0.5 * c.getValue(e.getTier()) / 100);
						you.getRegDeg().add(val);

						if (you.getOrigin().synergy() == Race.FIEND && Calc.chance(5)) {
							you.getRegDeg().add(val);
						}
					}
				}
			}
		}

		switch (you.getOrigin().synergy()) {
			case SHIKIGAMI -> {
				List<SlotColumn> slts = arena.getSlots(you.getSide());
				for (SlotColumn slt : slts) {
					if (slt.hasTop()) {
						slt.getTop().getStats().setDodge(-1);
					}
				}
			}
			case FALLEN -> {
				if (you.getRegDeg().peek() < 0) {
					you.getRegDeg().apply(0.05);
				}
			}
			case SPAWN -> you.getRegDeg().add(you.getBase().hp() * 0.05);
		}

		if (ally.getSlot() != null && !ally.popFlag(Flag.FREE_ACTION)) {
			ally.setAvailable(false);
		}

		you.modHP((int) -(dmg * 0.5));
		reportEvent("str/combat_self", ally, pHP - you.getHP());
		return false;
	}

	@PhaseConstraint("COMBAT")
	@PlayerAction("(?<inField>[1-5])(?:,(?<target>[1-5]))?")
	private boolean attack(Side side, JSONObject args) {
		Hand you = hands.get(side);
		SlotColumn yourSlot = arena.getSlots(you.getSide()).get(args.getInt("inField") - 1);

		if (!yourSlot.hasTop()) {
			getChannel().sendMessage(getLocale().get("error/missing_card", yourSlot.getIndex() + 1)).queue();
			return false;
		}

		Senshi ally = yourSlot.getTop();
		if (!ally.canAttack()) {
			getChannel().sendMessage(getLocale().get("error/card_cannot_attack")).queue();
			return false;
		}

		Hand op = hands.get(side.getOther());
		int pHP = op.getHP();

		Senshi enemy = null;
		if (args.getBoolean("target")) {
			SlotColumn opSlot = arena.getSlots(op.getSide()).get(args.getInt("target") - 1);

			if (!opSlot.hasTop()) {
				if (!opSlot.hasBottom()) {
					getChannel().sendMessage(getLocale().get("error/missing_card", opSlot.getIndex() + 1)).queue();
					return false;
				}

				enemy = opSlot.getBottom();
			} else {
				enemy = opSlot.getTop();
			}

			if (ally.getTarget() != null && !Objects.equals(ally.getTarget(), enemy)) {
				getChannel().sendMessage(getLocale().get("error/card_taunted", ally.getTarget(), ally.getTarget().getIndex() + 1)).queue();
				return false;
			}
		}

		if (enemy == null && !arena.isFieldEmpty(op.getSide()) && !ally.popFlag(Flag.DIRECT)) {
			getChannel().sendMessage(getLocale().get("error/field_not_empty")).queue();
			return false;
		} else if (enemy != null && enemy.isStasis()) {
			getChannel().sendMessage(getLocale().get("error/card_untargetable")).queue();
			return false;
		}

		Target t;
		int posHash;
		if (enemy != null) {
			t = enemy.asTarget(ON_DEFEND);
			posHash = enemy.posHash();
		} else {
			t = new Target();
			posHash = 0;
		}
		trigger(ON_ATTACK, ally.asSource(ON_ATTACK), t);

		int dmg = ally.getActiveAttr();
		int lifesteal = you.getBase().lifesteal();
		int thorns = 0;
		float dmgMult = 1;
		if (getTurn() < 3 || you.getLockTime(Lock.TAUNT) > 0) {
			dmgMult /= 2;
		}

		String outcome = "str/combat_skip";
		try {
			if ((enemy == null || posHash == enemy.posHash()) && ally.canAttack()) {
				for (Evogear e : ally.getEquipments()) {
					JSONArray charms = e.getCharms();

					for (Object o : charms) {
						Charm c = Charm.valueOf(String.valueOf(o));
						switch (c) {
							case PIERCING -> op.modHP((int) -(dmg * dmgMult * c.getValue(e.getTier()) / 100));
							case WOUNDING -> {
								int val = (int) (dmg * dmgMult * c.getValue(e.getTier()) / 100);
								op.getRegDeg().add(val);

								if (you.getOrigin().synergy() == Race.FIEND && Calc.chance(5)) {
									op.getRegDeg().add(val);
								}
							}
							case DRAIN -> {
								int toDrain = Math.min(c.getValue(e.getTier()), op.getMP());
								if (toDrain > 0) {
									you.modMP(toDrain);
									op.modMP(-toDrain);
								}
							}
							case LIFESTEAL -> lifesteal += c.getValue(e.getTier());
						}
					}
				}

				if (enemy != null) {
					enemy.setFlipped(false);

					for (Evogear e : enemy.getEquipments()) {
						JSONArray charms = e.getCharms();

						for (Object o : charms) {
							Charm c = Charm.valueOf(String.valueOf(o));
							if (c == Charm.THORNS) {
								thorns += c.getValue(e.getTier());
							}
						}
					}
				}

				switch (you.getOrigin().synergy()) {
					case SHIKIGAMI -> {
						List<SlotColumn> slts = arena.getSlots(op.getSide());
						for (SlotColumn slt : slts) {
							if (slt.hasTop()) {
								slt.getTop().getStats().setDodge(-1);
							}
						}
					}
					case FALLEN -> {
						if (op.getRegDeg().peek() < 0) {
							op.getRegDeg().apply(0.05);
						}
					}
					case SPAWN -> op.getRegDeg().leftShift(op.getBase().hp() * 0.05);
				}

				boolean ignore = ally.popFlag(Flag.NO_COMBAT);
				if (!ignore && enemy != null) {
					ignore = enemy.getSlot() == null || enemy.popFlag(Flag.IGNORE_COMBAT);
				}

				if (!ignore) {
					if (enemy != null) {
						if (enemy.isSupporting()) {
							for (Senshi s : enemy.getNearby()) {
								s.awake();
							}

							op.getGraveyard().add(enemy);

							dmg = 0;
							outcome = "str/combat_success";
						} else {
							boolean dbl = op.getOrigin().synergy() == Race.CYBERBEAST && Calc.chance(5);
							boolean unstop = ally.popFlag(Flag.UNSTOPPABLE);

							int enemyStats = enemy.getActiveAttr(dbl);
							int eEquipStats = enemy.getActiveEquips(dbl);
							int eCombatStats = enemyStats;
							if (ally.popFlag(Flag.IGNORE_EQUIP)) {
								eCombatStats -= eEquipStats;
							}

							if (!unstop && ally.getActiveAttr() < eCombatStats) {
								trigger(ON_SUICIDE, ally.asSource(ON_SUICIDE), enemy.asTarget(ON_BLOCK));
								pHP = you.getHP();

								if (!ally.popFlag(Flag.NO_DAMAGE)) {
									you.modHP((int) -((enemyStats - ally.getActiveAttr()) * dmgMult));
								}

								for (Senshi s : ally.getNearby()) {
									s.awake();
								}

								you.getGraveyard().add(ally);

								reportEvent("str/combat", ally, enemy, getLocale().get("str/combat_defeat", pHP - you.getHP()));
								return true;
							} else {
								int block = enemy.getBlock();
								int dodge = enemy.getDodge();

								if (Calc.chance(100 - ally.getHitChance())) {
									trigger(ON_MISS, ally.asSource(ON_MISS));

									reportEvent("str/combat", ally, enemy, getLocale().get("str/combat_miss"));
									return true;
								} else if (!unstop && !ally.popFlag(Flag.TRUE_STRIKE) && (enemy.popFlag(Flag.TRUE_BLOCK) || Calc.chance(block))) {
									trigger(ON_SUICIDE, ally.asSource(ON_SUICIDE), enemy.asTarget(ON_BLOCK));

									for (Senshi s : ally.getNearby()) {
										s.awake();
									}

									you.getGraveyard().add(ally);

									reportEvent("str/combat", ally, enemy, getLocale().get("str/combat_block", block));
									return true;
								} else if (!ally.popFlag(Flag.TRUE_STRIKE) && (enemy.popFlag(Flag.TRUE_DODGE) || Calc.chance(dodge))) {
									trigger(ON_MISS, ally.asSource(ON_MISS), enemy.asTarget(ON_DODGE));

									if (you.getOrigin().synergy() == Race.FABLED) {
										op.modHP((int) -(ally.getActiveAttr() * dmgMult * 0.02));
									}

									reportEvent("str/combat", ally, enemy, getLocale().get("str/combat_dodge", dodge));
									return true;
								}

								if (unstop || ally.getActiveAttr() > eCombatStats) {
									trigger(ON_HIT, ally.asSource(ON_HIT), enemy.asTarget(ON_LOSE));
									if (enemy.isDefending() || enemy.popFlag(Flag.NO_DAMAGE)) {
										dmg = 0;
									} else {
										dmg = Math.max(0, dmg - enemyStats);
									}

									for (Senshi s : enemy.getNearby()) {
										s.awake();
									}

									op.getGraveyard().add(enemy);

									outcome = "str/combat_success";
								} else {
									trigger(ON_CLASH, ally.asSource(ON_SUICIDE), enemy.asTarget(ON_LOSE));

									for (Senshi s : enemy.getNearby()) {
										s.awake();
									}

									op.getGraveyard().add(enemy);

									for (Senshi s : ally.getNearby()) {
										s.awake();
									}

									you.getGraveyard().add(ally);

									dmg = 0;
									outcome = "str/combat_clash";
								}
							}
						}
					} else {
						for (SlotColumn sc : getSlots(op.getSide())) {
							for (Senshi card : sc.getCards()) {
								if (card instanceof CardProxy) {
									EffectParameters params = new EffectParameters(
											Trigger.ON_TRAP, op.getSide(),
											card.asSource(ON_TRAP),
											ally.asTarget(ON_ATTACK, TargetType.ENEMY)
									);

									if (activateProxy(card, params)) {
										getChannel().sendMessage(getLocale().get("str/trap_activation", card)).queue();
									}
								}
							}
						}

						outcome = "str/combat_direct";
					}
				}

				op.modHP((int) -(dmg * dmgMult));
				if (thorns > 0) {
					you.modHP(-(pHP - op.getHP()) * thorns / 100);
				}
				if (lifesteal > 0) {
					you.modHP((pHP - op.getHP()) * lifesteal / 100);
				}
			}
		} finally {
			if (ally.getSlot() != null && !ally.popFlag(Flag.FREE_ACTION)) {
				ally.setAvailable(false);
			}
		}

		reportEvent("str/combat", ally, Utils.getOr(enemy, op.getName()), getLocale().get(outcome, pHP - op.getHP()));
		return false;
	}

	public ShoukanParams getParams() {
		return params;
	}

	public Map<Side, Hand> getHands() {
		return hands;
	}

	public Hand getCurrent() {
		return hands.get(getCurrentSide());
	}

	public Side getCurrentSide() {
		return getTurn() % 2 == 0 ? Side.TOP : Side.BOTTOM;
	}

	public Hand getOther() {
		return hands.get(getOtherSide());
	}

	public Side getOtherSide() {
		return getTurn() % 2 == 1 ? Side.TOP : Side.BOTTOM;
	}

	public Arena getArena() {
		return arena;
	}

	public List<Turn> getTurns() {
		return turns;
	}

	public boolean isSingleplayer() {
		return singleplayer;
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
				JSONArray deck = new JSONArray(IO.uncompress(p.deck()));
				for (Object o : deck) {
					JSONObject jo = new JSONObject(o);
					Class<Drawable<?>> klass = (Class<Drawable<?>>) Class.forName(jo.getString("KLASS"));

					h.getRealDeck().add(JSONUtils.fromJSON(jo.toString(), klass));
				}

				h.getGraveyard().clear();
				JSONArray graveyard = new JSONArray(IO.uncompress(p.graveyard()));
				for (Object o : graveyard) {
					JSONObject jo = new JSONObject(o);
					Class<Drawable<?>> klass = (Class<Drawable<?>>) Class.forName(jo.getString("KLASS"));

					h.getGraveyard().add(JSONUtils.fromJSON(jo.toString(), klass));
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

	public List<SlotColumn> getSlots(Side side) {
		return arena.getSlots(side);
	}

	public List<Evogear> getEquipments(Side side) {
		return arena.getSlots(side).stream()
				.map(SlotColumn::getTop)
				.filter(Objects::nonNull)
				.flatMap(s -> s.getEquipments().stream())
				.toList();
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

		List<Senshi> cards = getSlots(side).stream()
				.map(SlotColumn::getCards)
				.flatMap(List::stream)
				.filter(Objects::nonNull)
				.toList();

		for (Senshi s : cards) {
			s.execute(true, new EffectParameters(trigger, side, s.asSource(trigger)));
		}

		for (EffectHolder<?> leech : hands.get(side).getLeeches()) {
			leech.execute(new EffectParameters(ON_LEECH, side, leech.asSource(trigger)));
		}

		triggerEOTs(new EffectParameters(trigger, side));
	}

	public boolean trigger(Trigger trigger, Source source, Target... targets) {
		if (restoring) return false;

		EffectParameters ep = new EffectParameters(trigger, source.side(), source, targets);
		for (Target t : ep.targets()) {
			t.execute(ep);
		}

		try {
			return source.execute(ep);
		} finally {
			triggerEOTs(ep);
		}
	}

	public Set<EffectOverTime> getEOTs() {
		return eots;
	}

	public void triggerEOTs(EffectParameters ep) {
		Set<EffectOverTime> effects = new TreeSet<>(eots);
		for (EffectOverTime effect : effects) {
			if (effect.lock().get()) continue;

			boolean remove = false;
			Predicate<Side> checkSide = s -> effect.side() == null || effect.side() == s;
			if (checkSide.test(getCurrentSide()) && ep.trigger() == ON_TURN_BEGIN) {
				effect.decreaseTurn();
				remove = effect.expired() || effect.removed();
			}

			if (effect.triggers().contains(ep.trigger())) {
				if (ep.size() == 0) {
					if (checkSide.test(ep.side()) && effect.triggers().contains(ep.trigger())) {
						effect.decreaseLimit();

						try {
							effect.effect().accept(effect, new EffectParameters(ep.trigger(), ep.side()));
						} catch (ActivationException ignore) {
						}

						if (effect.side() == null) {
							effect.lock().set(true);
						}
					}
				} else if (ep.source() != null) {
					if (checkSide.test(ep.source().side()) && effect.triggers().contains(ep.source().trigger())) {
						effect.decreaseLimit();

						try {
							effect.effect().accept(effect, new EffectParameters(ep.source().trigger(), ep.side(), ep.source(), ep.targets()));
						} catch (ActivationException ignore) {
						}
					}

					for (Target t : ep.targets()) {
						if (checkSide.test(t.side()) && effect.triggers().contains(t.trigger())) {
							effect.decreaseLimit();

							try {
								effect.effect().accept(effect, new EffectParameters(t.trigger(), ep.side(), ep.source(), ep.targets()));
							} catch (ActivationException ignore) {
							}
						}
					}
				}

				remove = effect.expired() || effect.removed();
			}

			if (remove) {
				if (!effect.permanent()) {
					getChannel().sendMessage(getLocale().get("str/effect_expiration", effect.source())).queue();
				}

				eots.remove(effect);
			}
		}
	}

	private BiFunction<String, String, String> replaceMessages(Message message) {
		resetTimer();
		addButtons(message);

		return (chn, msg) -> {
			if (msg != null) {
				TextChannel channel = Main.getApp().getShiro().getTextChannelById(chn);
				if (channel != null) {
					channel.retrieveMessageById(msg)
							.flatMap(Objects::nonNull, Message::delete)
							.queue();
				}
			}

			return message.getId();
		};
	}

	private void reportEvent(String message, Object... args) {
		resetTimer();
		trigger(ON_TICK);

		List<Side> sides = List.of(getOtherSide(), getCurrentSide());
		for (Side side : sides) {
			Hand hand = hands.get(side);
			hand.getCards();
			hand.getRealDeck();
			hand.getGraveyard();

			String def = hand.getDefeat();
			if (hand.getHP() == 0 || def != null) {
				trigger(ON_VICTORY, side.getOther());
				trigger(ON_DEFEAT, side);

				if (hand.getDefeat() == null) {
					if (hand.getHP() > 0) continue;
					else if (hand.getOrigin().major() == Race.UNDEAD && hand.getOriginCooldown() == 0) {
						hand.setHP(1);
						hand.setDefeat(null);
						hand.getRegDeg().add(hand.getBase().hp() * 0.5, 1 / 3d);
						hand.setOriginCooldown(4);
						continue;
					}
				}

				restoring = true;
				for (List<SlotColumn> slts : arena.getSlots().values()) {
					for (SlotColumn slt : slts) {
						for (Senshi card : slt.getCards()) {
							if (card != null) {
								card.setFlipped(false);
							}
						}
					}
				}
				restoring = false;

				if (def != null) {
					reportResult(GameReport.SUCCESS, "str/game_end_special", def, "<@" + hands.get(side.getOther()).getUid() + ">");
				} else {
					reportResult(GameReport.SUCCESS, "str/game_end", "<@" + hand.getUid() + ">", "<@" + hands.get(side.getOther()).getUid() + ">");
				}

				return;
			}

			List<SlotColumn> slts = getSlots(side);
			for (SlotColumn slt : slts) {
				Senshi s = slt.getTop();
				if (s != null) {
					s.setLastInteraction(null);
					s.getStats().removeExpired(AttrMod::isExpired);
					for (Evogear e : s.getEquipments()) {
						e.getStats().removeExpired(AttrMod::isExpired);
					}
				}

				s = slt.getBottom();
				if (s != null) {
					s.setLastInteraction(null);
					s.getStats().removeExpired(AttrMod::isExpired);
				}

			}
		}

		BufferedImage img = history ? arena.render(getLocale(), getHistory()) : arena.render(getLocale());
		byte[] bytes = IO.getBytes(img, "png");

		AtomicBoolean registered = new AtomicBoolean();
		getChannel().sendMessage(getLocale().get(message, args))
				.addFile(bytes, "game.png")
				.queue(m -> {
					messages.compute(m.getTextChannel().getId(), replaceMessages(m));

					if (!registered.get()) {
						if (!message.startsWith("str/game_history")) {
							getHistory().add(new HistoryLog(m.getContentDisplay(), getCurrentSide()));
						}

						registered.set(true);
					}
				});
	}

	private void reportResult(@MagicConstant(valuesFromClass = GameReport.class) byte code, String message, Object... args) {
		if (isClosed()) return;

		BufferedImage img = history ? arena.render(getLocale(), getHistory()) : arena.render(getLocale());
		byte[] bytes = IO.getBytes(img, "png");

		AtomicBoolean registered = new AtomicBoolean();
		getChannel().sendMessage(getLocale().get(message, args))
				.addFile(bytes, "game.png")
				.queue(m -> {
					if (!registered.get()) {
						getHistory().add(new HistoryLog(m.getContentDisplay(), getCurrentSide()));
						registered.set(true);
					}
				});

		for (Map.Entry<String, String> tuple : messages.entrySet()) {
			if (tuple != null) {
				TextChannel channel = Main.getApp().getShiro().getTextChannelById(tuple.getKey());
				if (channel != null) {
					channel.retrieveMessageById(tuple.getValue())
							.flatMap(Objects::nonNull, Message::delete)
							.queue();
				}
			}
		}

		if (!singleplayer) {
			new MatchHistory(new Match(this)).save();
		}

		close(code);
	}

	private void addButtons(Message msg) {
		Hand curr = getCurrent();
		Map<Emoji, ThrowingConsumer<ButtonWrapper>> buttons = new LinkedHashMap<>();
		buttons.put(Utils.parseEmoji("▶"), w -> {
			if (curr.selectionPending()) {
				reportEvent("error/pending_choice");
				return;
			} else if (getPhase() == Phase.COMBAT || getTurn() == 1) {
				if (curr.getLockTime(Lock.TAUNT) > 0) {
					List<SlotColumn> yours = getSlots(curr.getSide());
					if (yours.stream().anyMatch(sc -> sc.getTop() != null && sc.getTop().canAttack())) {
						reportEvent("error/taunt_locked", curr.getLockTime(Lock.TAUNT));
						return;
					}
				}

				nextTurn();
				return;
			}

			setPhase(Phase.COMBAT);
			reportEvent("str/game_combat_phase");
		});

		if (getPhase() == Phase.PLAN) {
			buttons.put(Utils.parseEmoji("⏩"), w -> {
				if (curr.getLockTime(Lock.TAUNT) > 0) {
					List<SlotColumn> yours = getSlots(curr.getSide());
					if (yours.stream().anyMatch(sc -> sc.getTop() != null && sc.getTop().canAttack())) {
						reportEvent("error/taunt_locked", curr.getLockTime(Lock.TAUNT));
						return;
					}
				} else if (curr.selectionPending()) {
					reportEvent("error/pending_choice");
					return;
				}

				nextTurn();
			});

			if (!curr.getRealDeck().isEmpty()) {
				int rem = curr.getRemainingDraws();
				if (rem > 0) {
					buttons.put(Utils.parseEmoji("📤"), w -> {
						if (curr.selectionPending()) {
							reportEvent("error/pending_choice");
							return;
						}

						curr.manualDraw(1);
						curr.showHand();
						reportEvent("str/draw_card", curr.getName(), 1, "");
					});

					if (rem > 1) {
						buttons.put(Utils.parseEmoji("📦"), w -> {
							if (curr.selectionPending()) {
								reportEvent("error/pending_choice");
								return;
							}

							curr.manualDraw(curr.getRemainingDraws());
							curr.showHand();
							reportEvent("str/draw_card", curr.getName(), rem, "s");
						});
					}
				}

				if (curr.isCritical() && !curr.hasUsedDestiny()) {
					buttons.put(Utils.parseEmoji("\uD83E\uDDE7"), w -> {
						if (curr.selectionPending()) {
							reportEvent("error/pending_choice");
							return;
						}

						BondedList<Drawable<?>> deque = curr.getRealDeck();
						List<Drawable<?>> cards = new ArrayList<>();
						cards.add(deque.getFirst());
						if (deque.size() > 2) cards.add(deque.get((deque.size() - 1) / 2));
						if (deque.size() > 1) cards.add(deque.getLast());

						reportEvent("str/destiny_draw", curr.getName());
						curr.requestChoice(cards).thenAccept(d -> {
							curr.getCards().add(d);
							deque.remove(d);
							curr.setUsedDestiny(true);

							curr.showHand();
						});
					});
				}
			}

			if (curr.getOrigin().major() == Race.SPIRIT && curr.getDiscard().size() >= 3 && curr.getOriginCooldown() == 0) {
				buttons.put(Utils.parseEmoji("\uD83C\uDF00"), w -> {
					if (curr.selectionPending()) {
						reportEvent("error/pending_choice");
						return;
					}

					List<StashedCard> cards = new ArrayList<>();

					int i = 3;
					Iterator<Drawable<?>> it = curr.getDiscard().iterator();
					while (it.hasNext() && i-- > 0) {
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
						curr.getCards().remove(d);
					}

					curr.getCards().add(SynthesizeCommand.rollSynthesis(cards));
					curr.setOriginCooldown(3);
					curr.showHand();
					reportEvent("str/spirit_synth", curr.getName());
				});
			}

			buttons.put(Utils.parseEmoji("\uD83D\uDCD1"), w -> {
				history = !history;

				if (history) {
					reportEvent("str/game_history_enable", curr.getName());
				} else {
					reportEvent("str/game_history_disable", curr.getName());
				}
			});

			buttons.put(Utils.parseEmoji("\uD83E\uDEAA"), w -> {
				if (curr.selectionPending()) {
					w.getHook().setEphemeral(true)
							.sendFile(IO.getBytes(curr.renderChoices(), "png"), "choices.png")
							.queue();
					return;
				}

				w.getHook().setEphemeral(true)
						.sendFile(IO.getBytes(curr.render(), "png"), "hand.png")
						.queue();
			});

			buttons.put(Utils.parseEmoji("\uD83D\uDD0D"),
					w -> w.getHook().setEphemeral(true)
							.sendFile(IO.getBytes(arena.renderEvogears(), "png"), "evogears.png")
							.queue()
			);

			buttons.put(Utils.parseEmoji("🏳"), w -> {
				if (curr.isForfeit()) {
					reportResult(GameReport.SUCCESS, "str/game_forfeit", "<@" + getCurrent().getUid() + ">");
					return;
				}

				curr.setForfeit(true);
				w.getHook().setEphemeral(true)
						.sendMessage(getLocale().get("str/confirm_forfeit"))
						.queue();
			});
		}

		Pages.buttonize(msg, buttons, true, false, u -> u.getId().equals(curr.getUid()));
	}

	public List<SlotColumn> getOpenSlots(Side side, boolean top) {
		return getSlots(side).stream()
				.filter(sc -> !sc.isLocked() && !(top ? sc.hasTop() : sc.hasBottom()))
				.toList();
	}

	public boolean putAtOpenSlot(Side side, boolean top, Senshi card) {
		List<SlotColumn> slts = getOpenSlots(side, top);
		if (slts.isEmpty()) return false;

		if (top) {
			slts.get(0).setTop(card);
		} else {
			slts.get(0).setBottom(card);
		}

		return true;
	}

	public boolean putAtOpenSlot(Side side, Senshi card) {
		return putAtOpenSlot(side, true, card) || putAtOpenSlot(side, false, card);
	}

	public String getString(String key, Object... params) {
		if (key == null) return "";

		LocalizedString str = DAO.find(LocalizedString.class, new LocalizedId(key.toLowerCase(), getLocale()));
		if (str != null) {
			return str.getValue().formatted(params);
		} else {
			return "";
		}
	}

	public void send(Drawable<?> source, String text) {
		send(source, text, null);
	}

	public void send(Drawable<?> source, String text, String gif) {
		for (TextChannel chn : getChannel().getChannels()) {
			PseudoUser pu = new PseudoUser(source.toString(), Constants.API_ROOT + "card/" + source.getCard().getId(), chn);

			try (WebhookClient hook = pu.webhook()) {
				if (hook == null) continue;

				WebhookMessageBuilder msg = new WebhookMessageBuilder()
						.setUsername(pu.name())
						.setAvatarUrl(pu.avatar())
						.setAllowedMentions(AllowedMentions.none())
						.setContent(text);

				if (gif != null) {
					msg.addEmbeds(new WebhookEmbedBuilder()
							.setImageUrl(GIF_PATH + gif + ".gif")
							.build());
				}

				hook.send(msg.build());
			}
		}
	}

	@Override
	public void nextTurn() {
		turns.add(Turn.from(this));

		Hand curr = getCurrent();
		trigger(ON_TURN_END, curr.getSide());
		curr.flushDiscard();

		if (curr.getOrigin().synergy() == Race.ANGEL) {
			curr.modHP(curr.getMP() * 10);
		}

		for (Lock lock : Lock.values()) {
			curr.modLockTime(lock, -1);
		}

		for (SlotColumn slt : getSlots(curr.getSide())) {
			for (Senshi s : slt.getCards()) {
				if (s != null) {
					s.reduceSleep(1);
					s.reduceStun(1);
					s.reduceCooldown(1);
					s.reduceTaunt(1);
					s.setAvailable(true);
					s.setSwitched(false);

					s.getStats().clearTFlags();
					for (Evogear e : s.getEquipments()) {
						e.getStats().clearTFlags();
					}
				}
			}
		}

		super.nextTurn();
		setPhase(Phase.PLAN);
		curr = getCurrent();
		curr.modMP(curr.getBase().mpGain().apply(getTurn() - (curr.getSide() == Side.TOP ? 1 : 0)));
		curr.applyVoTs();
		curr.reduceOriginCooldown(1);

		if (curr.getLockTime(Lock.BLIND) > 0) {
			Collections.shuffle(curr.getCards());
		}

		for (SlotColumn slt : getSlots(curr.getSide())) {
			for (Senshi s : slt.getCards()) {
				if (s != null) {
					s.reduceStasis(1);

					s.getStats().expireMods();
					for (Evogear e : s.getEquipments()) {
						e.getStats().expireMods();
					}
				}
			}
		}

		trigger(ON_TURN_BEGIN, curr.getSide());
		curr.showHand();
		reportEvent("str/game_turn_change", "<@" + curr.getUid() + ">", (int) Math.ceil(getTurn() / 2d));

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