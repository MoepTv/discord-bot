package tv.moep.discord.bot.commands;

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

import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Permission;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public class ListCommand extends Command {
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat();

    public ListCommand(MoepsBot moepsBot) {
        super("list joins|leaves", Permission.ADMIN);
        registerSubCommand("joins", (sender, args) -> {
            if (sender.getServer() != null) {
                sendMessage(sender, "joins", moepsBot.getJoinLeaveManager().getJoins(sender.getServer()));
                return true;
            }
            return false;
        });
        registerSubCommand("leaves", (sender, args) -> {
            if (sender.getServer() != null) {
                sendMessage(sender, "leaves", moepsBot.getJoinLeaveManager().getLeaves(sender.getServer()));
                return true;
            }
            return false;
        });
    }

    private void sendMessage(CommandSender sender, String name, Collection<Map.Entry<String, Long>> list) {
        sender.sendMessage(
                "Last " + Math.min(list.size(), 10) + " " + name + ":",
                list.stream()
                        .skip(Math.max(list.size() - 10, 0))
                        .map(e -> FORMAT.format(e.getValue()) + " - " + e.getKey())
                        .collect(Collectors.joining("\n"))
        );
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        return false;
    }
}
