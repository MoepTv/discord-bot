package tv.moep.discord.bot.commands;

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

import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Permission;
import tv.moep.discord.bot.Utils;

import java.util.concurrent.CompletableFuture;

public class DiscordSender implements CommandSender<Message> {
    private final MoepsBot bot;
    private final Message message;

    public DiscordSender(MoepsBot bot, Message message) {
        this.bot = bot;
        this.message = message;
    }

    @Override
    public CompletableFuture<Message> sendNaturalMessage(String message) {
        return this.message.getChannel().sendMessage(message);
    }

    @Override
    public CompletableFuture<Message> sendMessage(String message) {
        EmbedBuilder embed = new EmbedBuilder()
                .setFooter("Answer to " + this.message.getAuthor().getDiscriminatedName())
                .setColor(Utils.getRandomColor());
        if (message.length() > 256) {
            embed.setDescription(message);
        } else {
            embed.setTitle(message);
        }
        CompletableFuture<Message> messageFuture = this.message.getChannel().sendMessage(embed);
        if (!(this.message.getChannel() instanceof ServerTextChannel)
                || bot.getTextChannelManager().has((ServerTextChannel) this.message.getChannel(), "emojiRemoval")) {
            messageFuture.thenAccept(m -> m.addReaction(MessageReaction.REMOVE));
        }
        return messageFuture;
    }

    @Override
    public CompletableFuture<Message> sendMessage(String title, String message) {
        CompletableFuture<Message> messageFuture = this.message.getChannel().sendMessage(new EmbedBuilder()
                .setAuthor(this.message.getApi().getYourself())
                .setTitle(title)
                .setDescription(message)
                .setFooter("Answer to " + this.message.getAuthor().getDiscriminatedName())
                .setColor(Utils.getRandomColor())
        );
        if (!(this.message.getChannel() instanceof ServerTextChannel)
                || bot.getTextChannelManager().has((ServerTextChannel) this.message.getChannel(), "emojiRemoval")) {
            messageFuture.thenAccept(m -> m.addReaction(MessageReaction.REMOVE));
        }
        return messageFuture;
    }

    @Override
    public boolean hasPermission(Permission permission) {
        switch (permission) {
            case USER:
                return true;
            case ADMIN:
                if (message.getUserAuthor().isPresent()
                        && message.getServer().isPresent()
                        && message.getServer().get().isAdmin(message.getUserAuthor().get())) {
                    return true;
                }
            case OWNER:
                if (message.getUserAuthor().isPresent()
                        && message.getServer().isPresent()
                        && message.getServer().get().isOwner(message.getUserAuthor().get())) {
                    return true;
                }
            case OPERATOR:
                return message.getUserAuthor().isPresent()
                        && (message.getUserAuthor().get().isBotOwner()
                                || bot.getConfig().getStringList("discord.operators").contains(message.getAuthor().getIdAsString()));
        }
        return false;
    }

    public Server getServer() {
        return message.getServer().orElse(null);
    }

    public User getUser() {
        return message.getUserAuthor().orElse(null);
    }

    @Override
    public void confirm() {
        if (this.message.getChannel() instanceof ServerTextChannel
                && bot.getTextChannelManager().has((ServerTextChannel) this.message.getChannel(), "emojiRemoval")) {
            message.addReactions(MessageReaction.REMOVE);
        } else {
            message.addReactions(MessageReaction.CONFIRM);
        }
    }

    @Override
    public String getName() {
        return message.getAuthor().getDisplayName();
    }

    public Message getMessage() {
        return message;
    }
}
