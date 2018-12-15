package tv.moep.discord.bot.managers;

/*
 * discordbot
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

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoleManager {
    public static final String REGEX_PREFIX = "r=";
    private final Config config;

    public RoleManager(MoepsBot moepsBot) {
        config = moepsBot.getConfig("roles");

        Config defaultConfig = ConfigFactory.parseMap(ImmutableMap.of(
                "playing", new ArrayList<String>(),
                "streaming", new ArrayList<String>(),
                "listening", new ArrayList<String>(),
                "watching", new ArrayList<String>(),
                "temporary", false
        ));

        moepsBot.getDiscordApi().addUserChangeActivityListener(event -> {
            for (Server server : event.getUser().getMutualServers()) {
                if (config.hasPath(server.getIdAsString())) {
                    for (String roleId : config.getConfig(server.getIdAsString()).root().keySet()) {
                        Optional<Role> role = server.getRoleById(roleId);
                        if (role.isPresent()) {
                            Config roleConfig = config.getConfig(server.getIdAsString() + "." + roleId).withFallback(defaultConfig);
                            boolean matches = false;
                            if (event.getNewActivity().isPresent()) {
                                List<String> matching = Utils.getList(roleConfig, event.getNewActivity().get().getType().name().toLowerCase());
                                for (String match : matching) {
                                    if (event.getNewActivity().get().getName().equalsIgnoreCase(match) ||
                                            (match.startsWith(REGEX_PREFIX) && event.getNewActivity().get().getName().matches(match.substring(REGEX_PREFIX.length())))) {
                                        matches = true;
                                        break;
                                    }
                                }
                            }


                            if (matches) {
                                if (!event.getUser().getRoles(server).contains(role.get())) {
                                    event.getUser().addRole(role.get());
                                }
                            } else if (roleConfig.getBoolean("temporary") && event.getUser().getRoles(server).contains(role.get())) {
                                event.getUser().removeRole(role.get());
                            }
                        }
                    }
                }
            }
        });
    }
}
