/*
 * Copyright (C) 2019 Yago Garcia Sanches Gimenez / KuuHaKu
 *
 * This file is part of Shiro J Bot.
 *
 *     Shiro J Bot is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Shiro J Bot is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Shiro J Bot.  If not, see https://www.gnu.org/licenses/
 */

package com.kuuhaku.model;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Beyblade {
    @Id
    private String id;
    private String name = "";
    private String color = "#ffffff";
    private float speed = 1, strength = 1, stability = 1;
    private int life = 100, wins = 0, loses = 0;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getStrength() {
        return strength;
    }

    public void setStrength(float strength) {
        this.strength = strength;
    }

    public float getStability() {
        return stability;
    }

    public void setStability(float stability) {
        this.stability = stability;
    }

    public int getLife() {
        return life;
    }

    public void setLife(int life) {
        this.life = life;
    }

    public int getWins() {
        return wins;
    }

    public void addWins() {
        wins += 1;
    }

    public int getLoses() {
        return loses;
    }

    public void addLoses() {
        loses += 1;
    }
}
