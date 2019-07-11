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

import lombok.Getter;
import tv.moep.discord.bot.Permission;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Getter
public abstract class Command<T extends CommandSender> {
    private final Command parent;
    private final String name;
    private final String usage;
    private final Permission permission;
    private final List<String> aliases;
    private Map<String, Command> subCommands = new HashMap<>();

    public Command(String usage, String... aliases) {
        this(usage, Permission.USER, aliases);
    }

    public Command(Command parent, String usage, String... aliases) {
        this(parent, usage, Permission.USER, aliases);
    }

    public Command(String usage, Permission permission, String... aliases) {
        this(null, usage, permission, aliases);
    }

    public Command(Command parent, String usage, Permission permission, String... aliases) {
        this.parent = parent;
        this.name = usage.split(" ")[0];
        this.usage = usage.contains(" ") ? usage.substring(usage.indexOf(' ') + 1) : "";
        this.permission = permission;
        this.aliases = Arrays.asList(aliases);
    }

    public abstract boolean execute(T sender, String[] args);


    public boolean runCommand(T sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            sender.sendMessage("You need to at least be an " + getPermission() + " to run this command!");
            return true;
        }

        if (args.length > 0) {
            Command<T> subCommand = getSubCommand(args[0]);
            if (subCommand != null) {
                return subCommand.runCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            }
        }
        return execute(sender, args);
    }

    public void registerSubCommand(String usage, BiFunction<T, String[], Boolean> execute) {
        registerSubCommand(usage, getPermission(), execute);
    }

    public void registerSubCommand(String usage, Permission permission, BiFunction<T, String[], Boolean> execute) {
        registerSubCommand(new Command<T>(this, usage, permission) {
            @Override
            public boolean execute(T sender, String[] args) {
                return execute.apply(sender, args);
            }
        });
    }

    public void registerSubCommand(Command<T> command) {
        subCommands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            subCommands.putIfAbsent(alias, command);
        }
    }

    public Command<T> getSubCommand(String name) {
        return subCommands.get(name.toLowerCase());
    }
}
