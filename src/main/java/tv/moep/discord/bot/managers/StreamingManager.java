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

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.common.events.channel.ChannelGoLiveEvent;
import com.github.twitch4j.common.events.channel.ChannelGoOfflineEvent;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.ServerVoiceChannel;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class StreamingManager extends Manager {

    private final boolean markChannel;
    private final String markerPrefix;
    private final String markerSuffix;
    private TwitchClient twitchClient = null;

    private Map<String, String> listeners = new HashMap<>();

    public StreamingManager(MoepsBot moepsBot) {
        super(moepsBot, "streaming");

        if (getConfig().hasPath("listener")) {
            Config config = getConfig().getConfig("listener");
            if (!config.isEmpty()) {
                twitchClient = TwitchClientBuilder.builder().withEnableHelix(true).build();
                for (Map.Entry<String, ConfigValue> entry : config.root().entrySet()) {
                    if (entry.getValue().valueType() == ConfigValueType.STRING) {
                        String twitchName = (String) entry.getValue().unwrapped();
                        listeners.put(twitchName.toLowerCase(), entry.getKey());
                        twitchClient.getClientHelper().enableStreamEventListener(twitchName);
                    }
                }

                if (listeners.size() > 0) {
                    twitchClient.getEventManager().onEvent(ChannelGoLiveEvent.class).subscribe(event -> {
                        if (listeners.containsKey(event.getChannel().getName().toLowerCase())) {
                            String discordId = listeners.get(event.getChannel().getName().toLowerCase());
                            User user = getMoepsBot().getUser(discordId);
                            if (!user.getActivity().isPresent() || user.getActivity().get().getType() != ActivityType.STREAMING) {
                                onLive(user, discordId, "https://twitch.tv/" + event.getChannel().getName(), event.getTitle());
                            }
                        }
                    });
                    twitchClient.getEventManager().onEvent(ChannelGoOfflineEvent.class).subscribe(event -> {
                        if (listeners.containsKey(event.getChannel().getName().toLowerCase())) {
                            String discordId = listeners.get(event.getChannel().getName().toLowerCase());
                            User user = getMoepsBot().getUser(discordId);
                            onOffline(user, discordId, "https://twitch.tv/" + event.getChannel().getName());
                        }
                    });
                }
            }
        }


        markChannel = getConfig().getBoolean("streaming-marker.enabled");
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
            if (event.getNewActivity().isPresent() && event.getNewActivity().get().getType() == ActivityType.STREAMING) {
                onLive(event.getUser(), event.getUser().getName(), event.getNewActivity().get().getStreamingUrl().orElse(null), event.getNewActivity().get().getName() + " " + event.getNewActivity().get().getDetails().orElse(""));
            }
            if (event.getOldActivity().isPresent() && event.getOldActivity().get().getType() == ActivityType.STREAMING) {
                onOffline(event.getUser(), event.getUser().getName(), event.getOldActivity().get().getStreamingUrl().orElse(null));
            }
        });
    }

    private void onLive(User user, String rawName, String streamingUrl, String title) {
        if (user != null) {
            if (!user.isBot()) {
                log(Level.FINE, user.getDiscriminatedName() + " started streaming " + title + " at " + streamingUrl);
            }
        }else {
            log(Level.FINE, rawName + " stopped streaming at " + streamingUrl);
        }

        if (streamingUrl != null) {
            for (Server server : user != null ? user.getMutualServers() : getMoepsBot().getDiscordApi().getServers()) {
                Config serverConfig = getConfig(server);
                if (serverConfig != null && serverConfig.hasPath("announce.channel")) {
                    ServerTextChannel channel = Utils.getTextChannel(server, serverConfig.getString("announce.channel"));
                    if (channel == null) {
                        log(Level.WARNING, "Could not find announce channel " + serverConfig.getString("announce.channel"));
                        continue;
                    }

                    if (user != null && serverConfig.hasPath("announce.roles") && !Utils.hasRole(user, server, serverConfig.getStringList("announce.roles"))) {
                        continue;
                    }

                    String message = serverConfig.hasPath("announce.message") ? serverConfig.getString("announce.message") : "%name% is now live: %url%";
                    channel.sendMessage(Utils.replace(
                            message,
                            "username", user != null ? user.getDisplayName(server) : rawName,
                            "title", title,
                            "url", streamingUrl
                    ));
                }
            }
        }

        if (markChannel && user != null) {
            for (ServerVoiceChannel voiceChannel : user.getConnectedVoiceChannels()) {
                markChannelName(voiceChannel);
            }
        }
    }

    private void onOffline(User user, String rawName, String streamingUrl) {
        if (user != null) {
            user.getConnectedVoiceChannels().forEach(this::checkForMarkRemoval);

            if (!user.isBot()) {
                log(Level.FINE, user.getDiscriminatedName() + " stopped streaming at " + streamingUrl);
            }
        } else {
            log(Level.FINE, rawName + " stopped streaming at " + streamingUrl);
        }

        if (streamingUrl != null) {
            for (Server server : user != null ? user.getMutualServers() : getMoepsBot().getDiscordApi().getServers()) {
                Config serverConfig = getConfig(server);
                if (serverConfig != null && serverConfig.hasPath("announce.channel") && serverConfig.hasPath("announce.offline")) {
                    ServerTextChannel channel = Utils.getTextChannel(server, serverConfig.getString("announce.channel"));
                    if (channel == null) {
                        log(Level.WARNING, "Could not find announce channel " + serverConfig.getString("announce.channel"));
                        continue;
                    }

                    String newMessage = Utils.replace(
                            serverConfig.getString("announce.offline"),
                            "username", user != null ? user.getDisplayName(server) : rawName,
                            "url", streamingUrl
                    );

                    channel.getMessages(100).thenAccept(ms -> ms.forEach(m -> {
                        if (m.getAuthor().isYourself() && m.getContent().contains(streamingUrl)) {
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
