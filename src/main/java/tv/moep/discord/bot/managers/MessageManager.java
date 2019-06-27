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

import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.permission.PermissionType;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.commands.MessageReaction;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MessageManager extends Manager {
    public MessageManager(MoepsBot moepsBot) {
        super(moepsBot, "messages");
        moepsBot.getDiscordApi().addReactionAddListener(event -> {
            if (event.getUser().isYourself()) {
                return;
            }

            if (event.getEmoji().equalsEmoji(MessageReaction.REMOVE)
                    && (!event.getServerTextChannel().isPresent() || moepsBot.getTextChannelManager().has(event.getServerTextChannel().get(), "emojiRemoval"))) {
                Message message = event.getMessage().orElseGet(() -> {
                    try {
                        return event.getChannel().getMessageById(event.getMessageId()).get(10, TimeUnit.SECONDS);
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    } catch (TimeoutException ignored) {}
                    return null;
                });
                if (message != null) {
                    if ((!event.getUser().isBot() && event.getUser().equals(message.getUserAuthor().orElse(null)))
                            || (!event.getServer().isPresent() && (message.getAuthor().isYourself() || event.getUser().equals(message.getUserAuthor().orElse(null))))
                            || (!event.getUser().isBot() && event.getServer().isPresent() && event.getServer().get().hasAnyPermission(event.getUser(), PermissionType.MANAGE_MESSAGES))) {
                        message.delete();
                    } else {
                        if (!message.getEmbeds().isEmpty())
                            message.getEmbeds().get(0).getFooter()
                                .map(f -> f.getText().orElse(null))
                                .filter(f -> f.endsWith(event.getUser().getDiscriminatedName()))
                                .ifPresent(f -> message.delete());
                    }
                }
            }
        });
    }
}
