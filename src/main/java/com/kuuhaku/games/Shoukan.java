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

package com.kuuhaku.games;

import com.github.ygimenez.method.Pages;
import com.github.ygimenez.model.ButtonWrapper;
import com.github.ygimenez.model.ThrowingConsumer;
import com.kuuhaku.Main;
import com.kuuhaku.games.engine.GameInstance;
import com.kuuhaku.games.engine.PhaseConstraint;
import com.kuuhaku.games.engine.PlayerAction;
import com.kuuhaku.interfaces.shoukan.Drawable;
import com.kuuhaku.model.common.shoukan.Arena;
import com.kuuhaku.model.common.shoukan.Hand;
import com.kuuhaku.model.common.shoukan.SlotColumn;
import com.kuuhaku.model.enums.Charm;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.enums.shoukan.Phase;
import com.kuuhaku.model.enums.shoukan.Side;
import com.kuuhaku.model.persistent.shoukan.Evogear;
import com.kuuhaku.model.persistent.shoukan.Senshi;
import com.kuuhaku.utils.IO;
import com.kuuhaku.utils.Utils;
import com.kuuhaku.utils.json.JSONObject;
import kotlin.Pair;
import net.dv8tion.jda.api.entities.*;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class Shoukan extends GameInstance<Phase> {
	private final I18N locale;
	private final String[] players;
	private final Map<Side, Hand> hands;
	private final Arena arena = new Arena(this);
	private final Map<String, Pair<String, String>> messages = new HashMap<>();

	public Shoukan(I18N locale, User p1, User p2) {
		this(locale, p1.getId(), p2.getId());
	}

	public Shoukan(I18N locale, String p1, String p2) {
		this.locale = locale;
		this.players = new String[]{p1, p2};
		this.hands = Map.of(
				Side.TOP, new Hand(p1, Side.TOP),
				Side.BOTTOM, new Hand(p2, Side.BOTTOM)
		);

		setTimeout(turn -> {
			getChannel().sendMessage(locale.get("str/game_wo", "<@" + getCurrent().getUid() + ">"))
					.addFile(IO.getBytes(arena.render(locale), "webp"), "game.webp")
					.queue(m -> {

					});

			close(1);
		}, 5, TimeUnit.MINUTES);
	}

	@Override
	protected boolean validate(Message message) {
		return ((Predicate<Message>) m -> ArrayUtils.contains(players, m.getAuthor().getId()))
				.and(m -> getTurn() % 2 == Arrays.binarySearch(players, m.getAuthor().getId()))
				.test(message);
	}

	@Override
	protected void begin() {
		Hand curr = getCurrent();
		curr.modMP(curr.getBase().mpGain().apply(getTurn()));

		getChannel().sendMessage(locale.get("str/game_start", "<@" + curr.getUid() + ">"))
				.addFile(IO.getBytes(arena.render(locale), "webp"), "game.webp")
				.queue(m -> messages.compute(m.getGuild().getId(), replaceMessages(m)));

		sendPlayerHand(curr);
		setPhase(Phase.PLAN);
	}

	@Override
	protected void runtime(String value) throws InvocationTargetException, IllegalAccessException {
		Pair<Method, JSONObject> action = toAction(value.toLowerCase(Locale.ROOT).replace(" ", ""));
		if (action != null) {
			if ((boolean) action.getFirst().invoke(this, action.getSecond())) {
				sendPlayerHand(getCurrent());
			}
		}

		/*
		nextTurn();
		Hand curr = getCurrent();
		getChannel().sendMessage(locale.get("str/game_turn_change", "<@" + curr.getUid() + ">", getTurn()))
				.addFile(IO.getBytes(arena.render(locale), "webp"), "game.webp")
				.queue(Once.exec(m ->
						curr.getUser().openPrivateChannel()
								.flatMap(chn -> chn.sendFile(IO.getBytes(curr.render(locale), "png"), "hand.png"))
								.queue()
				));

		 */
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\d+),(?<mode>[adb]),(?<inField>[1-5])(?<notCombat>,nc)?")
	private boolean placeCard(JSONObject args) {
		Hand curr = getCurrent();
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
			}
		} else {
			getChannel().sendMessage(locale.get("error/wrong_card_type")).queue();
			return false;
		}

		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);
		if (args.getBoolean("notCombat")) {
			if (slot.hasBottom()) {
				getChannel().sendMessage(locale.get("error/slot_occupied")).queue();
				return false;
			}

			curr.modHP(-chosen.getHPCost());
			curr.modMP(-chosen.getMPCost());
			chosen.setAvailable(false);
			slot.setBottom(chosen.withCopy(s -> {
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

			curr.modHP(-chosen.getHPCost());
			curr.modMP(-chosen.getMPCost());
			chosen.setAvailable(false);
			slot.setTop(chosen.withCopy(s -> {
				switch (args.getString("mode")) {
					case "d" -> s.setDefending(true);
					case "b" -> s.setFlipped(true);
				}
			}));
		}

		reportEvent("str/place_card",
				curr.getName(),
				chosen.isFlipped() ? locale.get("str/a_card") : chosen,
				chosen.getState().toString(locale)
		);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\d+),(?<inField>[1-5])")
	private boolean equipCard(JSONObject args) {
		Hand curr = getCurrent();
		if (!Utils.between(args.getInt("inHand"), 1, curr.getCards().size() + 1)) {
			getChannel().sendMessage(locale.get("error/invalid_hand_index")).queue();
			return false;
		}

		if (curr.getCards().get(args.getInt("inHand") - 1) instanceof Evogear chosen) {
			if (!chosen.isAvailable()) {
				getChannel().sendMessage(locale.get("error/card_unavailable")).queue();
				return false;
			} else if (chosen.getHPCost() >= curr.getHP()) {
				getChannel().sendMessage(locale.get("error/not_enough_hp")).queue();
				return false;
			} else if (chosen.getMPCost() > curr.getMP()) {
				getChannel().sendMessage(locale.get("error/not_enough_mp")).queue();
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
		} else if (slot.getTop().getEquipments().size() == 3) {
			getChannel().sendMessage(locale.get("error/equipment_capacity", slot.getTop())).queue();
			return false;
		}

		Senshi target = slot.getTop();
		curr.modHP(-chosen.getHPCost());
		curr.modMP(-chosen.getMPCost());
		chosen.setAvailable(false);
		target.getEquipments().add(chosen.withCopy(e -> e.setFlipped(e.getCharms().contains(Charm.TRAP))));
		reportEvent("str/equip_card",
				curr.getName(),
				chosen.isFlipped() ? locale.get("str/an_equipment") : chosen,
				target.isFlipped() ? locale.get("str/a_card") : target
		);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inField>[1-5]),f(?<notCombat>,nc)?")
	private boolean flipCard(JSONObject args) {
		Hand curr = getCurrent();
		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);

		boolean nc = args.getBoolean("notCombat");
		if ((nc && !slot.hasBottom()) || (!nc && !slot.hasTop())) {
			getChannel().sendMessage(locale.get("error/missing_card", slot.getIndex() + 1)).queue();
			return false;
		}

		Senshi chosen = nc ? slot.getBottom() : slot.getTop();
		if (chosen.isFlipped()) {
			chosen.setFlipped(false);
		} else {
			chosen.setDefending(!chosen.isDefending());
		}

		reportEvent("str/flip_card", curr.getName(), chosen, chosen.getState().toString(locale));
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inField>[1-5]),p")
	private boolean promoteCard(JSONObject args) {
		Hand curr = getCurrent();
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
	private boolean sacrificeCard(JSONObject args) {
		Hand curr = getCurrent();
		SlotColumn slot = arena.getSlots(curr.getSide()).get(args.getInt("inField") - 1);

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

		curr.modHP(-chosen.getHPCost() / 2);
		curr.modMP(-chosen.getMPCost() / 2);

		if (nc) {
			slot.setBottom(null);
		} else {
			slot.setTop(null);
		}
		curr.getGraveyard().add(chosen);

		reportEvent("str/sacrifice_card", curr.getName(), chosen);
		return true;
	}

	@PhaseConstraint("PLAN")
	@PlayerAction("(?<inHand>\\d+),d")
	private boolean discardCard(JSONObject args) {
		Hand curr = getCurrent();
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

		reportEvent("str/discard_card", curr.getName(), chosen);
		return true;
	}

	@PhaseConstraint("COMBAT")
	@PlayerAction("(?<inField>[1-5])(?:,(?<target>[1-5]))?")
	private boolean attack(JSONObject args) {
		Hand you = getCurrent();
		SlotColumn yourSlot = arena.getSlots(you.getSide()).get(args.getInt("inField") - 1);

		if (!yourSlot.hasTop()) {
			getChannel().sendMessage(locale.get("error/missing_card", yourSlot.getIndex() + 1)).queue();
			return false;
		}

		Senshi ally = yourSlot.getTop();

		Hand op = getOther();
		Senshi enemy = null;
		if (args.getBoolean("target")) {
			SlotColumn opSlot = arena.getSlots(op.getSide()).get(args.getInt("target") - 1);

			if (!opSlot.hasTop()) {
				if (!opSlot.hasBottom()) {
					getChannel().sendMessage(locale.get("error/missing_card", opSlot.getIndex() + 1)).queue();
					return false;
				} else if (!arena.isFieldEmpty(op.getSide())) {
					getChannel().sendMessage(locale.get("error/field_not_empty")).queue();
					return false;
				}

				enemy = opSlot.getBottom();
			} else {
				enemy = opSlot.getTop();
			}
		}

		int dmg = ally.getDmg();
		String outcome;
		if (enemy != null) {
			if (enemy.isSupporting()) {
				op.getGraveyard().add(enemy);
				outcome = locale.get("str/combat_direct", dmg);
			} else {
				if (ally.getDmg() > enemy.getActiveAttr()) {
					if (enemy.isDefending()) {
						dmg = 0;
					} else {
						dmg -= enemy.getActiveAttr();
					}
					op.getGraveyard().add(enemy);
					outcome = locale.get("str/combat_success", dmg);
				} else if (ally.getDmg() < enemy.getActiveAttr()) {
					you.getGraveyard().add(ally);
					you.modHP(-(enemy.getActiveAttr() - ally.getDmg()));
					reportEvent("str/combat", ally, enemy, locale.get("str/combat_defeat"));
					return true;
				} else {
					op.getGraveyard().add(enemy);
					you.getGraveyard().add(ally);
					dmg = 0;
					outcome = locale.get("str/combat_clash", dmg);
				}
			}
		} else {
			outcome = locale.get("str/combat_direct", dmg);
		}

		op.modHP(-dmg);
		reportEvent("str/combat", ally, Utils.getOr(enemy, op.getName()), outcome);
		return false;
	}

	public Map<Side, Hand> getHands() {
		return hands;
	}

	public Hand getCurrent() {
		return hands.get(getTurn() % 2 == 0 ? Side.TOP : Side.BOTTOM);
	}

	public Hand getOther() {
		return hands.get(getTurn() % 2 == 1 ? Side.TOP : Side.BOTTOM);
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
		getChannel().sendMessage(locale.get(message, args))
				.addFile(IO.getBytes(arena.render(locale), "webp"), "game.webp")
				.queue(m -> messages.compute(m.getGuild().getId(), replaceMessages(m)));
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
				if (rem > 0) {
					put(Utils.parseEmoji("📤"), w -> {
						curr.draw(1);
						reportEvent("str/draw_card", curr.getName(), 1, "");
						sendPlayerHand(curr);
					});

					if (rem > 1) {
						put(Utils.parseEmoji("📦"), w -> {
							curr.draw(curr.getRemainingDraws());
							reportEvent("str/draw_card", curr.getName(), rem, "s");
							sendPlayerHand(curr);
						});
					}
				}
				put(Utils.parseEmoji("🏳"), w -> {
					if (curr.isForfeit()) {
						close(0);
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

	@Override
	protected void nextTurn() {
		Hand curr = getCurrent();
		curr.getCards().removeIf(d -> !d.isAvailable());
		curr.flushDiscard();

		super.nextTurn();
		setPhase(Phase.PLAN);
		curr = getCurrent();
		curr.modMP(curr.getBase().mpGain().apply(getTurn()));

		getChannel().sendMessage(locale.get("str/game_turn_change", "<@" + curr.getUid() + ">", getTurn()))
				.addFile(IO.getBytes(arena.render(locale), "webp"), "game.webp")
				.queue(m -> messages.compute(m.getGuild().getId(), replaceMessages(m)));

		sendPlayerHand(curr);
	}

	@Override
	protected void resetTimer() {
		super.resetTimer();
		getCurrent().setForfeit(false);
	}
}
