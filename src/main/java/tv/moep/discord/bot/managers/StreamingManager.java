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
import org.javacord.api.entity.channel.ServerVoiceChannel;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;

import java.util.logging.Level;

public class StreamingManager {

    private final Config config;
    private final String markerPrefix;
    private final String markerSuffix;

    public StreamingManager(MoepsBot moepsBot) {
        config = moepsBot.getConfig("streaming");

        boolean markChannel = config.getBoolean("streaming-marker.enabled");
        markerPrefix = config.getString("streaming-marker.prefix");
        markerSuffix = config.getString("streaming-marker.suffix");

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
                MoepsBot.log(Level.FINE, user.getMentionTag() + " started streaming " + user.getActivity().get().getName() + " at " + user.getActivity().get().getStreamingUrl());

                if (markChannel && (!event.getOldActivity().isPresent() || event.getOldActivity().get().getType() != ActivityType.STREAMING)) {
                    for (ServerVoiceChannel voiceChannel : user.getConnectedVoiceChannels()) {
                        markChannelName(voiceChannel);
                    }
                }
            } else if (event.getOldActivity().isPresent() && event.getOldActivity().get().getType() == ActivityType.STREAMING) {
                user.getConnectedVoiceChannels().forEach(this::checkForMarkRemoval);
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
            voiceChannel.updateName(markerPrefix + voiceChannel + markerSuffix);
        }
    }

    private boolean isMarked(ServerVoiceChannel channel) {
        return channel.getName().startsWith(markerPrefix) && channel.getName().endsWith(markerSuffix);
    }
}
