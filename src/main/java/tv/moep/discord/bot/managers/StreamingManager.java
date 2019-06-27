package tv.moep.discord.bot.managers;

/*
 * MoepsBot
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

import com.typesafe.config.Config;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.ServerVoiceChannel;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Utils;

import java.util.Optional;
import java.util.logging.Level;

public class StreamingManager extends Manager {

    private final String markerPrefix;
    private final String markerSuffix;

    public StreamingManager(MoepsBot moepsBot) {
        super(moepsBot, "streaming");
        boolean markChannel = getConfig().getBoolean("streaming-marker.enabled");
        markerPrefix = getConfig().getString("streaming-marker.prefix");
        markerSuffix = getConfig().getString("streaming-marker.suffix");

        moepsBot.getDiscordApi().addServerVoiceChannelMemberJoinListener(event -> {
            User user = event.getUser();
            if (user.getActivity().isPresent() && user.getActivity().get().getType() == ActivityType.STREAMING) {
                if (markChannel) {
                    markChannelName(event.getChannel());
                }
            }
        });

        moepsBot.getDiscordApi().addServerVoiceChannelMemberLeaveListener(event -> {
            User user = event.getUser();
            if (user.getActivity().isPresent() && user.getActivity().get().getType() == ActivityType.STREAMING) {
                if (markChannel) {
                    markChannelName(event.getChannel());
                }
            }
        });

        moepsBot.getDiscordApi().addUserChangeActivityListener(event -> {
            User user = event.getUser();
            if (event.getNewActivity().isPresent() && event.getNewActivity().get().getType() == ActivityType.STREAMING) {
                Optional<String> streamingUrl = event.getNewActivity().get().getStreamingUrl();
                if (!user.isBot()) {
                    log(Level.FINE, user.getDiscriminatedName() + " started streaming " + event.getNewActivity().get().getName() + " at " + streamingUrl.orElse("somewhere?"));
                }

                if (streamingUrl.isPresent()) {
                    for (Server server : user.getMutualServers()) {
                        Config serverConfig = getConfig(server);
                        if (serverConfig != null && serverConfig.hasPath("announce.channel")) {
                            ServerTextChannel channel = Utils.getTextChannel(server, serverConfig.getString("announce.channel"));
                            if (channel == null) {
                                log(Level.WARNING, "Could not find channel ");
                                continue;
                            }

                            if (serverConfig.hasPath("announce.roles") && !Utils.hasRole(user, server, serverConfig.getStringList("announce.roles"))) {
                                continue;
                            }

                            String message = serverConfig.hasPath("announce.message") ? serverConfig.getString("announce.message") : "%name% is now live: %url%";
                            channel.sendMessage(Utils.replace(
                                    message,
                                    "username", event.getUser().getDisplayName(server),
                                    "game", event.getNewActivity().get().getName(),
                                    "url", streamingUrl.get()
                            ));
                        }
                    }
                }

                if (markChannel && (!event.getOldActivity().isPresent() || event.getOldActivity().get().getType() != ActivityType.STREAMING)) {
                    for (ServerVoiceChannel voiceChannel : user.getConnectedVoiceChannels()) {
                        markChannelName(voiceChannel);
                    }
                }
            }
            if (event.getOldActivity().isPresent() && event.getOldActivity().get().getType() == ActivityType.STREAMING) {
                user.getConnectedVoiceChannels().forEach(this::checkForMarkRemoval);

                Optional<String> streamingUrl = event.getOldActivity().get().getStreamingUrl();
                if (!user.isBot()) {
                    log(Level.FINE, user.getDiscriminatedName() + " stopped streaming " + user.getActivity().get().getName() + " at " + streamingUrl.orElse("somewhere?"));
                }

                if (streamingUrl.isPresent()) {
                    for (Server server : user.getMutualServers()) {
                        Config serverConfig = getConfig(server);
                        if (serverConfig != null && serverConfig.hasPath("announce.channel") && serverConfig.hasPath("announce.offline")) {
                            ServerTextChannel channel = Utils.getTextChannel(server, serverConfig.getString("announce.channel"));
                            if (channel == null) {
                                log(Level.WARNING, "Could not find channel ");
                                continue;
                            }

                            String message = Utils.replace(
                                    serverConfig.hasPath("announce.message") ? serverConfig.getString("announce.message") : "%name% is now live: %url%",
                                    "username", event.getUser().getDisplayName(server),
                                    "game", event.getOldActivity().get().getName(),
                                    "url", streamingUrl.get()
                            );
                            String newMessage = Utils.replace(
                                    serverConfig.getString("announce.offline"),
                                    "username", event.getUser().getDisplayName(server),
                                    "game", event.getOldActivity().get().getName(),
                                    "url", streamingUrl.get()
                            );

                            channel.getMessages(100).thenAccept(ms -> ms.forEach(m -> {
                                if (m.getAuthor().isYourself() && (m.getContent().equalsIgnoreCase(message) || m.getContent().contains(streamingUrl.get()))) {
                                    if (newMessage.equalsIgnoreCase("delete")) {
                                        m.delete("Stream is now offline");
                                    } else {
                                        m.edit(newMessage);
                                    }
                                }
                            }));
                        }
                    }
                }
            }
        });
    }

    private void checkForMarkRemoval(ServerVoiceChannel voiceChannel) {
        for (User user : voiceChannel.getConnectedUsers()) {
            if (user.getActivity().isPresent() && user.getActivity().get().getType() == ActivityType.STREAMING) {
                return;
            }
        }
        unmarkChannelName(voiceChannel);
    }

    private void unmarkChannelName(ServerVoiceChannel voiceChannel) {
        if (isMarked(voiceChannel)) {
            voiceChannel.updateName(getUnmarkedName(voiceChannel));
        }
    }

    private String getUnmarkedName(ServerVoiceChannel voiceChannel) {
        if (isMarked(voiceChannel)) {
            return voiceChannel.getName().substring(markerPrefix.length(), voiceChannel.getName().length() - markerSuffix.length());
        }
        return voiceChannel.getName();
    }

    private void markChannelName(ServerVoiceChannel voiceChannel) {
        if (!isMarked(voiceChannel)) {
            voiceChannel.updateName(markerPrefix + voiceChannel.getName() + markerSuffix);
        }
    }

    private boolean isMarked(ServerVoiceChannel channel) {
        return channel.getName().startsWith(markerPrefix) && channel.getName().endsWith(markerSuffix);
    }
}
