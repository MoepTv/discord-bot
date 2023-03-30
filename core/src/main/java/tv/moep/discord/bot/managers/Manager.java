package tv.moep.discord.bot.managers;

/*
 * MoepTv - bot
 * Copyright (C) 2020 Max Lee aka Phoenix616 (max@themoep.de)
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
import org.javacord.api.entity.server.Server;
import tv.moep.discord.bot.MoepsBot;

import java.util.logging.Level;

public abstract class Manager {
    private Config config;
    private final String name;
    private final MoepsBot moepsBot;

    protected Manager(MoepsBot moepsBot, String name) {
        this.moepsBot = moepsBot;
        this.name = name;
        loadConfig();
        MoepsBot.log(Level.INFO, "Manager " + name + " loaded!");
    }

    protected void loadConfig() {
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

    public Config getConfig(Server server, String path) {
        try {
            return getConfig().getConfig(server.getIdAsString() + "." + path);
        } catch (ConfigException e) {
            return null;
        }
    }

    public boolean has(Server server, String option) {
        if (getConfig().hasPath(server.getIdAsString() + "." + option)) {
            return getConfig().getBoolean(server.getIdAsString() + "." + option);
        }
        return false;
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

    public String getString(ServerChannel channel, String path, String def) {
        if (getConfig().hasPath(channel.getServer().getId() + "." + channel.getId() + "." + path)) {
            return getConfig().getString(channel.getServer().getId() + "." + channel.getId() + "." + path);
        }
        if (getConfig().hasPath(channel.getServer().getId() + "." + path)) {
            return getConfig().getString(channel.getServer().getId() + "." + path);
        }
        return def;
    }

    protected void notifyOperators(String message) {
        getMoepsBot().notifyOperators("[" + name + "] " + message);
    }

    protected void log(Level level, String message) {
        MoepsBot.log(level, "[" + name + "] " + message);
    }

    protected void logDebug(String message) {
        if (getConfig().getBoolean("debug")) {
            log(Level.INFO, "[DEBUG] " + message);
        }
    }
}
