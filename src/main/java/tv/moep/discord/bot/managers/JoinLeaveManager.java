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

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.typesafe.config.Config;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Utils;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class JoinLeaveManager {

    private final Config config;

    private Multimap<Long, Map.Entry<String, Long>> joins = MultimapBuilder.hashKeys().arrayListValues().build();
    private Multimap<Long, Map.Entry<String, Long>> leaves = MultimapBuilder.hashKeys().arrayListValues().build();

    public JoinLeaveManager(MoepsBot moepsBot) {
        config = moepsBot.getConfig("join-leave");

        moepsBot.getDiscordApi().addServerMemberJoinListener(event -> {
            MoepsBot.log(Level.INFO, event.getUser().getDiscriminatedName() + " joined guild " + event.getServer().getName());
            joins.put(event.getServer().getId(), new AbstractMap.SimpleEntry<>(event.getUser().getDiscriminatedName(), System.currentTimeMillis()));
        });
        moepsBot.getDiscordApi().addServerMemberLeaveListener(event -> {
            MoepsBot.log(Level.INFO, event.getUser().getDiscriminatedName() + " left guild " + event.getServer().getName());
            leaves.put(event.getServer().getId(), new AbstractMap.SimpleEntry<>(event.getUser().getDiscriminatedName(), System.currentTimeMillis()));
            if (config.hasPath(event.getServer().getId() + ".leaves.channel")) {
                String channelStr = config.getString(event.getServer().getId() + ".leaves.channel");
                ServerTextChannel channel = event.getServer().getTextChannelById(channelStr).orElseGet(() -> {
                    List<ServerTextChannel> channels = event.getServer().getTextChannelsByNameIgnoreCase(channelStr);
                    return channels.isEmpty() ? null : channels.get(0);
                });
                if (channel != null) {
                    if (config.hasPath(event.getServer().getId() + ".message")) {
                        List<String> messages = config.getStringList(event.getServer().getId() + ".leaves.message");
                        if (messages.isEmpty() && config.hasPath(event.getServer().getId() + ".leaves.message")) {
                            messages.add(config.getString(event.getServer().getId() + ".leaves.message"));
                        }
                        if (!messages.isEmpty()) {
                            sendMessage(channel, Utils.replace(
                                    messages.get(MoepsBot.RANDOM.nextInt(messages.size())),
                                    "username", event.getUser().getDiscriminatedName(),
                                    "usermention", event.getUser().getNicknameMentionTag(),
                                    "nickname", event.getUser().getDisplayName(event.getServer()),
                                    "discriminator", event.getUser().getDiscriminator()
                            ));
                        }
                    } else {
                        sendMessage(channel, "*" + event.getUser().getMentionTag() + " left*");
                    }
                }
            }
        });
    }

    private void sendMessage(ServerTextChannel channel, String message) {
        channel.sendMessage(new EmbedBuilder()
                .setDescription(message)
                .setColor(Utils.getRandomColor())
        );
    }

    public Collection<Map.Entry<String, Long>> getJoins(Server server) {
        return joins.get(server.getId());
    }

    public Collection<Map.Entry<String, Long>> getLeaves(Server server) {
        return leaves.get(server.getId());
    }
}
