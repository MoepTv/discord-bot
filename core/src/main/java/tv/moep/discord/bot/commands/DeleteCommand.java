package tv.moep.discord.bot.commands;

/*
 * MoepTv - core
 * Copyright (C) 2023 Max Lee aka Phoenix616 (max@themoep.de)
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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.javacord.api.entity.message.Message;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Permission;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DeleteCommand extends Command<DiscordSender>  {
    private final MoepsBot bot;
    private final Cache<String, Set<String>> deletionQueue = Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();

    public DeleteCommand(MoepsBot bot) {
        super("confirm|<author> <from> <to>", Permission.ADMIN);
        this.bot = bot;
    }

    @Override
    public boolean execute(DiscordSender sender, String[] args) {
        sender.removeSource();
        if (sender.getChannel() == null) {
            sender.sendReply("Please execute this command in a channel!");
            return true;
        }

        if (args.length < 1) {
            return false;
        }

        if ("confirm".equalsIgnoreCase(args[0])) {
            Set<String> messages = deletionQueue.getIfPresent(sender.getName());
            if (messages != null) {
                sender.confirm();
                sender.sendReply("Starting to delete " + messages.size() + " messages!");
                Message.delete(bot.getDiscordApi(), sender.getChannel().getIdAsString(), messages.toArray(new String[0])).whenComplete((v, e) -> {
                    if (e != null) {
                        sender.sendMessage("Error while trying to delete " + messages.size() + "! " + e.getMessage());
                        e.printStackTrace();
                    } else {
                        deletionQueue.invalidate(sender.getName());
                        sender.sendReply("Deleted " + messages.size() + " messages!");
                    }
                });
            }
            return true;
        }

        if (args.length < 3) {
            return false;
        }

        try {
            long authorId = Long.parseLong(args[0]);

            sender.getChannel().getMessagesBetween(Long.parseLong(args[1]), Long.parseLong(args[2])).whenComplete((ms, e) -> {
                sender.confirm();
                if (e != null) {
                    sender.sendReply(e.getMessage());
                } else {
                    Set<String> messages = new LinkedHashSet<>();
                    for (Message message : ms) {
                        if (message.getAuthor().getId() == authorId) {
                            messages.add(message.getIdAsString());
                        }
                    }
                    deletionQueue.put(sender.getName(), messages);
                    sender.sendReply("Found " + messages.size() + " messages by user " + authorId + "!", "Run '/delete confirm' to remove them.");
                }
            });
        } catch (NumberFormatException e) {
            sender.sendReply(e.getMessage());
        }
        return true;
    }
}
