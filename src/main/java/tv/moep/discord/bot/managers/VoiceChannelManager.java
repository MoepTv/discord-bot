package tv.moep.discord.bot.managers;

/*
 * MoepTv - bot
 * Copyright (C) 2022 Max Lee aka Phoenix616 (max@themoep.de)
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
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import org.javacord.api.entity.activity.Activity;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.channel.ServerVoiceChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Permission;
import tv.moep.discord.bot.Utils;
import tv.moep.discord.bot.commands.Command;
import tv.moep.discord.bot.commands.CommandSender;
import tv.moep.discord.bot.commands.DiscordSender;
import tv.moep.discord.bot.commands.MessageReaction;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class VoiceChannelManager extends Manager {

    private final Cache<Long, MoveRequest> moveRequests = Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

    public VoiceChannelManager(MoepsBot moepsBot) {
        super(moepsBot, "voice-channel");

        moepsBot.getDiscordApi().addServerVoiceChannelMemberJoinListener(event -> {
            User user = event.getUser();
            ServerVoiceChannel voiceChannel = event.getChannel();
            for (Activity activity : user.getActivities()) {
                if (activity.getType() == ActivityType.PLAYING) {
                    Optional<ServerVoiceChannel> newVoice = checkForMove(user, activity, event.getChannel());
                    if (newVoice.isPresent()) {
                        voiceChannel = newVoice.get();
                        break;
                    }
                }
            }

            MoveRequest moveRequest = moveRequests.getIfPresent(user.getId());
            if (moveRequest != null) {
                moveRequests.invalidate(user.getId());
                int amount = 0;
                for (User connectedUser : moveRequest.getVoiceChannel().getConnectedUsers()) {
                    if (!connectedUser.isConnected(voiceChannel)) {
                        connectedUser.move(voiceChannel);
                        amount++;
                    }
                }
                moveRequest.getCommandSender().removeSource();
                int finalAmount = amount;
                ServerVoiceChannel finalVoiceChannel = voiceChannel;
                moveRequest.getAnswerFuture().thenAccept(m -> {
                    m.edit(new EmbedBuilder()
                            .setTitle("Moved " + finalAmount + " users to " + finalVoiceChannel.getName())
                            .setFooter("Answer to " + event.getUser().getDiscriminatedName())
                            .setColor(m.getEmbeds().isEmpty() ? Utils.getRandomColor() : m.getEmbeds().get(0).getColor().orElse(Utils.getRandomColor())));
                    m.addReaction(MessageReaction.CONFIRM);
                });
            }
        });

        moepsBot.getDiscordApi().addUserChangeActivityListener(event -> {
            if (event.getUser().isEmpty())
                return;

            User user = event.getUser().get();

            ACTIVITIES:
            for (Activity activity : event.getNewActivities()) {
                if (activity.getType() == ActivityType.PLAYING) {
                    for (ServerVoiceChannel voiceChannel : user.getConnectedVoiceChannels()) {
                        if (checkForMove(user, activity, voiceChannel).isPresent()) {
                            break ACTIVITIES;
                        }
                    }
                }
            }
        });

        Command<DiscordSender> moveCommand = moepsBot.registerCommand("move", Permission.USER, (sender, args) -> {
            Config serverConfig = getConfig(sender.getServer());
            if (serverConfig != null && serverConfig.hasPath("moveRoles")
                    && Utils.hasRole(sender.getUser(), sender.getServer(), serverConfig.getStringList("moveRoles"))) {
                Optional<ServerVoiceChannel> voiceChannel = sender.getUser().getConnectedVoiceChannel(sender.getServer());
                if (voiceChannel.isPresent()) {
                    moveRequests.put(sender.getUser().getId(), new MoveRequest(
                            voiceChannel.get(),
                            sender,
                            sender.sendMessage("Voice channel move request from " + voiceChannel.get().getName() + " valid for 1 minute!")
                    ));
                } else {
                    sender.removeSource();
                    sender.sendReply("You are not connected to a voice channel in this server?");
                }
            }
            return true;
        }, "mv");
    }

    /**
     * Check whether or not to move the user
     * @param user          The user to check
     * @param activity      The user's activity to check
     * @param voiceChannel  The channel to check
     * @return The channel the user might have moved to, an empty optional if he wasn't used for any reason
     */
    private Optional<ServerVoiceChannel> checkForMove(User user, Activity activity, ServerVoiceChannel voiceChannel) {
        Config channelConfig = getConfig(voiceChannel);
        String path = "games.\"" + activity.getName() + "\"";
        if (channelConfig != null && channelConfig.hasPath(path)) {
            if (channelConfig.hasPath("ignoreRoles")) {
                if (Utils.hasRole(user, voiceChannel.getServer(), channelConfig.getStringList("ignoreRoles"))) {
                    return Optional.empty();
                }
            }

            ConfigValue value = channelConfig.getValue(path);
            ServerVoiceChannel targetChannel = null;
            if (value.valueType() == ConfigValueType.STRING) {
                targetChannel = voiceChannel.getServer().getVoiceChannelById((String) value.unwrapped()).orElse(null);
            }
            if (value.valueType() == ConfigValueType.NUMBER) {
                targetChannel = voiceChannel.getServer().getVoiceChannelById((long) value.unwrapped()).orElse(null);
            }
            if (targetChannel == null) {
                List<ServerVoiceChannel> matchingChannels = voiceChannel.getServer().getVoiceChannelsByName(channelConfig.getString(path));
                if (!matchingChannels.isEmpty()) {
                    targetChannel = matchingChannels.get(0);
                }
            }
            if (targetChannel != null) {
                user.move(targetChannel);
                log(Level.FINE, "Moved " + user.getDiscriminatedName() + " from channel " + voiceChannel.getName() + "/" + voiceChannel.getIdAsString() + " to " + targetChannel.getName() + "/" + targetChannel.getIdAsString() + " because he was playing " + activity.getName());
                return Optional.of(targetChannel);
            }
        }
        return Optional.empty();
    }

    private class MoveRequest {
        private final ServerVoiceChannel voiceChannel;
        private final CommandSender commandSender;
        private final CompletableFuture<Message> answerFuture;

        private MoveRequest(ServerVoiceChannel voiceChannel, CommandSender commandSender, CompletableFuture<Message> answerFuture) {
            this.voiceChannel = voiceChannel;
            this.commandSender = commandSender;
            this.answerFuture = answerFuture;
        }

        public ServerVoiceChannel getVoiceChannel() {
            return voiceChannel;
        }

        public CommandSender getCommandSender() {
            return commandSender;
        }

        public CompletableFuture<Message> getAnswerFuture() {
            return answerFuture;
        }
    }
}
