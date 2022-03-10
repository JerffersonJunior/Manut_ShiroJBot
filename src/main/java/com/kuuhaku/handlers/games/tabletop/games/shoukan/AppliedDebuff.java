package com.kuuhaku.handlers.games.tabletop.games.shoukan;

import com.kuuhaku.controller.postgresql.DebuffDAO;

import javax.persistence.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Entity
@Table(name = "applieddebuff")
public class AppliedDebuff {
	@Id
	@Column(columnDefinition = "VARCHAR(255) NOT NULL")
	private String debuff;

	@Column(columnDefinition = "TIMESTAMP")
	private ZonedDateTime expiration = null;

	public AppliedDebuff() {
	}

	public AppliedDebuff(Debuff debuff, long duration) {
		this.debuff = debuff.getId();
		this.expiration = ZonedDateTime.now(ZoneId.of("GMT-3")).plus(duration, ChronoUnit.SECONDS);
	}

	public Debuff getDebuff() {
		return DebuffDAO.getDebuff(debuff);
	}

	public void setDebuff(Debuff debuff) {
		this.debuff = debuff.getId();
	}

	public ZonedDateTime getExpiration() {
		return expiration;
	}

	public void setExpiration(ZonedDateTime expiration) {
		this.expiration = expiration;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		AppliedDebuff that = (AppliedDebuff) o;
		return Objects.equals(debuff, that.debuff);
	}

	@Override
	public int hashCode() {
		return Objects.hash(debuff);
	}
}
