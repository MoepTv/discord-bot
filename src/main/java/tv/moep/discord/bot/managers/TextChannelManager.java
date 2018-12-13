package tv.moep.discord.bot.managers;

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

import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.commands.DiscordSender;

public class TextChannelManager {
    public TextChannelManager(MoepsBot moepsBot) {
        moepsBot.getDiscordApi().addMessageCreateListener(event -> {
            if (!event.isServerMessage()) {
                return;
            }

            if (event.getMessageContent().startsWith("!")) {
                moepsBot.runCommand(new DiscordSender(event.getMessage()), event.getReadableMessageContent().substring(1));
            }
        });
    }
}
