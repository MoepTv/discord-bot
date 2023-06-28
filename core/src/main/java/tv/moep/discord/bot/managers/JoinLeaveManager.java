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

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.typesafe.config.Config;
import org.javacord.api.entity.auditlog.AuditLog;
import org.javacord.api.entity.auditlog.AuditLogActionType;
import org.javacord.api.entity.auditlog.AuditLogEntry;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.server.member.ServerMemberLeaveEvent;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Utils;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public class JoinLeaveManager extends Manager {

    private Multimap<Long, Map.Entry<String, Long>> joins = MultimapBuilder.hashKeys().arrayListValues().build();
    private Multimap<Long, Map.Entry<String, Long>> leaves = MultimapBuilder.hashKeys().arrayListValues().build();

    public JoinLeaveManager(MoepsBot moepsBot) {
        super(moepsBot, "join-leave");

        moepsBot.getDiscordApi().addServerMemberJoinListener(event -> {
            log(Level.INFO, event.getUser().getDiscriminatedName() + " joined guild " + event.getServer().getName());
            joins.put(event.getServer().getId(), new AbstractMap.SimpleEntry<>(event.getUser().getDiscriminatedName(), System.currentTimeMillis()));
        });
        moepsBot.getDiscordApi().addServerMemberLeaveListener(event -> {
            log(Level.INFO, event.getUser().getDiscriminatedName() + " left guild " + event.getServer().getName());
            leaves.put(event.getServer().getId(), new AbstractMap.SimpleEntry<>(event.getUser().getDiscriminatedName(), System.currentTimeMillis()));
            Config serverConfig = getConfig(event.getServer());
            if (serverConfig != null && serverConfig.hasPath("leaves.channel") && (!has(event.getServer(), "leaves.ignore-kicks") || !isKick(event))) {
                ServerTextChannel channel = Utils.getTextChannel(event.getServer(), serverConfig.getString("leaves.channel"));
                if (channel != null) {
                    if (serverConfig.hasPath("leaves.message")) {
                        List<String> messages = serverConfig.getStringList("leaves.message");
                        if (messages.isEmpty()) {
                            messages.add(serverConfig.getString("leaves.message"));
                        }
                        if (!messages.isEmpty()) {
                            channel.sendMessage(Utils.replace(
                                    messages.get(MoepsBot.RANDOM.nextInt(messages.size())),
                                    "name", event.getUser().getName(),
                                    "username", event.getUser().getDiscriminatedName(),
                                    "usermention", event.getUser().getNicknameMentionTag(),
                                    "nickname", event.getUser().getDisplayName(event.getServer()),
                                    "discriminator", event.getUser().getDiscriminator()
                            ));
                        }
                    } else {
                        channel.sendMessage("*" + event.getUser().getMentionTag() + " left*");
                    }
                }
            }
        });
    }

    private boolean isKick(ServerMemberLeaveEvent event) {
        if (event.getServer().canYouViewAuditLog()) {
            try {
                AuditLog auditLog = event.getServer().getAuditLog(1, AuditLogActionType.MEMBER_KICK).get();
                for (AuditLogEntry entry : auditLog.getEntries()) {
                    if (entry.getTarget().isPresent() && entry.getTarget().get().asUser().get().equals(event.getUser())) {
                        return true;
                    }
                }
            } catch (InterruptedException | ExecutionException ignored) {}
        }
        return false;
    }

    public Collection<Map.Entry<String, Long>> getJoins(Server server) {
        return joins.get(server.getId());
    }

    public Collection<Map.Entry<String, Long>> getLeaves(Server server) {
        return leaves.get(server.getId());
    }
}
