package com.kuuhaku.interfaces.dunhun;

import com.kuuhaku.game.Dunhun;
import com.kuuhaku.model.common.XStringBuilder;
import com.kuuhaku.model.common.dunhun.ActorModifiers;
import com.kuuhaku.model.common.dunhun.Combat;
import com.kuuhaku.model.common.shoukan.RegDeg;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.enums.dunhun.RarityClass;
import com.kuuhaku.model.enums.dunhun.Team;
import com.kuuhaku.model.enums.shoukan.Race;
import com.kuuhaku.model.enums.shoukan.Trigger;
import com.kuuhaku.model.persistent.dunhun.Skill;
import com.kuuhaku.model.persistent.shoukan.Senshi;
import com.kuuhaku.util.Utils;

import java.awt.image.BufferedImage;
import java.util.List;

public interface Actor {
	String getId();

	String getName(I18N locale);

	Race getRace();

	int getHp();

	int getMaxHp();

	int getHpDelta();

	default void modHp(int value) {
		modHp(value, false);
	}

	default void modHp(int value, boolean crit) {
		modHp(value, crit, false);
	}

	default void modHp(int value, boolean crit, boolean pure) {
		if (value == 0 || getHp() == 0) return;
		if (crit) value *= 2;

		Dunhun game = getGame();
		if (!pure && game != null) {
			Senshi sen = getSenshi();
			if (value < 0) {
				double fac = sen.isDefending() ? 2 : 1;
				if (game.isDuel()) {
					fac *= 25;
				}

				value = -value;
				value = (int) -Math.max(value / (5 * fac), ((5 / fac) * Math.pow(value, 2)) / (sen.getDfs() + (5 / fac) * value));
			}

			if (sen.isStasis()) {
				getRegDeg().add(Math.min(value, 0));
				return;
			}
		}

		int diff = getHp();
		setHp(getHp() + value, pure);

		if (!pure && game != null && game.getCombat() != null) {
			Combat comb = game.getCombat();
			if (value < 0) {
				comb.trigger(Trigger.ON_DAMAGE, this, this);

				Senshi sen = getSenshi();
				if (sen.isSleeping()) {
					sen.reduceSleep(999);
				}
			} else {
				comb.trigger(Trigger.ON_HEAL, this, this);
			}

			diff = getHp() - diff;
			if (diff == 0) return;

			I18N locale = game.getLocale();
			comb.getHistory().add(locale.get(diff < 0 ? "str/actor_damage" : "str/actor_heal",
					getName(locale), Math.abs(diff), crit ? ("**(" + locale.get("str/critical_hit") + ")**") : ""
			));
		}
	}

	default void setHp(int value) {
		setHp(value, false);
	}

	void setHp(int value, boolean bypass);

	default void revive(int value) {
		if (getHp() >= value) return;

		setHp(value);
		Dunhun game = getGame();
		if (game != null) {
			getSenshi().setAvailable(true);
		}
	}

	default void addHpBar(XStringBuilder sb) {
		double part;
		if (getMaxHp() > 10000) part = 1000;
		else if (getMaxHp() > 5000) part = 500;
		else if (getMaxHp() > 2500) part = 250;
		else part = 100;

		String hp, max;
		if (getHp() > part * 10) {
			hp = Utils.roundToString(getHp() / 1000d, 1) + "k";
		} else {
			hp = String.valueOf(getHp());
		}

		if (getMaxHp() > part * 10) {
			max = Utils.roundToString(getMaxHp() / 1000d, 1) + "k";
		} else {
			max = String.valueOf(getMaxHp());
		}

		sb.appendNewLine("HP: " + hp + "/" + max);
		sb.nextLine();

		boolean rdClosed = true;
		int rd = -getRegDeg().peek();
		if (rd >= part) {
			sb.append("__");
			rdClosed = false;
		}

		int steps = (int) Math.ceil(getMaxHp() / part);
		for (int i = 0; i < steps; i++) {
			if (i > 0 && i % 10 == 0) sb.nextLine();
			int threshold = (int) (i * part);

			if (!rdClosed && threshold > rd) {
				sb.append("__");
				rdClosed = true;
			}

			if (getHp() > 0 && getHp() >= threshold) sb.append('▰');
			else sb.append('▱');
		}

		if (rd >= getMaxHp() && !rdClosed) {
			sb.append("__");
		}
	}

	int getAp();

	int getMaxAp();

	void modAp(int value);

	default void addApBar(XStringBuilder sb) {
		sb.appendNewLine(Utils.makeProgressBar(getAp(), getMaxAp(), getMaxAp(), '◇', '◈'));
	}

	int getInitiative();

	double getCritical();

	int getAggroScore();

	boolean hasFleed();

	void setFleed(boolean flee);

	default boolean isOutOfCombat() {
		return hasFleed() || getHp() <= 0;
	}

	RarityClass getRarityClass();

	ActorModifiers getModifiers();

	void trigger(Trigger trigger, Actor target);

	RegDeg getRegDeg();

	Senshi asSenshi(I18N locale);

	default Senshi getSenshi() {
		return asSenshi(getGame().getLocale());
	}

	BufferedImage render(I18N locale);

	List<Skill> getSkills();

	default Skill getSkill(String id) {
		return getSkills().stream()
				.filter(skill -> skill.getId().equals(id))
				.findFirst()
				.orElse(null);
	}

	Team getTeam();

	void setTeam(Team team);

	Dunhun getGame();

	void setGame(Dunhun game);

	Actor fork() throws CloneNotSupportedException;

	default Actor copy() {
		try {
			return fork();
		} catch (Exception e) {
			return this;
		}
	}
}
