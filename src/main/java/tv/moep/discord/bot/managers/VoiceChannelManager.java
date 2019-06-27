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
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import org.javacord.api.entity.activity.Activity;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.channel.ServerVoiceChannel;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Utils;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class VoiceChannelManager extends Manager {

    public VoiceChannelManager(MoepsBot moepsBot) {
        super(moepsBot, "voice-channel");

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
        Config channelConfig = getConfig(voiceChannel);
        String path = "games.\"" + activity.getName() + "\"";
        if (channelConfig != null && channelConfig.hasPath(path)) {
            if (channelConfig.hasPath("ignoreRoles")) {
                if (Utils.hasRole(user, voiceChannel.getServer(), channelConfig.getStringList("ignoreRoles"))) {
                    return Optional.empty();
                }
            }

            ConfigValue value = getConfig().getValue(path);
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
}
