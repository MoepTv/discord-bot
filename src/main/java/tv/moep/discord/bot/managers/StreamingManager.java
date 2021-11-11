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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.CredentialManagerBuilder;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.simple.SimpleEventHandler;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.github.twitch4j.events.ChannelChangeGameEvent;
import com.github.twitch4j.events.ChannelChangeTitleEvent;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import com.github.twitch4j.helix.domain.Game;
import com.github.twitch4j.helix.domain.Video;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import org.apache.commons.lang.WordUtils;
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

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class StreamingManager extends Manager {

    private boolean markChannel;
    private String markerPrefix;
    private String markerSuffix;
    private String oAuthToken = "";
    private String username = null;
    private TwitchClient twitchClient = null;

    private BiMap<String, String> listeners = HashBiMap.create();

    private LoadingCache<String, String> gameCache = Caffeine.newBuilder().build((gameId) -> {
            List<Game> games = twitchClient.getHelix().getGames(oAuthToken, Collections.singletonList(gameId), null).execute().getGames();
            return games.isEmpty() ? null : games.iterator().next().getName();
    });

    private Map<String, StreamData> streams = new HashMap<>();
    private Map<Long, ServerData> serverData = new HashMap<>();

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
        getMoepsBot().getDiscordApi().getServers().forEach(this::getServerData);
        loadConfig();
        if (getConfig().hasPath("twitch.listener")) {
            Config config = getConfig().getConfig("twitch.listener");
            if (!config.isEmpty()) {
                boolean setupTwitchClient = twitchClient == null;
                if (setupTwitchClient) {
                    TwitchClientBuilder twitchClientBuilder = TwitchClientBuilder.builder()
                            .withEnableHelix(true);
                    String redirectUrl = getConfig().hasPath("twitch.client.redirecturl") ? getConfig().getString("twitch.client.redirecturl") : "";
                    if (getConfig().hasPath("twitch.client.oauth")) {
                        oAuthToken = getConfig().getString("twitch.client.oauth");
                    }
                    if (getConfig().hasPath("twitch.client.id") && getConfig().hasPath("twitch.client.secret")
                            && !getConfig().getString("twitch.client.id").isEmpty() && !getConfig().getString("twitch.client.secret").isEmpty()) {
                        if (oAuthToken.isEmpty()) {
                            if (redirectUrl.isEmpty()) {
                                log(Level.SEVERE, "Redirect URL is empty!");
                            } else {
                                log(Level.SEVERE, "OAuth token not set in config! Please get it from https://id.twitch.tv/oauth2/authorize?client_id=" + getConfig().getString("twitch.client.id") + "&redirect_uri=" + redirectUrl + "&response_type=token&scope=");
                            }
                            return;
                        }
                        CredentialManager credentialManager = CredentialManagerBuilder.builder().build();
                        credentialManager.registerIdentityProvider(new TwitchIdentityProvider(getConfig().getString("twitch.client.id"), getConfig().getString("twitch.client.secret"), redirectUrl));
                        twitchClientBuilder = twitchClientBuilder
                                .withCredentialManager(credentialManager)
                                .withDefaultAuthToken(new OAuth2Credential("twitch", oAuthToken));
                    } else {
                        log(Level.SEVERE, "No client ID/secret provided!");
                        return;
                    }
                    if (getConfig().hasPath("twitch.client.username")) {
                        twitchClientBuilder = twitchClientBuilder
                                .withEnableChat(true)
                                .withChatAccount(twitchClientBuilder.getDefaultAuthToken());
                        username = getConfig().getString("twitch.client.username");
                    }
                    twitchClient = twitchClientBuilder.build();
                    // Test client
                    try {
                        List<com.github.twitch4j.helix.domain.User> userList = twitchClient.getHelix().getUsers(oAuthToken, null, Collections.singletonList("MoepsBot")).execute().getUsers();
                        if (userList.isEmpty()) {
                            log(Level.WARNING, "Unable to query API?");
                        }
                    } catch (Exception e) {
                        log(Level.SEVERE, "OAuth token might be invalid! Please get it from https://id.twitch.tv/oauth2/authorize?client_id=" + getConfig().getString("twitch.client.id") + "&redirect_uri=" + redirectUrl + "&response_type=token&scope=" + (username != null ? "chat:edit%20chat:read%20whispers:read%20whispers:edit" : ""));
                        throw e;
                    }
                    try {
                        if (username != null) {
                            twitchClient.getChat().joinChannel("MoepsBot");
                            twitchClient.getChat().sendMessage(username, "Testing " + MoepsBot.NAME + " " + MoepsBot.VERSION + " Twitch chat setup!");
                        }
                    } catch (Exception e) {
                        log(Level.SEVERE, "Unable to use chat functionality! Please make sure you've granted the required rights! https://id.twitch.tv/oauth2/authorize?client_id=" + getConfig().getString("twitch.client.id") + "&redirect_uri=" + redirectUrl + "&response_type=token&scope=chat:edit%20chat:read%20whispers:read%20whispers:edit");
                        log(Level.SEVERE, e.getMessage());
                        username = null;
                    }
                }
                BiMap<String, String> newListeners = HashBiMap.create();
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
                    SimpleEventHandler eventHandler = twitchClient.getEventManager().getEventHandler(SimpleEventHandler.class);
                    eventHandler.onEvent(ChannelGoLiveEvent.class, event -> {
                        if (listeners.containsKey(event.getChannel().getName().toLowerCase())) {
                            String discordId = listeners.get(event.getChannel().getName().toLowerCase());
                            if (!streams.containsKey(discordId.toLowerCase())) {
                                User user = getMoepsBot().getUser(discordId);
                                onLive(user, discordId, "https://twitch.tv/" + event.getChannel().getName(), event.getStream().getGameId(), getGame(event.getStream().getGameId()), event.getStream().getTitle());
                                log(Level.FINE, discordId + " stream online due to twitch listener");
                            }
                        }
                    });
                    eventHandler.onEvent(ChannelChangeGameEvent.class, event -> {
                        if (listeners.containsKey(event.getChannel().getName().toLowerCase())) {
                            String discordId = listeners.get(event.getChannel().getName().toLowerCase());
                            if (streams.containsKey(discordId.toLowerCase())) {
                                User user = getMoepsBot().getUser(discordId);
                                updateGame(user, discordId, getGame(event.getGameId()));
                            }
                        }
                    });
                    eventHandler.onEvent(ChannelChangeTitleEvent.class, event -> {
                        if (listeners.containsKey(event.getChannel().getName().toLowerCase())) {
                            String discordId = listeners.get(event.getChannel().getName().toLowerCase());
                            if (streams.containsKey(discordId.toLowerCase())) {
                                User user = getMoepsBot().getUser(discordId);
                                updateTitle(user, discordId, event.getTitle());
                            }
                        }
                    });
                    eventHandler.onEvent(ChannelGoOfflineEvent.class, event -> {
                        if (listeners.containsKey(event.getChannel().getName().toLowerCase())) {
                            String discordId = listeners.get(event.getChannel().getName().toLowerCase());
                            User user = getMoepsBot().getUser(discordId);
                            onOffline(user, discordId);
                            log(Level.FINE, discordId + " stream offline due to twitch listener");
                        }
                    });
                    if (username != null) {
                        SimpleEventHandler chatEventHandler = twitchClient.getChat().getEventManager().getEventHandler(SimpleEventHandler.class);
                        chatEventHandler.onEvent(ChannelMessageEvent.class, event -> {
                            if (listeners.containsKey(event.getChannel().getName().toLowerCase())) {
                                String commandPrefix = getConfig().hasPath("twitch.commandprefix." + event.getMessageEvent().getChannel().getName().toLowerCase())
                                        ? getConfig().getString("twitch.commandprefix." + event.getMessageEvent().getChannel().getName().toLowerCase())
                                        : getConfig().hasPath("twitch.commandprefix.default")
                                                ? getConfig().getString("twitch.commandprefix.default")
                                                : "!";
                                if (event.getMessage().startsWith(commandPrefix)) {
                                    switch (event.getMessage().toLowerCase().substring(1)) {
                                        case "group":
                                        case "voice":
                                        case "gruppe":
                                            String discordId = listeners.get(event.getChannel().getName().toLowerCase());
                                            User user = getMoepsBot().getUser(discordId);
                                            Map<String, String> userInfos = new LinkedHashMap<>();
                                            logDebug("Executing command " + event.getMessage() + " in " + event.getChannel().getName() + " with " + discordId + "/" + (user != null ? user.getDiscriminatedName() : "null"));
                                            for (ServerVoiceChannel voiceChannel : user.getConnectedVoiceChannels()) {
                                                if (getConfig(voiceChannel.getServer()) != null) {
                                                    for (User connectedUser : voiceChannel.getConnectedUsers()) {
                                                        String userInfo = connectedUser.getDisplayName(voiceChannel.getServer());
                                                        if (listeners.containsValue(connectedUser.getDiscriminatedName())) {
                                                            userInfo += " - https://twitch.tv/" + listeners.inverse().get(connectedUser.getDiscriminatedName());
                                                        }
                                                        userInfos.putIfAbsent(connectedUser.getDiscriminator(), userInfo);
                                                    }
                                                }
                                            }
                                            if (!userInfos.isEmpty()) {
                                                event.getTwitchChat().sendMessage(event.getChannel().getName(), String.join(", ", userInfos.values()));
                                            }
                                            return;
                                    }
                                }
                            }
                        });
                    }
                }
            }
        }

        markChannel = getConfig().getBoolean("streaming-marker.enabled");
        markerPrefix = getConfig().getString("streaming-marker.prefix");
        markerSuffix = getConfig().getString("streaming-marker.suffix");

        getMoepsBot().getDiscordApi().addServerVoiceChannelMemberJoinListener(event -> {
            StreamData streamData = getStreamData(event.getUser());
            if (streamData != null) {
                if (markChannel) {
                    markChannelName(event.getChannel());
                }
                if (streamData.getUrl() != null) {
                    joinTwitchChat(getUserLogin(streamData.getUrl()));
                }
            }
        });

        getMoepsBot().getDiscordApi().addServerVoiceChannelMemberLeaveListener(event -> {
            StreamData streamData = getStreamData(event.getUser());
            if (streamData != null) {
                if (markChannel) {
                    checkForMarkRemoval(event.getChannel());
                }
                if (streamData.getUrl() != null) {
                    leaveTwitchChat(getUserLogin(streamData.getUrl()));
                }
            }
        });

        getMoepsBot().getDiscordApi().addUserChangeActivityListener(event -> {
            if (event.getNewActivity().isPresent() && event.getNewActivity().get().getType() == ActivityType.STREAMING) {
                onLive(
                        event.getUser(),
                        event.getUser().getDiscriminatedName(),
                        event.getNewActivity().get().getStreamingUrl().orElse(null),
                        getGameId(event.getNewActivity().get().getName()),
                        event.getNewActivity().get().getName(),
                        event.getNewActivity().get().getDetails().orElse("")
                );
                log(Level.FINE, event.getUser().getDiscriminatedName() + " stream online due to activity");
            } else if (event.getOldActivity().isPresent() && event.getOldActivity().get().getType() == ActivityType.STREAMING) {
                onOffline(event.getUser(), event.getUser().getDiscriminatedName());
                log(Level.FINE, event.getUser().getDiscriminatedName() + " stream offline due to activity");
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

    private ServerData getServerData(Server server) {
        return serverData.computeIfAbsent(server.getId(), id -> new ServerData(id, server.getIcon().isPresent() ? server.getIcon().get().getUrl() : null));
    }

    private void updateTitle(User user, String rawName, String title) {
        StreamData streamData = getStreamData(rawName);
        streamData.setTitle(title);
        logDebug(rawName + " changed title to " + title);
        updateNotification(user, rawName, streamData);
    }

    private void updateGame(User user, String rawName, String game) {
        StreamData streamData = getStreamData(rawName);
        streamData.setGame(game);
        streamData.setGameId(getGameId(game));
        logDebug(rawName + " changed game to " + streamData.getGame() + "/" + streamData.getGameId());
        updateNotification(user, rawName, streamData);
    }

    private void updateNotification(User user, String rawName, StreamData streamData) {
        for (Server server : user != null ? user.getMutualServers() : getMoepsBot().getDiscordApi().getServers()) {
            Config serverConfig = getConfig(server);
            String twitchChannel = getUserLogin(streamData.getUrl());
            String newMessage = Utils.replace(
                    serverConfig.hasPath("announce.message") ? serverConfig.getString("announce.message") : "%name% is now live: %url%",
                    "streamname", twitchChannel != null ? twitchChannel : user != null ? user.getDisplayName(server) : rawName,
                    "username", user != null ? user.getDisplayName(server) : rawName,
                    "game", streamData.getGame(),
                    "title", streamData.getTitle(),
                    "url", streamData.getUrl()
            );
            updateNotificationMessage(server, streamData.getUrl(), newMessage, false);
        }
    }

    private String getGame(String gameId) {
        return gameCache.get(gameId);
    }

    private String getGameId(String game) {
        for (Map.Entry<String, String> entry : gameCache.asMap().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(game)) {
                return entry.getKey();
            }
        }
        List<Game> games = twitchClient.getHelix().getGames(oAuthToken, null, Collections.singletonList(game)).execute().getGames();
        if (!games.isEmpty()) {
            for (Game g : games) {
                gameCache.put(g.getId(), g.getName());
            }
            return games.get(0).getId();
        }
        return null;
    }

    private void onLive(User user, String rawName, String streamingUrl, String gameId, String game, String title) {
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
            streams.put(rawName.toLowerCase(), new StreamData(streamingUrl, gameId, game, title));
        } else {
            streamData.setGame(game);
            streamData.setGameId(gameId);
            streamData.setTitle(title);
        }

        boolean hasHandledRole = false;
        String twitchChannel = null;
        if (streamingUrl != null) {
            twitchChannel = getUserLogin(streamingUrl);
            for (Server server : user != null && !user.getMutualServers().isEmpty() ? user.getMutualServers() : getMoepsBot().getDiscordApi().getServers()) {
                Config announceConfig = getConfig(server, "announce");
                logDebug("Handling server " + server.getName() + ". (" + (announceConfig != null) + ")");
                if (announceConfig != null) {
                    if (user != null && announceConfig.hasPath("roles") && !Utils.hasRole(user, server, announceConfig.getStringList("roles"))) {
                        continue;
                    }

                    hasHandledRole = true;

                    ServerData serverData = getServerData(server);
                    serverData.getLiveUsers().add(rawName.toLowerCase());

                    if (announceConfig.hasPath("channel")) {
                        String message = Utils.replace(
                                announceConfig.hasPath("message") ? announceConfig.getString("message") : "%name% is now live: %url%",
                                "streamname", twitchChannel != null ? twitchChannel : user != null ? user.getDisplayName(server) : rawName,
                                "username", user != null ? user.getDisplayName(server) : rawName,
                                "game", game,
                                "title", title,
                                "url", streamingUrl
                        );
                        if (streamData == null) {
                            ServerTextChannel channel = Utils.getTextChannel(server, announceConfig.getString("channel"));
                            if (channel != null) {
                                channel.sendMessage(message);
                            } else {
                                log(Level.WARNING, "Could not find announce channel " + announceConfig.getString("channel") + " on server " + server.getName() + "/" + server.getId());
                            }
                        } else {
                            updateNotificationMessage(server, streamingUrl, message, false);
                        }
                    }
                    if (serverData.getLiveUsers().size() == 1 && announceConfig.hasPath("icon.live")) {
                        try {
                            server.updateIcon(new URL(announceConfig.getString("icon.live")));
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        if (listeners.containsValue(rawName)) {
            twitchChannel = listeners.inverse().get(rawName);
        }

        if (user != null) {
            boolean isInVoice = false;
            for (ServerVoiceChannel voiceChannel : user.getConnectedVoiceChannels()) {
                isInVoice = true;
                if (markChannel) {
                    markChannelName(voiceChannel);
                }
            }
            if (hasHandledRole && isInVoice) {
                joinTwitchChat(twitchChannel);
            }
        }
    }

    private void onOffline(User user, String rawName) {
        StreamData streamData = streams.remove(rawName.toLowerCase());
        if (user != null) {
            user.getConnectedVoiceChannels().forEach(this::checkForMarkRemoval);

            if (!user.isBot()) {
                log(Level.FINE, user.getDiscriminatedName() + " stopped streaming " + (streamData != null ? streamData.getGame() + " " + streamData.getTitle() + " at " + streamData.getUrl() : ""));
            }
        } else {
            log(Level.FINE, rawName + " stopped streaming " + (streamData != null ? streamData.getGame() + " " + streamData.getTitle() + " at " + streamData.getUrl() : ""));
        }

        String twitchChannel = null;
        String vodUrl = null;
        if (streamData != null && streamData.getUrl() != null) {
            twitchChannel = getUserLogin(streamData.getUrl());

            vodUrl = getVodUrl(streamData.getUrl(), streamData.getGameId());
        }

        for (Server server : user != null && !user.getMutualServers().isEmpty() ? user.getMutualServers() : getMoepsBot().getDiscordApi().getServers()) {
            Config announceConfig = getConfig(server, "announce");
            logDebug("Handling server " + server.getName() + ". (" + (announceConfig != null) + ")");
            if (announceConfig != null) {
                ServerData serverData = getServerData(server);
                logDebug("Currently live: " + String.join(", ", serverData.getLiveUsers()));
                serverData.getLiveUsers().remove(rawName.toLowerCase());
                if (streamData != null && announceConfig.hasPath("channel") && announceConfig.hasPath("offline")) {
                    String newMessage = Utils.replace(
                            announceConfig.getString("offline"),
                            "streamname", twitchChannel != null ? twitchChannel : user != null ? user.getDisplayName(server) : rawName,
                            "username", user != null ? user.getDisplayName(server) : rawName,
                            "game", streamData.getGame(),
                            "title", streamData.getTitle(),
                            "url", streamData.getUrl(),
                            "vodurl", vodUrl
                    );
                    logDebug("Setting announce message to: " + newMessage);
                    updateNotificationMessage(server, streamData.getUrl(), newMessage, true);
                }
                if (serverData.getLiveUsers().isEmpty() && announceConfig.hasPath("icon.live")) {
                    if (announceConfig.hasPath("icon.offline")) {
                        try {
                            logDebug("Updating icon to configured offline icon " + announceConfig.getString("icon.offline"));
                            server.updateIcon(new URL(announceConfig.getString("icon.offline")));
                        } catch (MalformedURLException e) {
                            server.updateIcon(serverData.getIcon());
                            e.printStackTrace();
                        }
                    } else if (serverData.getIcon() != null) {
                        logDebug("Updating icon to cached icon " + serverData.getIcon());
                        server.updateIcon(serverData.getIcon());
                    } else {
                        log(Level.WARNING, "Live icon defined but no configured or cached offline icon found! Cannot reset server icon!");
                    }
                }
            }
        }

        if (listeners.containsValue(rawName)) {
            twitchChannel = listeners.inverse().get(rawName);
        }

        leaveTwitchChat(twitchChannel);
    }

    private String getVodUrl(String streamUrl, String gameId) {
        String userLogin = getUserLogin(streamUrl);
        if (userLogin != null) {
            List<com.github.twitch4j.helix.domain.User> userList = twitchClient.getHelix().getUsers(oAuthToken, null, Collections.singletonList(userLogin)).execute().getUsers();
            logDebug("Getting VOD of " + userLogin + " (" + userList.size() + ")");
            if (!userList.isEmpty()) {
                List<Video> videoList = twitchClient.getHelix().getVideos(oAuthToken, null, userList.get(0).getId(), gameId, null, "day", null, "archive", null, null, 1).execute().getVideos();
                if (!videoList.isEmpty()) {
                    logDebug("Found vod " + videoList.get(0).getUrl() );
                    return videoList.get(0).getUrl();
                }
            }
        }
        return streamUrl;
    }

    private String getUserLogin(String url) {
        String[] parts = url.split("twitch.tv/");
        return parts.length > 1 ? WordUtils.capitalize(parts[1], new char[]{'_', '-'}) : null;
    }

    private void updateNotificationMessage(Server server, String streamingUrl, String message, boolean offline) {
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
                        log(Level.FINE, "Deleting message " + m.getIdAsString() + " due to config (" + message + ")");
                        m.delete("Stream is now offline");
                    } else if (offline && m.getCreationTimestamp().isAfter(Instant.now().minus(Duration.ofMinutes(5)))) {
                        log(Level.FINE, "Deleting message " + m.getIdAsString() + " due to it being less than 5 minutes old");
                        m.delete("Stream started less than 5 minutes ago");
                    } else {
                        log(Level.FINE, "Editing message " + m.getIdAsString() + " to '" + message + "'");
                        m.edit(message);
                    }
                    break;
                }
            }
        });

    }

    private void joinTwitchChat(String twitchChannel) {
        if (twitchChannel != null && !twitchClient.getChat().getChannels().contains(twitchChannel)) {
            twitchClient.getChat().joinChannel(twitchChannel);
            logDebug("Joined " + twitchChannel + " Twitch channel");
            logDebug(twitchClient.getChat().getConnectionState() + " " + String.join(", ", twitchClient.getChat().getCurrentChannels()));
        }
    }

    private void leaveTwitchChat(String twitchChannel) {
        if (twitchChannel != null ) {
            twitchClient.getChat().leaveChannel(twitchChannel);
            logDebug("Left " + twitchChannel + " Twitch channel");
            logDebug(twitchClient.getChat().getConnectionState() + " " + String.join(", ", twitchClient.getChat().getCurrentChannels()));
        }
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

    private class StreamData {
        private final String url;
        private String gameId;
        private String game;
        private String title;

        private StreamData(String url, String gameId, String game, String title) {
            this.url = url;
            this.gameId = gameId;
            this.game = game;
            this.title = title;
        }

        public String getUrl() {
            return url;
        }

        public String getGameId() {
            return gameId;
        }

        public void setGameId(String gameId) {
            this.gameId = gameId;
        }

        public String getGame() {
            return game;
        }

        public void setGame(String game) {
            this.game = game;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StreamData that = (StreamData) o;
            return Objects.equals(url, that.url) && Objects.equals(gameId, that.gameId) && Objects.equals(game, that.game) && Objects.equals(title, that.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(url, gameId, game, title);
        }

        @Override
        public String toString() {
            return "StreamData{" +
                    "url='" + url + '\'' +
                    ", gameId='" + gameId + '\'' +
                    ", game='" + game + '\'' +
                    ", title='" + title + '\'' +
                    '}';
        }
    }

    private class ServerData {
        private final Long id;
        private final URL icon;
        private Set<String> liveUsers = new LinkedHashSet<>();

        public ServerData(Long id, URL icon) {
            this.id = id;
            this.icon = icon;
        }

        public Long getId() {
            return id;
        }

        public URL getIcon() {
            return icon;
        }

        public Set<String> getLiveUsers() {
            return liveUsers;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ServerData that = (ServerData) o;
            return Objects.equals(id, that.id) && Objects.equals(icon, that.icon) && Objects.equals(liveUsers, that.liveUsers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, icon, liveUsers);
        }

        @Override
        public String toString() {
            return "ServerData{" +
                    "id=" + id +
                    ", icon=" + icon +
                    ", liveUsers=" + liveUsers +
                    '}';
        }
    }
}
