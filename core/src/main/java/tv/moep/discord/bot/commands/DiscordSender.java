package tv.moep.discord.bot.commands;

/*
 * MoepTv - bot
 * Copyright (C) 2022 Max Lee aka Phoenix616 (max@themoep.de)
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

import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Permission;

public abstract class DiscordSender implements CommandSender<Message> {
    private final MoepsBot bot;
    private final User user;
    private final Server server;

    public DiscordSender(MoepsBot bot, User user, Server server) {
        this.bot = bot;
        this.user = user;
        this.server = server;
    }

    @Override
    public boolean hasPermission(Permission permission) {
        switch (permission) {
            case USER:
                return true;
            case ADMIN:
                if (user != null && server != null && server.isAdmin(user)) {
                    return true;
                }
            case OWNER:
                if (user != null && server != null && server.isOwner(user)) {
                    return true;
                }
            case OPERATOR:
                return user != null
                        && (user.isBotOwner()
                        || getBot().getConfig().getStringList("discord.operators").contains(user.getIdAsString())
                        || getBot().getConfig().getStringList("discord.operators").contains(user.getDiscriminatedName()));
        }
        return false;
    }

    public MoepsBot getBot() {
        return bot;
    }

    public Server getServer() {
        return server;
    }

    public User getUser() {
        return user;
    }
}