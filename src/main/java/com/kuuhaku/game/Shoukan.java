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
import com.kuuhaku.model.enums.Role;
import com.kuuhaku.model.enums.shoukan.*;
import com.kuuhaku.model.persistent.shoukan.*;
import com.kuuhaku.model.persistent.user.Account;
import com.kuuhaku.model.persistent.user.StashedCard;
import com.kuuhaku.model.records.PseudoUser;
import com.kuuhaku.model.records.shoukan.*;
import com.kuuhaku.model.records.shoukan.history.Match;
import com.kuuhaku.model.records.shoukan.history.Turn;
import com.kuuhaku.model.records.shoukan.snapshot.Player;
import com.kuuhaku.model.records.shoukan.snapshot.Slot;
import com.kuuhaku.model.records.shoukan.snapshot.StateSnap;
import com.kuuhaku.util.Bit;
import com.kuuhaku.util.Calc;
import com.kuuhaku.util.IO;
import com.kuuhaku.util.Utils;
import com.kuuhaku.util.json.JSONArray;
import com.kuuhaku.util.json.JSONObject;
import com.kuuhaku.util.json.JSONUtils;
import kotlin.Pair;
import kotlin.Triple;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.FileUpload;
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
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.kuuhaku.model.enums.shoukan.Trigger.*;

public class Shoukan extends GameInstance<Phase> {
	private final long seed = ThreadLocalRandom.current().nextLong();
	private final String GIF_PATH = "https://raw.githubusercontent.com/OtagamerZ/ShoukanAssets/master/gifs/";

	private final Arcade arcade;
	private final Arena arena;
	private final Map<Side, Hand> hands;
	private final Map<String, String> messages = new HashMap<>();
	private final Set<EffectOverTime> eots = new HashSet<>();
	private final List<Turn> turns = new TreeList<>();

	private final boolean singleplayer;
	private boolean cheats = false;
	private StateSnap snapshot = null;
	private boolean restoring = true;
	private boolean history = false;
	private boolean lock = false;
	private Side winner;

	public Shoukan(I18N locale, Arcade arcade, User p1, User p2) {
		this(locale, arcade, p1.getId(), p2.getId());
	}

	public Shoukan(I18N locale, Arcade arcade, String p1, String p2) {
		super(locale, new String[]{p1, p2});

		this.arcade = arcade;
		this.arena = new Arena(this);
		this.hands = Map.of(
				Side.TOP, new Hand(p1, this, Side.TOP),
				Side.BOTTOM, new Hand(p2, this, Side.BOTTOM)
		);
		this.singleplayer = p1.equals(p2);

		setTimeout(turn -> reportResult(GameReport.GAME_TIMEOUT, getOther().getSide(), "str/game_wo", "<@" + getOther().getUid() + ">"), 5, TimeUnit.MINUTES);
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
			h.manualDraw(h.getRemainingDraws());
		}

		setPhase(Phase.PLAN);

		Hand curr = getCurrent();
		curr.modMP(curr.getBase().mpGain().get());

		curr.showHand();
		reportEvent("str/game_start", false, "<@" + curr.getUid() + ">");

