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

import java.util.ArrayList;
import java.util.List;

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

}
