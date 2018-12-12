package tv.moep.discord.bot.commands;

/*
 * discordbot
 * Copyright (C) 2018 Max Lee aka Phoenix616 (mail@moep.tv)
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
import tv.moep.discord.bot.Permission;

public class DiscordSender implements CommandSender {
    private final Message message;

    public DiscordSender(Message message) {
        this.message = message;
    }

    @Override
    public void sendMessage(String message) {
        this.message.getChannel().sendMessage(message);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        switch (permission) {
            case USER:
                return true;
            case ADMIN:
                if (message.getUserAuthor().isPresent()) {
                    if (message.getServer().isPresent()) {
                        return message.getServer().get().isAdmin(message.getUserAuthor().get());
                    } else {
                        return true;
                    }
                }
            case OWNER:
                if (message.getUserAuthor().isPresent()) {
                    if (message.getServer().isPresent()) {
                        return message.getServer().get().isOwner(message.getUserAuthor().get());
                    } else {
                        return true;
                    }
                }
            case OPERATOR:
                return message.getUserAuthor().get().isBotOwner();
        }
        return false;
    }
}
