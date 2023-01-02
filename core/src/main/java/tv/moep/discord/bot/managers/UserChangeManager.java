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

import com.typesafe.config.Config;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Utils;

import java.util.List;
import java.util.logging.Level;

public class UserChangeManager extends Manager {

    public UserChangeManager(MoepsBot moepsBot) {
        super(moepsBot, "user-change");

        moepsBot.getDiscordApi().addUserChangeNicknameListener(event -> {
            if (event.getUser().isBot()) return;
            handleChange(event.getServer(), "nick-name", event.getUser(), event.getOldNickname().orElse("'" + event.getUser().getName() + "' (original)"), event.getNewNickname().orElse("'" + event.getUser().getName() + "' (original)"));
        });

        moepsBot.getDiscordApi().addUserChangeNameListener(event -> {
            for (Server server : event.getUser().getMutualServers()) {
                handleChange(server, "user-name", event.getUser(), event.getOldName(), event.getNewName());
            }
        });

        moepsBot.getDiscordApi().addUserChangeDiscriminatorListener(event -> {
            for (Server server : event.getUser().getMutualServers()) {
                handleChange(server, "discriminator", event.getUser(), event.getOldDiscriminator(), event.getNewDiscriminator());
            }
        });
    }

    private void handleChange(Server server, String type, User user, String oldName, String newName) {
        log(Level.INFO, user.getDiscriminatedName() + " changed their " + type + " in guild " + server.getName() + " from " + oldName + " to " + newName);
        Config serverConfig = getConfig(server);
        if (serverConfig != null && serverConfig.hasPath(type + ".channel")) {
            ServerTextChannel channel = Utils.getTextChannel(server, serverConfig.getString(type + ".channel"));
            if (channel != null) {
                if (serverConfig.hasPath(type + ".message")) {
                    List<String> messages = serverConfig.getStringList(type + ".message");
                    if (messages.isEmpty()) {
                        messages.add(serverConfig.getString(type + ".message"));
                    }
                    if (!messages.isEmpty()) {
                        channel.sendMessage(Utils.replace(
                                messages.get(MoepsBot.RANDOM.nextInt(messages.size())),
                                "username", user.getDiscriminatedName(),
                                "usermention", user.getNicknameMentionTag(),
                                "nickname", user.getDisplayName(server),
                                "discriminator", user.getDiscriminator(),
                                "old", oldName,
                                "new", newName
                        ));
                    }
                } else {
                    channel.sendMessage("*" + user.getDiscriminatedName() + " changed " + type + " from " + oldName + " to " + newName + "*");
                }
            }
        }
    }
}
