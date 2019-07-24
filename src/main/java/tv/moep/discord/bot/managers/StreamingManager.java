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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.CredentialManagerBuilder;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.github.twitch4j.common.events.channel.ChannelChangeGameEvent;
import com.github.twitch4j.common.events.channel.ChannelChangeTitleEvent;
import com.github.twitch4j.common.events.channel.ChannelGoLiveEvent;
import com.github.twitch4j.common.events.channel.ChannelGoOfflineEvent;
import com.github.twitch4j.helix.domain.Game;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.ServerVoiceChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Permission;
import tv.moep.discord.bot.Utils;
import tv.moep.discord.bot.commands.Command;
import tv.moep.discord.bot.commands.DiscordSender;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class StreamingManager extends Manager {

    private boolean markChannel;
    private String markerPrefix;
    private String markerSuffix;
    private TwitchClient twitchClient = null;

    private Map<String, String> listeners = new HashMap<>();

    private LoadingCache<Long, String> gameCache = Caffeine.newBuilder().build((aLong) -> {
            List<Game> games = twitchClient.getHelix().getGames(Collections.singletonList(String.valueOf(aLong)), null).execute().getGames();
            return games.isEmpty() ? null : games.iterator().next().getName();
    });

    private Map<String, StreamData> streams = new HashMap<>();

    public StreamingManager(MoepsBot moepsBot) {
        super(moepsBot, "streaming");
        Command<DiscordSender> streamCommand = moepsBot.registerCommand("stream [list|setoffline [<user>]]", Permission.ADMIN, (sender, args) -> false);
        streamCommand.registerSubCommand("list", ((sender, args) -> {
            if (streams.size() > 0) {
                sender.sendMessage("Live channels:\n" + streams.entrySet().stream()
                        .map(e -> e.getKey() + ": " + e.getValue().getGame() + " - " + e.getValue().getTitle() + " - <" + e.getValue().getUrl() + ">")
                        .collect(Collectors.joining("\n")));
            } else {
                sender.sendMessage("No stream live?");
            }
            return true;
        }));
        streamCommand.registerSubCommand("setoffline [<user>]", (sender, args) -> {
            if (args.length == 0) {
                StreamData streamData = getStreamData(sender.getUser());
                if (streamData == null) {
                    sender.sendMessage("You are not online?");
                    return true;
                }

                onOffline(sender.getUser(), sender.getUser().getDiscriminatedName());
                sender.sendMessage("Set you to offline! Was streaming " + streamData.getGame() + " - " + streamData.getTitle() + " - <" + streamData.getUrl() + ">");
                return true;
            } else if (args.length == 1) {
                User user = moepsBot.getUser(args[0]);
                if (user == null || !user.getMutualServers().contains(sender.getServer())) {
                    sender.sendMessage("The user `" + args[0] + "` wasn't found?");
                    return true;
                }

                StreamData streamData = getStreamData(user);
                if (streamData == null) {
                    sender.sendMessage(user.getDiscriminatedName() + " is not online?");
                    return true;
                }

                onOffline(user, user.getDiscriminatedName());
                sender.sendMessage("Set " + user.getDiscriminatedName() + " to offline! Was streaming " + streamData.getGame() + " - " + streamData.getTitle() + " - <" + streamData.getUrl() + ">");
                return true;
            }
            return false;
        });
    }

    public void reload() {
        if (getConfig().hasPath("twitch.listener")) {
            Config config = getConfig().getConfig("twitch.listener");
            if (!config.isEmpty()) {
                boolean setupTwitchClient = twitchClient == null;
                if (setupTwitchClient) {
                    TwitchClientBuilder twitchClientBuilder = TwitchClientBuilder.builder().withEnableHelix(true);
                    if (getConfig().hasPath("twitch.client.id") && getConfig().hasPath("twitch.client.secret")
                            && !getConfig().getString("twitch.client.id").isEmpty() && !getConfig().getString("twitch.client.secret").isEmpty()) {
                        CredentialManager credentialManager = CredentialManagerBuilder.builder().build();
                        credentialManager.registerIdentityProvider(new TwitchIdentityProvider(getConfig().getString("twitch.client.id"), getConfig().getString("twitch.client.secret"), ""));
                        twitchClientBuilder.withCredentialManager(credentialManager);
                    }
                    twitchClient = twitchClientBuilder.build();
                }
                Map<String, String> newListeners = new HashMap<>();
                for (Map.Entry<String, ConfigValue> entry : config.root().entrySet()) {
                    if (entry.getValue().valueType() == ConfigValueType.STRING) {
                        String twitchName = (String) entry.getValue().unwrapped();
                        newListeners.put(twitchName.toLowerCase(), entry.getKey());
                        if (!listeners.containsKey(twitchName.toLowerCase())) {
                            twitchClient.getClientHelper().enableStreamEventListener(twitchName);
                        }
                    }
                }
                listeners = newListeners;

                if (setupTwitchClient && listeners.size() > 0) {
                    twitchClient.getEventManager().onEvent(ChannelGoLiveEvent.class).subscribe(event -> {
                        if (listeners.containsKey(event.getChannel().getName().toLowerCase())) {
                            String discordId = listeners.get(event.getChannel().getName().toLowerCase());
                            if (!streams.containsKey(discordId)) {
                                User user = getMoepsBot().getUser(discordId);
                                onLive(user, discordId, "https://twitch.tv/" + event.getChannel().getName(), getGame(event.getGameId()), event.getTitle());
                            }
                        }
                    });
                    twitchClient.getEventManager().onEvent(ChannelChangeGameEvent.class).subscribe(event -> {
                        if (listeners.containsKey(event.getChannel().getName().toLowerCase())) {
                            String discordId = listeners.get(event.getChannel().getName().toLowerCase());
                            if (streams.containsKey(discordId)) {
                                User user = getMoepsBot().getUser(discordId);
                                updateGame(user, discordId, getGame(event.getGameId()));
                            }
                        }
                    });
                    twitchClient.getEventManager().onEvent(ChannelChangeTitleEvent.class).subscribe(event -> {
                        if (listeners.containsKey(event.getChannel().getName().toLowerCase())) {
                            String discordId = listeners.get(event.getChannel().getName().toLowerCase());
                            if (streams.containsKey(discordId)) {
                                User user = getMoepsBot().getUser(discordId);
                                updateTitle(user, discordId, event.getTitle());
                            }
                        }
                    });
                    twitchClient.getEventManager().onEvent(ChannelGoOfflineEvent.class).subscribe(event -> {
                        if (listeners.containsKey(event.getChannel().getName().toLowerCase())) {
                            String discordId = listeners.get(event.getChannel().getName().toLowerCase());
                            User user = getMoepsBot().getUser(discordId);
                            onOffline(user, discordId);
                        }
                    });
                }
            }
        }


        markChannel = getConfig().getBoolean("streaming-marker.enabled");
        markerPrefix = getConfig().getString("streaming-marker.prefix");
        markerSuffix = getConfig().getString("streaming-marker.suffix");

        getMoepsBot().getDiscordApi().addServerVoiceChannelMemberJoinListener(event -> {
            if (markChannel && isStreaming(event.getUser())) {
                markChannelName(event.getChannel());
            }
        });

        getMoepsBot().getDiscordApi().addServerVoiceChannelMemberLeaveListener(event -> {
            if (markChannel && isStreaming(event.getUser())) {
                checkForMarkRemoval(event.getChannel());
            }
        });

        getMoepsBot().getDiscordApi().addUserChangeActivityListener(event -> {
            if (event.getNewActivity().isPresent() && event.getNewActivity().get().getType() == ActivityType.STREAMING) {
                onLive(
                        event.getUser(),
                        event.getUser().getDiscriminatedName(),
                        event.getNewActivity().get().getStreamingUrl().orElse(null),
                        event.getNewActivity().get().getName(),
                        event.getNewActivity().get().getDetails().orElse("")
                );
            } else if (event.getOldActivity().isPresent() && event.getOldActivity().get().getType() == ActivityType.STREAMING) {
                onOffline(event.getUser(), event.getUser().getDiscriminatedName());
            }
        });
    }

    private boolean isStreaming(String discordId) {
        return streams.containsKey(discordId.toLowerCase());
    }

    private boolean isStreaming(User user) {
        return isStreaming(user.getDiscriminatedName());
    }

    private StreamData getStreamData(User user) {
        return getStreamData(user.getDiscriminatedName());
    }

    private StreamData getStreamData(String discordId) {
        return streams.get(discordId.toLowerCase());
    }

    private void updateTitle(User user, String rawName, String title) {
        StreamData streamData = getStreamData(rawName);
        streamData.setTitle(title);

        updateNotification(user, rawName, streamData);
    }

    private void updateGame(User user, String rawName, String game) {
        StreamData streamData = getStreamData(rawName);
        streamData.setGame(game);
        updateNotification(user, rawName, streamData);
    }

    private void updateNotification(User user, String rawName, StreamData streamData) {
        for (Server server : user != null ? user.getMutualServers() : getMoepsBot().getDiscordApi().getServers()) {
            Config serverConfig = getConfig(server);
            String newMessage = Utils.replace(
                    serverConfig.hasPath("announce.message") ? serverConfig.getString("announce.message") : "%name% is now live: %url%",
                    "username", user != null ? user.getDisplayName(server) : rawName,
                    "game", streamData.getGame(),
                    "title", streamData.getTitle(),
                    "url", streamData.getUrl()
            );
            updateNotificationMessage(server, streamData.getUrl(), newMessage);
        }
    }

    private String getGame(Long gameId) {
        return gameCache.get(gameId);
    }

    private void onLive(User user, String rawName, String streamingUrl, String game, String title) {
        StreamData streamData;
        if (user != null) {
            streamData = getStreamData(user);
            if (!user.isBot()) {
                log(Level.FINE, user.getDiscriminatedName() + " started streaming " + game + " - " + title + " at " + streamingUrl);
            }
        } else {
            streamData = getStreamData(rawName);
            log(Level.FINE, rawName + " started streaming at " + streamingUrl);
        }
        if (streamData == null) {
            streams.put(rawName.toLowerCase(), new StreamData(streamingUrl, game, title));
        } else {
            streamData.setGame(game);
            streamData.setTitle(title);
        }

        if (streamingUrl != null) {
            for (Server server : user != null ? user.getMutualServers() : getMoepsBot().getDiscordApi().getServers()) {
                Config serverConfig = getConfig(server);
                if (serverConfig != null && serverConfig.hasPath("announce.channel")) {
                    if (user != null && serverConfig.hasPath("announce.roles") && !Utils.hasRole(user, server, serverConfig.getStringList("announce.roles"))) {
                        continue;
                    }

                    String message = Utils.replace(
                            serverConfig.hasPath("announce.message") ? serverConfig.getString("announce.message") : "%name% is now live: %url%",
                            "username", user != null ? user.getDisplayName(server) : rawName,
                            "game", game,
                            "title", title,
                            "url", streamingUrl
                    );
                    if (streamData == null) {
                        ServerTextChannel channel = Utils.getTextChannel(server, serverConfig.getString("announce.channel"));
                        if (channel != null) {
                            channel.sendMessage(message);
                        } else {
                            log(Level.WARNING, "Could not find announce channel " + serverConfig.getString("announce.channel") + " on server " + server.getName() + "/" + server.getId());
                        }
                    } else {
                        updateNotificationMessage(server, streamingUrl, message);
                    }
                }
            }
        }

        if (markChannel && user != null) {
            for (ServerVoiceChannel voiceChannel : user.getConnectedVoiceChannels()) {
                markChannelName(voiceChannel);
            }
        }
    }

    private void onOffline(User user, String rawName) {
        StreamData streamData = getStreamData(rawName);
        streams.remove(rawName.toLowerCase());
        if (user != null) {
            user.getConnectedVoiceChannels().forEach(this::checkForMarkRemoval);

            if (!user.isBot()) {
                log(Level.FINE, user.getDiscriminatedName() + " stopped streaming " + (streamData != null ? streamData.getGame() + " " + streamData.getTitle() + " at " + streamData.getUrl() : ""));
            }
        } else {
            log(Level.FINE, rawName + " stopped streaming " + (streamData != null ? streamData.getGame() + " " + streamData.getTitle() + " at " + streamData.getUrl() : ""));
        }

        if (streamData != null && streamData.getUrl() != null) {
            for (Server server : user != null ? user.getMutualServers() : getMoepsBot().getDiscordApi().getServers()) {
                Config serverConfig = getConfig(server);
                if (serverConfig != null && serverConfig.hasPath("announce.channel") && serverConfig.hasPath("announce.offline")) {
                    String newMessage = Utils.replace(
                            serverConfig.getString("announce.offline"),
                            "username", user != null ? user.getDisplayName(server) : rawName,
                            "game", streamData.getGame(),
                            "title", streamData.getTitle(),
                            "url", streamData.getUrl()
                    );
                    updateNotificationMessage(server, streamData.getUrl(), newMessage);
                }
            }
        }
    }

    private void updateNotificationMessage(Server server, String streamingUrl, String message) {
        Config serverConfig = getConfig(server);
        ServerTextChannel channel = Utils.getTextChannel(server, serverConfig.getString("announce.channel"));
        if (channel == null) {
            log(Level.WARNING, "Could not find announce channel " + serverConfig.getString("announce.channel") + " on server " + server.getName() + "/" + server.getId());
            return;
        }
        channel.getMessages(100).thenAccept(ms -> {
            for (Message m : ms.descendingSet()) {
                if (m.getAuthor().isYourself() && m.getContent().contains(streamingUrl)) {
                    if (message.equalsIgnoreCase("delete")) {
                        m.delete("Stream is now offline");
                    } else {
                        m.edit(message);
                    }
                    break;
                }
            }
        });

    }

    private void checkForMarkRemoval(ServerVoiceChannel voiceChannel) {
        for (User user : voiceChannel.getConnectedUsers()) {
            if (isStreaming(user)) {
                return;
            }
        }
        unmarkChannelName(voiceChannel);
    }

    private void unmarkChannelName(ServerVoiceChannel voiceChannel) {
        if (isMarked(voiceChannel)) {
            voiceChannel.updateName(getUnmarkedName(voiceChannel));
            log(Level.FINE, "Removed stream marker from " + voiceChannel.getName());
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
            log(Level.FINE, "Added stream marker to " + voiceChannel.getName());
        }
    }

    private boolean isMarked(ServerVoiceChannel channel) {
        return channel.getName().startsWith(markerPrefix) && channel.getName().endsWith(markerSuffix);
    }

    @Data
    @AllArgsConstructor
    private class StreamData {
        private final String url;
        private String game;
        private String title;
    }
}
