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
import org.javacord.api.entity.activity.Activity;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.channel.ServerVoiceChannel;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class VoiceChannelManager {

    private final Config config;

    public VoiceChannelManager(MoepsBot moepsBot) {
        config = moepsBot.getConfig("voice-channel");

        moepsBot.getDiscordApi().addServerVoiceChannelMemberJoinListener(event -> {
            User user = event.getUser();
            if (user.getActivity().isPresent() && user.getActivity().get().getType() == ActivityType.PLAYING) {
                checkForMove(user, user.getActivity().get(), event.getChannel());
            }
        });

        moepsBot.getDiscordApi().addUserChangeActivityListener(event -> {
            User user = event.getUser();
            if (event.getNewActivity().isPresent() && event.getNewActivity().get().getType() == ActivityType.PLAYING) {
                for (ServerVoiceChannel voiceChannel : user.getConnectedVoiceChannels()) {
                    checkForMove(user, event.getNewActivity().get(), voiceChannel);
                }
            }
        });
    }

    /**
     * Check whether or not to move the user
     * @param user          The user to check
     * @param activity      The user's activity to check
     * @param voiceChannel  The channel to check
     * @return The channel the user might have moved to, an empty optional if he wasn't used for any reason
     */
    private Optional<ServerVoiceChannel> checkForMove(User user, Activity activity, ServerVoiceChannel voiceChannel) {
        String channelPath = voiceChannel.getServer().getIdAsString() + "." + voiceChannel.getIdAsString();
        String path = channelPath + ".games." + activity.getName();
        if (config.hasPath(path)) {
            List<String> ignoredRoles = config.getStringList(channelPath + ".ignoreRoles");
            for (Role role : user.getRoles(voiceChannel.getServer())) {
                if (ignoredRoles.contains(role.getName()) || ignoredRoles.contains(role.getIdAsString())) {
                    return Optional.empty();
                }
            }
            ServerVoiceChannel targetChannel = voiceChannel.getServer().getVoiceChannelById(config.getString(path)).orElseGet(() -> {
                List<ServerVoiceChannel> matchingChannels = voiceChannel.getServer().getVoiceChannelsByName(config.getString(path));
                if (!matchingChannels.isEmpty()) {
                    return matchingChannels.get(0);
                }
                return null;
            });
            if (targetChannel != null) {
                user.move(targetChannel);
                MoepsBot.log(Level.FINE, "Moved " + user.getMentionTag() + " from channel " + voiceChannel.getName() + "/" + voiceChannel.getIdAsString() + " to " + targetChannel.getName() + "/" + targetChannel.getIdAsString() + " because he was playing " + activity.getName());
                return Optional.of(targetChannel);
            }
        }
        return Optional.empty();
    }
}