		takeSnapshot();
	}

	@Override
	protected void runtime(User user, String value) throws InvocationTargetException, IllegalAccessException {
		Pair<Method, JSONObject> action = toAction(value.toLowerCase().replace(" ", ""));
		if (action != null) {
			Method m = action.getFirst();

			for (Hand h : hands.values()) {
				if (h.getUid().equals(user.getId()) && h.selectionPending()) {
					if (!m.getName().equals("select") && !m.getName().startsWith("deb")) {
						getChannel().sendMessage(getLocale().get("error/pending_choice")).queue();
						return;
					}

					if (m.getName().startsWith("deb")) {
						cheats = true;
					}

					m.invoke(this, h.getSide(), action.getSecond());
					return;
				}
			}

			if (!lock) {
				lock = true;

				try {
					if (m.getName().startsWith("deb")) {
						cheats = true;
					}

					if ((boolean) m.invoke(this, getCurrentSide(), action.getSecond())) {
						getCurrent().showHand();
					}
				} catch (Exception e) {
					Constants.LOGGER.error("Failed to execute method " + m.getName(), e);
				} finally {
					lock = false;
				}
			}
		}
	}

	@PlayerAction("reload")
	private boolean reload(Side side, JSONObject args) {
		reportEvent("str/game_reload", false, getCurrent().getName());
		return true;
	}

	// DEBUG START

	@PhaseConstraint({"PLAN", "COMBAT"})
	@PlayerAction("set_hp,(?<value>\\d+)")
	private boolean debSetHp(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (DAO.find(Account.class, curr.getUid()).hasRole(Role.TESTER)) {
			int val = args.getInt("value");
			curr.setHP(val);

			reportEvent("SET_HP -> " + val, false);
		}

		return false;
	}

	@PhaseConstraint({"PLAN", "COMBAT"})
	@PlayerAction("set_mp,(?<value>\\d+)")
	private boolean debSetMp(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (DAO.find(Account.class, curr.getUid()).hasRole(Role.TESTER)) {
			int val = args.getInt("value");
			curr.setMP(val);

			reportEvent("SET_MP -> " + val, false);
		}

		return false;
	}

	@PhaseConstraint({"PLAN", "COMBAT"})
	@PlayerAction("set_origin,(?<major>\\w+)(?:,(?<minor>[\\w,]+))?")
	private boolean debSetOrigin(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (DAO.find(Account.class, curr.getUid()).hasRole(Role.TESTER)) {
			Race major = args.getEnum(Race.class, "major", Race.NONE);

			Set<Race> minors = new HashSet<>();
			JSONArray races = new JSONArray(Arrays.asList(args.getString("minor").split(",")));
			for (int i = 0; i < races.size(); i++) {
				Race minor = races.getEnum(Race.class, i);
				if (minor != null) {
					minors.add(minor);
				}
			}

			curr.setOrigin(new Origin(major, minors.toArray(Race[]::new)));
			reportEvent("SET_ORIGIN -> " + curr.getOrigin(), false);
		}

		return false;
	}

	@PhaseConstraint({"PLAN", "COMBAT"})
	@PlayerAction("add,(?<card>[\\w-]+)(?:,(?<amount>\\d+))?")
	private boolean debAddCard(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (DAO.find(Account.class, curr.getUid()).hasRole(Role.TESTER)) {
			String id = args.getString("card").toUpperCase();
			CardType type = Bit.toEnumSet(CardType.class, DAO.queryNative(Integer.class, "SELECT get_type(?1)", id)).stream()
					.findFirst()
					.orElse(CardType.NONE);

			boolean add = false;
			int amount = args.getInt("amount", 1);
			for (int i = 0; i < amount; i++) {
				Drawable<?> d = switch (type) {
					case NONE -> null;
					case KAWAIPON -> DAO.find(Senshi.class, id);
					case EVOGEAR -> DAO.find(Evogear.class, id);
					case FIELD -> DAO.find(Field.class, id);
				};

				if (d != null) {
					add = true;
					curr.getCards().add(d.copy());
				}
			}

			if (add) {
				reportEvent("ADD_CARD -> " + amount + " x " + id, false);
				return true;
			}
		}

		return false;
	}

	@PhaseConstraint({"PLAN", "COMBAT"})
	@PlayerAction("terminate")
	private boolean debTerminate(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (DAO.find(Account.class, curr.getUid()).hasRole(Role.TESTER)) {
			reportResult(GameReport.SUCCESS, null, "GAME_TERMINATE");
			return false;
		}

		return false;
	}

	// DEBUG END

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

			chosen.setAvailable(curr.getOrigin().synergy() == Race.HERALD && Calc.chance(5));
			slot.setBottom(copy = chosen.withCopy(s -> {
				switch (args.getString("mode")) {
					case "d" -> s.setDefending(true);
					case "b" -> s.setFlipped(true);
				}

				curr.consumeHP(s.getHPCost());
				curr.consumeMP(s.getMPCost());
				List<Drawable<?>> consumed = curr.consumeSC(s.getSCCost());
				if (!consumed.isEmpty()) {
					s.getStats().getData().put("consumed", consumed);
				}
			}));
		} else {
			if (slot.hasTop()) {
				getChannel().sendMessage(getLocale().get("error/slot_occupied")).queue();
				return false;
			}

			chosen.setAvailable(curr.getOrigin().synergy() == Race.HERALD && Calc.chance(5));
			slot.setTop(copy = chosen.withCopy(s -> {
				switch (args.getString("mode")) {
					case "d" -> s.setDefending(true);
					case "b" -> s.setFlipped(true);
				}

				curr.consumeHP(s.getHPCost());
				curr.consumeMP(s.getMPCost());
				List<Drawable<?>> consumed = curr.consumeSC(s.getSCCost());
				if (!consumed.isEmpty()) {
					s.getStats().getData().put("consumed", consumed);
				}
			}));
		}

		curr.getData().put("last_summon", copy);
		reportEvent("str/place_card", true,
				curr.getName(),
				copy.isFlipped() ? getLocale().get("str/a_card") : copy,
				copy.getState().toString(getLocale())
		);
		return true;
	}

	private boolean placeProxy(Hand hand, JSONObject args) {
		if (hand.getCards().get(args.getInt("inHand") - 1) instanceof Evogear chosen && chosen.isSpell()) {
			if (!chosen.isAvailable() || chosen.isPassive()) {
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

		TrapSpell proxy = new TrapSpell(chosen);
		Evogear copy = proxy.getOriginal();

		if (args.getBoolean("notCombat")) {
			if (slot.hasBottom()) {
				getChannel().sendMessage(getLocale().get("error/slot_occupied")).queue();
				return false;
			}

			hand.consumeHP(copy.getHPCost());
			hand.consumeMP(copy.getMPCost());
			List<Drawable<?>> consumed = hand.consumeSC(copy.getSCCost());
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

			hand.consumeHP(copy.getHPCost());
			hand.consumeMP(copy.getMPCost());
			List<Drawable<?>> consumed = hand.consumeSC(copy.getSCCost());
			if (!consumed.isEmpty()) {
				proxy.getStats().getData().put("consumed", consumed);
			}

			chosen.setAvailable(false);
			slot.setTop(proxy);
		}

		reportEvent("str/place_card", true,
				hand.getName(),
				proxy.isFlipped() ? getLocale().get("str/a_card") : proxy,
				proxy.getState().toString(getLocale())
		);
		return true;
	}

	public boolean activateProxy(Senshi proxy, EffectParameters ep) {
		if (!(proxy instanceof TrapSpell p)) return false;

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
		if (enemy != null && enemy.isProtected(e)) {
			hand.getGraveyard().add(p);
			hand.getData().put("last_spell", e);
			trigger(ON_SPELL, hand.getSide());
			getChannel().sendMessage(getLocale().get("str/str/avoid_effect")).queue();
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

		Evogear copy = chosen.withCopy(e -> {
			if (curr.isEmpowered() && curr.getOrigin().major() == Race.MACHINE) {
				e.getStats().setFlag(Flag.EMPOWERED, true, true);
				curr.setEmpowered(false);
			}
		});

		Senshi target = slot.getTop();
		curr.consumeHP(copy.getHPCost());
		curr.consumeMP(copy.getMPCost());
		List<Drawable<?>> consumed = curr.consumeSC(copy.getSCCost());
		if (!consumed.isEmpty()) {
			copy.getStats().getData().put("consumed", consumed);
		}

		chosen.setAvailable(false);
		target.getEquipments().add(copy);
		curr.getData().put("last_equipment", copy);
		reportEvent("str/equip_card", true,
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
		reportEvent("str/place_field", true, curr.getName(), chosen);
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
		reportEvent("str/flip_card", true, curr.getName(), chosen, chosen.getState().toString(getLocale()));
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

		reportEvent("str/promote_card", true,
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

		reportEvent("str/sacrifice_card", true, curr.getName(), chosen);
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

		reportEvent("str/sacrifice_card", true, curr.getName(),
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

				if (!available.isEmpty()) {
					Utils.getRandomEntry(available).getStats().setMana(-1);
				}
			}
		}

		reportEvent("str/discard_card", true, curr.getName(), chosen);
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

		reportEvent("str/discard_card", true, curr.getName(),
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
			if (!chosen.isAvailable() || chosen.isPassive()) {
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
		if (enemy != null && enemy.isProtected(chosen)) {
			curr.consumeHP(chosen.getHPCost());
			curr.consumeMP(chosen.getMPCost());
			List<Drawable<?>> consumed = curr.consumeSC(chosen.getSCCost());
			if (!consumed.isEmpty()) {
				chosen.getStats().getData().put("consumed", consumed);
			}

			if (!chosen.getStats().popFlag(Flag.FREE_ACTION)) {
				chosen.setAvailable(false);
				stack.add(chosen.copy());
			}

			curr.getData().put("last_spell", chosen);
			trigger(ON_SPELL, side);
			reportEvent("str/avoid_effect", true);
			return false;
		} else if (!tgt.validate(chosen.getTargetType())) {
			getChannel().sendMessage(getLocale().get("error/target", getLocale().get("str/target_" + chosen.getTargetType()))).queue();
			return false;
		}

		if (curr.isEmpowered() && curr.getOrigin().major() == Race.MYSTICAL) {
			chosen.getStats().setFlag(Flag.EMPOWERED, true, true);
			curr.setEmpowered(false);
		}

		curr.consumeHP(chosen.getHPCost());
		curr.consumeMP(chosen.getMPCost());
		List<Drawable<?>> consumed = curr.consumeSC(chosen.getSCCost());
		if (!consumed.isEmpty()) {
			chosen.getStats().getData().put("consumed", consumed);
		}

		if (!chosen.execute(chosen.toParameters(tgt))) {
			if (!chosen.isAvailable()) {
				chosen.setAvailable(false);
				reportEvent("str/effect_interrupted", true, chosen);
				return true;
			}

			return false;
		}

		if (!chosen.getStats().popFlag(Flag.FREE_ACTION)) {
			chosen.setAvailable(false);
			stack.add(chosen.copy());
		}

		curr.getData().put("last_spell", chosen);
		trigger(ON_SPELL, side);
		reportEvent("str/activate_card", true,
				curr.getName(),
				chosen.getTags().contains("tag/secret") ? getLocale().get("str/a_spell") : chosen
		);
		return true;
	}

	@PhaseConstraint({"PLAN", "COMBAT"})
	@PlayerAction("(?<choice>\\d+)")
	private boolean select(Side side, JSONObject args) {
		Hand curr = hands.get(side);
		if (!curr.selectionPending()) return false;

		Triple<List<Drawable<?>>, Boolean, CompletableFuture<Drawable<?>>> selection = curr.getSelection();
		if (!Utils.between(args.getInt("choice"), 0, selection.getFirst().size())) {
			getChannel().sendMessage(getLocale().get("error/invalid_selection_index")).queue();
			return false;
		}

		int idx = args.getInt("choice") - 1;
		if (idx == -1) {
			selection.getThird().complete(null);
			return true;
		}

		Drawable<?> chosen = selection.getFirst().get(idx);
		selection.getThird().complete(chosen);
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

			if (enemy.isProtected(chosen)) {
				curr.consumeMP(1);
				if (!chosen.popFlag(Flag.FREE_ACTION)) {
					chosen.setAvailable(false);
				}

				curr.getData().put("last_ability", chosen);
				trigger(ON_ABILITY, side);
				reportEvent("str/avoid_effect", true);
				return false;
			}
		}

		if (!tgt.validate(type)) {
			getChannel().sendMessage(getLocale().get("error/target", getLocale().get("str/target_" + type))).queue();
			return false;
		} else if (!trigger(ON_ACTIVATE, chosen.asSource(ON_ACTIVATE), tgt.targets(ON_EFFECT_TARGET))) {
			if (!chosen.isAvailable()) {
				reportEvent("str/effect_interrupted", true, chosen);
				return true;
			}

			return false;
		}

		curr.consumeMP(1);
		if (getPhase() != Phase.PLAN && !chosen.popFlag(Flag.FREE_ACTION)) {
			chosen.setAvailable(false);
		}

		curr.getData().put("last_ability", chosen);
		trigger(ON_ABILITY, side);
		reportEvent("str/card_special", true, curr.getName(), chosen);
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
		attack(ally, you, null, true);

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
		Hand op = you.getOther();

		Senshi enemy = null;
		if (args.has("target")) {
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
		}

		if (enemy == null) {
			attack(ally, op, null, true);
		} else {
			attack(ally, enemy, null, true);
		}

		return false;
	}

	public boolean attack(Senshi source, Senshi target) {
		return attack(source, target, null, false);
	}

	public boolean attack(Senshi source, Senshi target, int dmg) {
		return attack(source, target, dmg, false);
	}

	private boolean attack(Senshi source, Senshi target, Integer dmg, boolean announce) {
		if (target == null) return false;
		else if (source == null || ((announce && !source.canAttack()) || !source.isAvailable())) {
			if (announce) {
				getChannel().sendMessage(getLocale().get("error/card_cannot_attack")).queue();
			}

			return false;
		}

		Hand you = source.getHand();
		Hand op = target.getHand();
		int pHP = you.getHP();
		int eHP = op.getHP();

		if (source.getTarget() != null && !Objects.equals(source.getTarget(), target)) {
			if (announce) {
				getChannel().sendMessage(getLocale().get("error/card_taunted", source.getTarget(), source.getTarget().getIndex() + 1)).queue();
			}

			return false;
		}

		if (target.isStasis()) {
			if (announce) {
				getChannel().sendMessage(getLocale().get("error/card_untargetable")).queue();
			}

			return false;
		}

		Target t = target.asTarget(ON_DEFEND);
		int posHash = target.posHash();
		trigger(ON_ATTACK, source.asSource(ON_ATTACK), t);

		if (dmg == null) {
			dmg = source.getActiveAttr();
		}

		int lifesteal = you.getBase().lifesteal();
		int thorns = 0;
		double dmgMult = 1d / (1 << op.getChainReduction());
		if (getTurn() < 3 || you.getLockTime(Lock.TAUNT) > 0) {
			dmgMult /= 2;
		}

		boolean win = false;
		String outcome = getLocale().get("str/combat_skip");
		try {
			if (posHash == target.posHash() && ((announce && source.canAttack()) || source.isAvailable())) {
				for (Evogear e : source.getEquipments()) {
					JSONArray charms = e.getCharms();

					for (Object o : charms) {
						Charm c = Charm.valueOf(String.valueOf(o));
						switch (c) {
							case PIERCING -> op.modHP((int) -(dmg * dmgMult * c.getValue(e.getTier()) / 100));
							case WOUNDING -> {
								int val = (int) -(dmg * dmgMult * c.getValue(e.getTier()) / 100);
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

				target.setFlipped(false);

				for (Evogear e : target.getEquipments()) {
					JSONArray charms = e.getCharms();

					for (Object o : charms) {
						Charm c = Charm.valueOf(String.valueOf(o));
						if (c == Charm.THORNS) {
							thorns += c.getValue(e.getTier());
						}
					}
				}

				switch (you.getOrigin().synergy()) {
					case SHIKIGAMI -> {
						List<SlotColumn> slts = arena.getSlots(op.getSide());
						for (SlotColumn slt : slts) {
							if (slt.hasTop()) {
								slt.getTop().getStats().setDodge(-2);
							}
						}
					}
					case FALLEN -> {
						if (op.getRegDeg().peek() < 0) {
							op.getRegDeg().apply(0.05);
						}
					}
					case SPAWN -> op.getRegDeg().leftShift(-op.getBase().hp() * 0.05);
				}

				boolean ignore = source.popFlag(Flag.NO_COMBAT);
				if (!ignore) {
					ignore = target.getSlot().getIndex() == -1 || target.popFlag(Flag.IGNORE_COMBAT);
				}

				if (!ignore) {
					if (target.isSupporting()) {
						outcome = getLocale().get("str/combat_success", dmg, 0);

						for (Senshi s : target.getNearby()) {
							s.awake();
						}

						op.getGraveyard().add(target);

						dmg = 0;
						win = true;
					} else {
						boolean dbl = op.getOrigin().synergy() == Race.CYBERBEAST && Calc.chance(5);
						boolean unstop = source.popFlag(Flag.UNSTOPPABLE);

						int enemyStats = target.getActiveAttr(dbl);
						int eEquipStats = target.getActiveEquips(dbl);
						int eCombatStats = enemyStats;
						if (source.popFlag(Flag.IGNORE_EQUIP)) {
							eCombatStats -= eEquipStats;
						}

						if (!unstop && dmg < eCombatStats) {
							outcome = getLocale().get("str/combat_defeat", dmg, eCombatStats);
							trigger(ON_SUICIDE, source.asSource(ON_SUICIDE), target.asTarget(ON_BLOCK));

							for (Senshi s : source.getNearby()) {
								s.awake();
							}

							if (announce) {
								if (!source.popFlag(Flag.NO_DAMAGE)) {
									you.modHP((int) -((enemyStats - dmg) * dmgMult));
								}

								you.getGraveyard().add(source);
							}

							dmg = 0;
						} else {
							int block = target.getBlock();
							int dodge = target.getDodge();

							if (Calc.chance(100 - source.getHitChance())) {
								outcome = getLocale().get("str/combat_miss");
								trigger(ON_MISS, source.asSource(ON_MISS));

								dmg = 0;
							} else if (!unstop && !source.popFlag(Flag.TRUE_STRIKE) && (target.popFlag(Flag.TRUE_BLOCK) || Calc.chance(block))) {
								outcome = getLocale().get("str/combat_block", block);
								trigger(null, source.asSource(), target.asTarget(ON_BLOCK));

								source.setStun(1);

								dmg = 0;
							} else if (!source.popFlag(Flag.TRUE_STRIKE) && (target.popFlag(Flag.TRUE_DODGE) || Calc.chance(dodge))) {
								outcome = getLocale().get("str/combat_dodge", dodge);
								trigger(ON_MISS, source.asSource(ON_MISS), target.asTarget(ON_DODGE));

								if (you.getOrigin().synergy() == Race.FABLED) {
									op.modHP((int) -(dmg * dmgMult * 0.02));
								}

								dmg = 0;
							} else {
								if (unstop || dmg > eCombatStats) {
									outcome = getLocale().get("str/combat_success", dmg, eCombatStats);
									trigger(ON_HIT, source.asSource(ON_HIT), target.asTarget(ON_LOSE));

									if (target.isDefending() || target.popFlag(Flag.NO_DAMAGE)) {
										dmg = 0;
									} else {
										dmg = Math.max(0, dmg - enemyStats);
									}

									for (Senshi s : target.getNearby()) {
										s.awake();
									}

									op.getGraveyard().add(target);

									win = true;
								} else {
									outcome = getLocale().get("str/combat_clash", dmg, eCombatStats);
									trigger(ON_CLASH, source.asSource(ON_SUICIDE), target.asTarget(ON_LOSE));

									for (Senshi s : target.getNearby()) {
										s.awake();
									}

									op.getGraveyard().add(target);

									for (Senshi s : source.getNearby()) {
										s.awake();
									}

									if (announce) {
										you.getGraveyard().add(source);
									}

									dmg = 0;
								}
							}
						}
					}
				} else {
					dmg = 0;
				}

				op.modHP((int) -(dmg * dmgMult));
				op.addChain();

				if (thorns > 0) {
					you.modHP(-Math.max(0, eHP - op.getHP()) * thorns / 100);
				}
				if (lifesteal > 0) {
					you.modHP(Math.max(0, eHP - op.getHP()) * lifesteal / 100);
				}
			}
		} finally {
			if (announce && source.getSlot().getIndex() != -1 && !source.popFlag(Flag.FREE_ACTION)) {
				source.setAvailable(false);
			}
		}

		if (eHP != op.getHP()) {
			int val = eHP - op.getHP();
			outcome += "\n" + getLocale().get(val > 0 ? "str/combat_damage_dealt" : "str/combat_heal_op", Math.abs(val));
		}
		if (pHP != you.getHP()) {
			int val = pHP - you.getHP();
			outcome += "\n" + getLocale().get(val > 0 ? "str/combat_damage_taken" : "str/combat_heal_self", Math.abs(val));
		}

		if (announce) {
			reportEvent("str/combat", true, source, Utils.getOr(target, op.getName()), outcome.trim());
		}

		return win;
	}

	public boolean attack(Senshi source, Hand op) {
		return attack(source, op, null, false);
	}

	public boolean attack(Senshi source, Hand op, int dmg) {
		return attack(source, op, dmg, false);
	}

	private boolean attack(Senshi source, Hand op, Integer dmg, boolean announce) {
		if (source == null || op == null || ((announce && !source.canAttack()) || !source.isAvailable())) {
			if (announce) {
				getChannel().sendMessage(getLocale().get("error/card_cannot_attack")).queue();
			}

			return false;
		}

		Hand you = source.getHand();
		int pHP = you.getHP();
		int eHP = op.getHP();

		if (!arena.isFieldEmpty(op.getSide()) && !source.popFlag(Flag.DIRECT)) {
			if (announce) {
				getChannel().sendMessage(getLocale().get("error/field_not_empty")).queue();
			}

			return false;
		}

		trigger(ON_ATTACK, source.asSource(ON_ATTACK));

		if (dmg == null) {
			dmg = source.getActiveAttr();
		}

		int lifesteal = you.getBase().lifesteal();
		double dmgMult = 1d / (1 << op.getChainReduction());
		if (getTurn() < 3 || you.getLockTime(Lock.TAUNT) > 0) {
			dmgMult /= 2;
		}

		try {
			if ((announce && source.canAttack()) || source.isAvailable()) {
				for (Evogear e : source.getEquipments()) {
					JSONArray charms = e.getCharms();

					for (Object o : charms) {
						Charm c = Charm.valueOf(String.valueOf(o));
						switch (c) {
							case PIERCING -> op.modHP((int) -(dmg * dmgMult * c.getValue(e.getTier()) / 100));
							case WOUNDING -> {
								int val = (int) -(dmg * dmgMult * c.getValue(e.getTier()) / 100);
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

				switch (you.getOrigin().synergy()) {
					case SHIKIGAMI -> {
						List<SlotColumn> slts = arena.getSlots(op.getSide());
						for (SlotColumn slt : slts) {
							if (slt.hasTop()) {
								slt.getTop().getStats().setDodge(-2);
							}
						}
					}
					case FALLEN -> {
						if (op.getRegDeg().peek() < 0) {
							op.getRegDeg().apply(0.05);
						}
					}
					case SPAWN -> op.getRegDeg().leftShift(-op.getBase().hp() * 0.05);
				}

				if (!source.popFlag(Flag.NO_COMBAT)) {
					for (SlotColumn sc : getSlots(op.getSide())) {
						for (Senshi card : sc.getCards()) {
							if (card instanceof TrapSpell) {
								EffectParameters params = new EffectParameters(
										ON_TRAP, op.getSide(),
										card.asSource(ON_TRAP),
										source.asTarget(ON_ATTACK, TargetType.ENEMY)
								);

								if (activateProxy(card, params)) {
									source.setAvailable(false);
									getChannel().sendMessage(getLocale().get("str/trap_activation", card)).queue();
								}
							}
						}
					}

					if ((announce && source.canAttack()) || source.isAvailable()) {
						trigger(ON_DIRECT, source.asSource(ON_DIRECT));
					} else {
						dmg = 0;
					}
				}

				op.modHP((int) -(dmg * dmgMult));
				op.addChain();

				if (lifesteal > 0) {
					you.modHP(Math.max(0, eHP - op.getHP()) * lifesteal / 100);
				}
			}
		} finally {
			if (announce && source.getSlot().getIndex() != -1 && !source.popFlag(Flag.FREE_ACTION)) {
				source.setAvailable(false);
			}
		}

		String outcome = "";
		if (eHP != op.getHP()) {
			int val = eHP - op.getHP();
			outcome += "\n" + getLocale().get(val > 0 ? "str/combat_damage_dealt" : "str/combat_heal_op", Math.abs(val));
		}
		if (pHP != you.getHP()) {
			int val = pHP - you.getHP();
			outcome += "\n" + getLocale().get(val > 0 ? "str/combat_damage_taken" : "str/combat_heal_self", Math.abs(val));
		}

		if (announce) {
			reportEvent("str/combat", true, source, op.getName(), outcome);
		}

		return true;
	}

	public Arcade getArcade() {
		return arcade;
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

	public boolean hasCheated() {
		return singleplayer || cheats;
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

	public void iterateSlots(Consumer<Senshi> act) {
		for (Side side : Side.values()) {
			iterateSlots(side, act);
		}
	}

	public void iterateSlots(Side side, Consumer<Senshi> act) {
		for (SlotColumn slot : arena.getSlots(side)) {
			for (Senshi card : slot.getCards()) {
				if (card != null) {
					act.accept(card);
				}
			}
		}
	}

	public Senshi findCard(Side side, String id) {
		for (SlotColumn slt : getSlots(side)) {
			for (Senshi s : slt.getCards()) {
				if (s != null && s.getId().equalsIgnoreCase(id)) {
					return s;
				}
			}
		}

		return null;
	}

	public Senshi findCard(Side side, Predicate<Senshi> condition) {
		for (SlotColumn slt : getSlots(side)) {
			for (Senshi s : slt.getCards()) {
				if (s != null && condition.test(s)) {
					return s;
				}
			}
		}

		return null;
	}

	public BondedList<Drawable<?>> getBanned() {
		return arena.getBanned();
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

		iterateSlots(side, s -> s.execute(true, new EffectParameters(trigger, side, s.asSource(trigger))));

		Hand h = hands.get(side);
		for (EffectHolder<?> leech : h.getLeeches()) {
			leech.execute(new EffectParameters(ON_LEECH, side, leech.asSource(trigger)));
		}

		EffectParameters ep = new EffectParameters(trigger, side);
		if (Utils.equalsAny(trigger, ON_VICTORY, ON_DEFEAT)) {
			for (Drawable<?> card : h.getCards()) {
				if (card instanceof EffectHolder<?> eh) {
					eh.execute(ep);
				}
			}
		}

		triggerEOTs(ep);
	}

	public boolean trigger(Trigger trigger, Source source, Target... targets) {
		if (restoring) return false;

		EffectParameters ep = new EffectParameters(trigger, source.side(), source, targets);

		try {
			return source.execute(ep);
		} finally {
			for (Target t : ep.targets()) {
				t.execute(ep);
			}

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

			if (ep.size() == 0) {
				if (checkSide.test(ep.side()) && effect.triggers().contains(ep.trigger())) {
					effect.decreaseLimit();

					try {
						effect.effect().accept(effect, new EffectParameters(ep.trigger(), ep.side()));
					} catch (ActivationException ignore) {
					} catch (Exception e) {
						getChannel().sendMessage(getLocale().get("error/effect")).queue();
						Constants.LOGGER.warn("Failed to execute " + effect.source() + " persistent effect", e);
					}

					if (effect.side() == null) {
						effect.lock().set(true);
					}
				}

				remove = effect.expired() || effect.removed();
			} else if (ep.source() != null) {
				if (checkSide.test(ep.source().side()) && effect.triggers().contains(ep.source().trigger())) {
					effect.decreaseLimit();

					try {
						effect.effect().accept(effect, new EffectParameters(ep.source().trigger(), ep.side(), ep.source(), ep.targets()));
					} catch (ActivationException ignore) {
					} catch (Exception e) {
						getChannel().sendMessage(getLocale().get("error/effect")).queue();
						Constants.LOGGER.warn("Failed to execute " + effect.source() + " persistent effect", e);
					}
				}

				for (Target t : ep.targets()) {
					if (checkSide.test(t.side()) && effect.triggers().contains(t.trigger())) {
						effect.decreaseLimit();

						try {
							effect.effect().accept(effect, new EffectParameters(t.trigger(), ep.side(), ep.source(), ep.targets()));
						} catch (ActivationException ignore) {
						} catch (Exception e) {
							getChannel().sendMessage(getLocale().get("error/effect")).queue();
							Constants.LOGGER.warn("Failed to execute " + effect.source() + " persistent effect", e);
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
				GuildMessageChannel channel = Main.getApp().getMessageChannelById(chn);
				if (channel != null) {
					channel.retrieveMessageById(msg)
							.flatMap(Objects::nonNull, Message::delete)
							.queue(null, Utils::doNothing);
				}
			}

			return message.getId();
		};
	}

	private void reportEvent(String message, boolean trigger, Object... args) {
		if (getChannel() == null) return;

		for (GuildMessageChannel chn : getChannel().getChannels()) {
			String msg = messages.get(chn.getId());
			if (msg != null) {
				chn.retrieveMessageById(msg)
						.flatMap(Message::editMessageComponents)
						.queue(null, Utils::doNothing);
			}
		}

		resetTimer();
		if (trigger) {
			trigger(ON_TICK);
			getCurrent().setRerolled(true);
		}

		List<Side> sides = List.of(getOtherSide(), getCurrentSide());
		for (Side side : sides) {
			Hand hand = hands.get(side);
			hand.getCards();
			hand.getRealDeck();
			hand.getGraveyard();
			hand.resetChain();
			hand.getStats().removeExpired(ValueMod::isExpired);

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

				if (def != null) {
					reportResult(GameReport.SUCCESS, hand.getOther().getSide(), "str/game_end_special", def, "<@" + hand.getOther().getUid() + ">");
				} else {
					reportResult(GameReport.SUCCESS, hand.getOther().getSide(), "str/game_end", "<@" + hand.getUid() + ">", "<@" + hand.getOther().getUid() + ">");
				}

				return;
			}

			iterateSlots(side, s -> {
				s.setLastInteraction(null);
				s.getStats().removeExpired(ValueMod::isExpired);
				for (Evogear e : s.getEquipments()) {
					e.getStats().removeExpired(ValueMod::isExpired);
				}
			});
		}

		BufferedImage img = history ? arena.render(getLocale(), getHistory()) : arena.render(getLocale());
		byte[] bytes = IO.getBytes(img, "png", 0.5f);

		AtomicBoolean registered = new AtomicBoolean();
		getChannel().sendMessage(getLocale().get(message, args))
				.addFile(bytes, "game.png")
				.queue(m -> {
					messages.compute(m.getChannel().getId(), replaceMessages(m));

					if (!registered.get()) {
						if (!message.startsWith("str/game_history")) {
							getHistory().add(new HistoryLog(m.getContentDisplay(), getCurrentSide()));
						}

						registered.set(true);
					}
				});
	}

	private void reportResult(@MagicConstant(valuesFromClass = GameReport.class) byte code, Side winner, String message, Object... args) {
		if (isClosed()) return;
		turns.add(Turn.from(this));

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
				GuildMessageChannel channel = Main.getApp().getMessageChannelById(tuple.getKey());
				if (channel != null) {
					channel.retrieveMessageById(tuple.getValue())
							.flatMap(Objects::nonNull, Message::delete)
							.queue(null, Utils::doNothing);
				}
			}
		}

		if (winner != null) {
			this.winner = winner;
		}

		if (!singleplayer && arcade == null && !cheats) {
			new MatchHistory(new Match(this, message.equals("str/game_end") ? "default" : String.valueOf(args[0]))).save();
		}

		close(code);
	}

	private void addButtons(Message msg) {
		Hand curr = getCurrent();
		Map<Emoji, ThrowingConsumer<ButtonWrapper>> buttons = new LinkedHashMap<>();
		buttons.put(Utils.parseEmoji("▶"), w -> {
			if (curr.selectionPending()) {
				getChannel().sendMessage(getLocale().get("error/pending_choice")).queue();
				return;
			} else if (getPhase() == Phase.COMBAT || getTurn() == 1) {
				if (curr.getLockTime(Lock.TAUNT) > 0) {
					List<SlotColumn> yours = getSlots(curr.getSide());
					if (yours.stream().anyMatch(sc -> sc.getTop() != null && sc.getTop().canAttack())) {
						getChannel().sendMessage(getLocale().get("error/pending_choice", false, curr.getLockTime(Lock.TAUNT))).queue();
						return;
					}
				}

				nextTurn();
				return;
			}

			setPhase(Phase.COMBAT);
			reportEvent("str/game_combat_phase", true, true);
		});

		if (getPhase() == Phase.PLAN) {
			buttons.put(Utils.parseEmoji("⏩"), w -> {
				if (curr.selectionPending()) {
					getChannel().sendMessage(getLocale().get("error/pending_choice")).queue();
					return;
				} else if (curr.getLockTime(Lock.TAUNT) > 0) {
					List<SlotColumn> yours = getSlots(curr.getSide());
					if (yours.stream().anyMatch(sc -> sc.getTop() != null && sc.getTop().canAttack())) {
						getChannel().sendMessage(getLocale().get("error/pending_choice", false, curr.getLockTime(Lock.TAUNT))).queue();
						return;
					}
				}

				nextTurn();
			});

			if (getTurn() == 1 && !curr.hasRerolled()) {
				buttons.put(Utils.parseEmoji("\uD83D\uDD04"), w -> {
					curr.rerollHand();
					reportEvent("str/hand_reroll", true, curr.getName());
				});
			}

			if (!curr.getRealDeck().isEmpty() && arcade != Arcade.DECK_ROYALE) {
				int rem = curr.getRemainingDraws();
				if (rem > 0) {
					buttons.put(Utils.parseEmoji("📤"), w -> {
						if (curr.selectionPending()) {
							getChannel().sendMessage(getLocale().get("error/pending_choice")).queue();
							return;
						}

						curr.manualDraw(1);
						curr.showHand();
						reportEvent("str/draw_card", true, curr.getName(), 1, "");
					});

					if (rem > 1) {
						buttons.put(Utils.parseEmoji("📦"), w -> {
							if (curr.selectionPending()) {
								getChannel().sendMessage(getLocale().get("error/pending_choice")).queue();
								return;
							}

							curr.manualDraw(curr.getRemainingDraws());
							curr.showHand();
							reportEvent("str/draw_card", true, curr.getName(), rem, "s");
						});
					}
				}

				if (curr.isCritical() && !curr.hasUsedDestiny()) {
					if (Utils.equalsAny(curr.getOrigin().major(), Race.MACHINE, Race.MYSTICAL)) {
						buttons.put(Utils.parseEmoji("⚡"), w -> {
							if (curr.selectionPending()) {
								getChannel().sendMessage(getLocale().get("error/pending_choice")).queue();
								return;
							}

							curr.setEmpowered(true);
							curr.setUsedDestiny(true);

							if (curr.getOrigin().major() == Race.MACHINE) {
								reportEvent("str/martial_empower", true, curr.getName());
							} else {
								reportEvent("str/arcane_empower", true, curr.getName());
							}
						});
					} else {
						buttons.put(Utils.parseEmoji("\uD83E\uDDE7"), w -> {
							if (curr.selectionPending()) {
								getChannel().sendMessage(getLocale().get("error/pending_choice")).queue();
								return;
							}

							BondedList<Drawable<?>> deque = curr.getRealDeck();
							List<Drawable<?>> cards = new ArrayList<>();
							cards.add(deque.getFirst());
							if (deque.size() > 2) cards.add(deque.get((deque.size() - 1) / 2));
							if (deque.size() > 1) cards.add(deque.getLast());

							Drawable<?> d = curr.requestChoice(cards);
							if (d != null) {
								curr.getCards().add(d);
								deque.remove(d);

								curr.showHand();
								reportEvent("str/destiny_draw", true, curr.getName());
							}

							curr.setUsedDestiny(true);
						});
					}
				}
			}

			if (curr.getOrigin().major() == Race.SPIRIT && curr.getDiscard().size() >= 3 && curr.getOriginCooldown() == 0) {
				buttons.put(Utils.parseEmoji("\uD83C\uDF00"), w -> {
					if (curr.selectionPending()) {
						getChannel().sendMessage(getLocale().get("error/pending_choice")).queue();
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

					curr.getCards().add(SynthesizeCommand.rollSynthesis(curr.getUser(), cards));
					curr.setOriginCooldown(3);
					curr.showHand();
					reportEvent("str/spirit_synth", true, curr.getName());
				});
			}

			buttons.put(Utils.parseEmoji("\uD83D\uDCD1"), w -> {
				history = !history;

				if (history) {
					reportEvent("str/game_history_enable", false, curr.getName());
				} else {
					reportEvent("str/game_history_disable", false, curr.getName());
				}
			});

			buttons.put(Utils.parseEmoji("\uD83E\uDEAA"), w -> {
				if (curr.selectionPending()) {
					w.getHook().setEphemeral(true)
							.sendFiles(FileUpload.fromData(IO.getBytes(curr.renderChoices(), "png"), "choices.png"))
							.queue();
					return;
				}

				w.getHook().setEphemeral(true)
						.sendFiles(FileUpload.fromData(IO.getBytes(curr.render(), "png"), "hand.png"))
						.queue();
			});

			buttons.put(Utils.parseEmoji("\uD83D\uDD0D"),
					w -> w.getHook().setEphemeral(true)
							.sendFiles(FileUpload.fromData(IO.getBytes(arena.renderEvogears(), "png"), "evogears.png"))
							.queue()
			);

			if (singleplayer || getTurn() > 10) {
				buttons.put(Utils.parseEmoji("🏳"), w -> {
					if (curr.isForfeit()) {
						reportResult(GameReport.SUCCESS, getOther().getSide(), "str/game_forfeit", "<@" + getCurrent().getUid() + ">");
						return;
					}

					curr.setForfeit(true);
					w.getHook().setEphemeral(true)
							.sendMessage(getLocale().get("str/confirm_forfeit"))
							.queue();
				});
			}
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
		return LocalizedString.get(getLocale(), key, "").formatted(params);
	}

	public void send(Drawable<?> source, String text) {
		send(source, text, null);
	}

	public void send(Drawable<?> source, String text, String gif) {
		for (GuildMessageChannel chn : getChannel().getChannels()) {
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

		if (arcade == Arcade.DECK_ROYALE) {
			boolean noHand = curr.getCards().stream().noneMatch(d -> d instanceof Senshi);
			boolean noField = getSlots(curr.getSide()).stream()
					.flatMap(sc -> sc.getCards().stream())
					.noneMatch(Objects::nonNull);

			if (noHand && noField) {
				reportResult(GameReport.SUCCESS, getOther().getSide(), "arcade/deck_royale_win", "<@" + curr.getUid() + ">", "<@" + curr.getOther().getUid() + ">");
				return;
			}
		}

		if (curr.getOrigin().synergy() == Race.ANGEL) {
			curr.modHP(curr.getMP() * 10);
		}

		for (Lock lock : Lock.values()) {
			curr.modLockTime(lock, -1);
		}

		for (SlotColumn slt : getSlots(curr.getSide())) {
			slt.reduceLock(1);

			for (Senshi s : slt.getCards()) {
				if (s != null && s.getSlot().getIndex() != -1) {
					s.reduceSleep(1);
					s.reduceStun(1);
					s.reduceCooldown(1);
					s.reduceTaunt(1);
					s.setAvailable(true);
					s.setSwitched(false);

					s.clearBlocked();
					s.getStats().clearTFlags();
					for (Evogear e : s.getEquipments()) {
						e.getStats().clearTFlags();
					}

					if (arcade == Arcade.DECAY) {
						s.getStats().setMana(-1);
						if (s.getMPCost() == 0) {
							s.getHand().getGraveyard().add(s);
						}
					}
				}
			}
		}

		if (arcade == Arcade.INSTABILITY) {
			int affected = Math.min((int) Math.ceil(getTurn() / 2d), 8);
			List<SlotColumn> chosen = Utils.getRandomN(Utils.flatten(arena.getSlots().values()), affected, 1);

			for (SlotColumn slt : chosen) {
				slt.setLock(1);
			}
		}

		super.nextTurn();
		setPhase(Phase.PLAN);
		curr = getCurrent();
		curr.modMP(curr.getBase().mpGain().get());
		curr.applyVoTs();
		curr.reduceOriginCooldown(1);
		curr.getStats().expireMods();

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
		reportEvent("str/game_turn_change", true, "<@" + curr.getUid() + ">", (int) Math.ceil(getTurn() / 2d));

		takeSnapshot();
	}

	public Side getWinner() {
		return winner;
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

	public boolean isRestoring() {
		return restoring;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Shoukan shoukan = (Shoukan) o;
		return seed == shoukan.seed && arcade == shoukan.arcade && singleplayer == shoukan.singleplayer;
	}

	@Override
	public int hashCode() {
		return Objects.hash(seed, arcade, singleplayer);
	}
}