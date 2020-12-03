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
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.server.Server;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Utils;
import tv.moep.discord.bot.commands.DiscordSender;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class TextChannelManager extends Manager {

    public TextChannelManager(MoepsBot moepsBot) {
        super(moepsBot, "text-channel");

        for (Server server : moepsBot.getDiscordApi().getServers()) {
            Config serverConfig = getConfig(server);
            if (serverConfig != null) {
                boolean scanServerMessages = serverConfig.hasPath("deleteMessages")
                        || serverConfig.hasPath("deleteUserMessagess");
                for (ServerTextChannel channel : server.getTextChannels()) {
                    if (scanServerMessages
                            || serverConfig.hasPath(channel.getId() + ".deleteMessages")
                            || serverConfig.hasPath(channel.getId() + ".deleteUserMessages")) {
                        channel.getMessages(100).thenAccept(ms -> ms.forEach(m -> checkForDeletion(channel, m)));
                    }
                }
            }
        }

        moepsBot.getDiscordApi().addMessageCreateListener(event -> {
            if (!event.getServerTextChannel().isPresent()) {
                return;
            }

            if (has(event.getServerTextChannel().get(), "commands")) {
                String message = event.getMessageContent();
                if (moepsBot.getConfig().getBoolean("debug")) {
                    MoepsBot.log(Level.INFO, event.getMessage().getAuthor().getDiscriminatedName() + ": " + message);
                }
                String commandPrefix = getString(event.getServerTextChannel().get(), "command-prefix", "!");
                if (message.startsWith(commandPrefix)) {
                    moepsBot.runCommand(new DiscordSender(moepsBot, event.getMessage()), message.substring(commandPrefix.length()).trim());
                }
            }

            if (has(event.getServerTextChannel().get(), "pasteFiles")) {
                    Utils.uploadToPaste(event.getMessage(), event.getMessage().getAttachments()).thenAccept(paste -> {
                        if (paste != null) {
                            event.getServerTextChannel().get().sendMessage("Paste: <" + paste.getLink() + ">").thenAccept(m -> {
                                event.getMessage().addMessageDeleteListener(event1 -> {
                                    m.delete("Original paste message deleted");
                                    Utils.deletePaste(paste);
                                });
                            });
                        }
                    });
            }

            checkForDeletion(event.getServerTextChannel().get(), event.getMessage());
        });
    }

    private void checkForDeletion(ServerTextChannel channel, Message message) {
        int deleteDuration;
        if (hasPath(channel, "deleteUserMessages." + message.getAuthor().getId())) {
            deleteDuration = getInt(channel, "deleteUserMessages." + message.getAuthor().getId());
        } else {
            deleteDuration = getInt(channel, "deleteMessages");
        }
        if (deleteDuration >= 0) {
            long adjustedDeleteDuration = Math.max(0, deleteDuration - ChronoUnit.MINUTES.between(message.getLastEditTimestamp().orElse(message.getCreationTimestamp()), Instant.now()));
            log(Level.FINE, "Auto deleting message " + message.getId() + " from " + getChannelPath(channel) + " by " + message.getAuthor().getDiscriminatedName() + " in " + adjustedDeleteDuration + " Minutes!");

            Runnable run = () -> {
                message.delete("Auto deleted after " + adjustedDeleteDuration + " Minutes");
                log(Level.FINE, "Auto deleted message " + message.getId() + " from " + getChannelPath(channel) + " by " + message.getAuthor().getDiscriminatedName());
            };

            if (adjustedDeleteDuration > 0) {
                getMoepsBot().getScheduler().schedule(run, adjustedDeleteDuration, TimeUnit.MINUTES);
            } else {
                run.run();
            }
        }
    }

    private String getChannelPath(ServerTextChannel channel) {
        return channel.getServer().getName() + "/" + channel.getServer().getId() + " " + channel.getName() + "/" + channel.getId();
    }
}
