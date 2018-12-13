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

import org.javacord.api.entity.permission.PermissionType;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.commands.MessageReaction;

public class MessageManager {
    public MessageManager(MoepsBot moepsBot) {
        moepsBot.getDiscordApi().addReactionAddListener(event -> {
            if (event.getUser().isYourself()) {
                return;
            }

            if (event.getEmoji().equalsEmoji(MessageReaction.REMOVE)) {
                if (event.getMessageAuthor().isPresent() && (event.getMessageAuthor().get().isYourself() || event.getMessageAuthor().get().isUser())) {
                    if (!event.getServer().isPresent()
                            || event.getMessageAuthor().get().isUser()
                            || event.getServer().get().hasAnyPermission(event.getUser(), PermissionType.MANAGE_MESSAGES)) {
                        event.getMessage().get().delete();
                    } else {
                        event.getMessage()
                                .map(m -> m.getEmbeds().isEmpty() ? null : m.getEmbeds().get(0))
                                .map(e -> e.getFooter().orElse(null))
                                .map(f -> f.getText().orElse(null))
                                .filter(f -> f.endsWith(event.getUser().getDiscriminatedName()))
                                .ifPresent(f -> event.getMessage().get().delete());
                    }
                }
            }
        });
    }
}
