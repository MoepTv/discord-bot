package tv.moep.discord.bot;

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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Utils {

    public static List<String> getList(Config config, String path) {
        List<String> list = new ArrayList<>();
        try {
            ConfigValue value = config.getValue(path);
            if (value.valueType() == ConfigValueType.LIST) {
                for (Object o : ((List<?>) value.unwrapped())) {
                    list.add(String.valueOf(o));
                }
            } else {
                list.add(String.valueOf(value.unwrapped()));
            }
        } catch (ConfigException ignored) {}
        return list;
    }

    public static Color getRandomColor() {
        return new Color((int) (MoepsBot.RANDOM.nextDouble() * 0x1000000));
    }

    public static String replacePlaceholders(String response) {
        return replace(response, "version", MoepsBot.VERSION, "name", MoepsBot.NAME);
    }

    public static String replace(String string, String... replacements) {
        for (int i = 0; i+1 < replacements.length; i+=2) {
            string = string.replace("%" + replacements[i] + "%", replacements[i+1]);
        }
        return string;
    }

    public static boolean hasRole(User user, Server server, List<String> roles) {
        for (Role role : user.getRoles(server)) {
            if (roles.contains(role.getName()) || roles.contains(role.getIdAsString())) {
                return true;
            }
        }
        return false;
    }

    public static ServerTextChannel getTextChannel(Server server, String channelStr) {
        return server.getTextChannelById(channelStr).orElseGet(() -> {
            List<ServerTextChannel> channels = server.getTextChannelsByNameIgnoreCase(channelStr);
            return channels.isEmpty() ? null : channels.get(0);
        });
    }
}
