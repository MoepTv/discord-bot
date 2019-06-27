package tv.moep.discord.bot.managers;

/*
 * discordbot
 * Copyright (C) 2019 Max Lee aka Phoenix616 (mail@moep.tv)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.server.Server;
import tv.moep.discord.bot.MoepsBot;

public abstract class Manager {
    private final Config config;
    private final MoepsBot moepsBot;

    protected Manager(MoepsBot moepsBot, String name) {
        this.moepsBot = moepsBot;
        config = moepsBot.getConfig(name);
    }

    public Config getConfig() {
        return config;
    }

    public MoepsBot getMoepsBot() {
        return moepsBot;
    }

    public Config getConfig(Server server) {
        try {
            return getConfig().getConfig(server.getIdAsString());
        } catch (ConfigException e) {
            return null;
        }
    }

    public Config getConfig(ServerChannel channel) {
        try {
            return getConfig().getConfig(channel.getServer().getIdAsString() + "." + channel.getIdAsString());
        } catch (ConfigException e) {
            return null;
        }
    }

    public boolean hasPath(ServerChannel channel, String path) {
        return getConfig().hasPath(channel.getServer().getId() + "." + channel.getId() + "." + path)
                || getConfig().hasPath(channel.getServer().getId() + "." + path);
    }

    public boolean has(ServerChannel channel, String option) {
        if (getConfig().hasPath(channel.getServer().getId() + "." + channel.getId() + "." + option)) {
            return getConfig().getBoolean(channel.getServer().getId() + "." + channel.getId() + "." + option);
        }
        if (getConfig().hasPath(channel.getServer().getId() + "." + option)) {
            return getConfig().getBoolean(channel.getServer().getId() + "." + option);
        }
        return false;
    }

    public int getInt(ServerChannel channel, String path) {
        if (getConfig().hasPath(channel.getServer().getId() + "." + channel.getId() + "." + path)) {
            return getConfig().getInt(channel.getServer().getId() + "." + channel.getId() + "." + path);
        }
        if (getConfig().hasPath(channel.getServer().getId() + "." + path)) {
            return getConfig().getInt(channel.getServer().getId() + "." + path);
        }
        return -1;
    }
}