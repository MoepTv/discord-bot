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

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.javacord.api.entity.server.Server;
import tv.moep.discord.bot.MoepsBot;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;

public class JoinLeaveManager {

    private Multimap<Long, Map.Entry<String, Long>> joins = MultimapBuilder.hashKeys().arrayListValues().build();
    private Multimap<Long, Map.Entry<String, Long>> leaves = MultimapBuilder.hashKeys().arrayListValues().build();

    public JoinLeaveManager(MoepsBot moepsBot) {
        moepsBot.getDiscordApi().addServerMemberJoinListener(event -> {
            MoepsBot.log(Level.INFO, event.getUser().getDiscriminatedName() + " joined guild " + event.getServer().getName());
            joins.put(event.getServer().getId(), new AbstractMap.SimpleEntry<>(event.getUser().getDiscriminatedName(), System.currentTimeMillis()));
        });
        moepsBot.getDiscordApi().addServerMemberLeaveListener(event -> {
            MoepsBot.log(Level.INFO, event.getUser().getDiscriminatedName() + " left guild " + event.getServer().getName());
            leaves.put(event.getServer().getId(), new AbstractMap.SimpleEntry<>(event.getUser().getDiscriminatedName(), System.currentTimeMillis()));
        });
    }

    public Collection<Map.Entry<String, Long>> getJoins(Server server) {
        return joins.get(server.getId());
    }

    public Collection<Map.Entry<String, Long>> getLeaves(Server server) {
        return leaves.get(server.getId());
    }
}
