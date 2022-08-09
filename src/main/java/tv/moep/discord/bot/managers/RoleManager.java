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

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.javacord.api.entity.activity.Activity;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Permission;
import tv.moep.discord.bot.Utils;
import tv.moep.discord.bot.commands.DiscordSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class RoleManager extends Manager {
    public static final String REGEX_PREFIX = "r=";
    private final Config defaultRoleConfig = ConfigFactory.parseMap(ImmutableMap.of(
            "playing", new ArrayList<String>(),
            "streaming", new ArrayList<String>(),
            "listening", new ArrayList<String>(),
            "watching", new ArrayList<String>(),
            "temporary", false
    ));

    public RoleManager(MoepsBot moepsBot) {
        super(moepsBot, "roles");

        for (Server server : moepsBot.getDiscordApi().getServers()) {
            if (getConfig(server) != null) {
                for (User user : server.getMembers()) {
                    updateRoles(user, user.getActivities(), server);
                }
            }
        }

        moepsBot.getDiscordApi().addReactionAddListener(event -> {
            if (event.getUser().isPresent() && event.getServer().isPresent() && event.getEmoji().isCustomEmoji()) {
                Config config = getConfig(event.getServer().get());
                String path = "reactions." + event.getMessageId() + "." + event.getEmoji().asCustomEmoji().get().getName();
                if (config != null && config.hasPath(path)) {
                    event.getServer().get()
                            .getRoleById(config.getLong(path))
                            .filter(r -> !r.hasUser(event.getUser().get()))
                            .ifPresent(r -> event.getUser().get().addRole(r)
                                    .thenAccept(a -> logDebug("[Reaction] Added role " + r.getName() + " to " + event.getUser().get().getDiscriminatedName())));
                }
            }
        });

        moepsBot.getDiscordApi().addReactionRemoveListener(event -> {
            if (event.getUser().isPresent() && event.getServer().isPresent() && event.getEmoji().isCustomEmoji()) {
                Config config = getConfig(event.getServer().get());
                String path = "reactions." + event.getMessageId() + "." + event.getEmoji().asCustomEmoji().get().getName();
                if (config != null && config.hasPath(path)) {
                    event.getServer().get()
                            .getRoleById(config.getLong(path))
                            .filter(r -> r.hasUser(event.getUser().get()))
                            .ifPresent(r -> event.getUser().get().removeRole(r)
                                    .thenAccept(a -> logDebug("[Reaction] Removed role " + r.getName() + " from " + event.getUser().get().getDiscriminatedName())));
                }
            }
        });

        moepsBot.getDiscordApi().addUserChangeActivityListener(event -> {
            if (event.getUser().isPresent()) {
                updateRoles(event.getUser().get(), event.getNewActivities());
            }
        });

        for (ActivityType type : ActivityType.values()) {
            moepsBot.registerCommand(type.name() + " <name>".toLowerCase(), Permission.USER, (sender, args) -> {
                if (sender instanceof DiscordSender) {
                    if (getConfig(((DiscordSender) sender).getServer()) == null) {
                        return true;
                    }
                    if (args.length > 0) {
                        if (updateRoles(((DiscordSender) sender).getUser(), type, String.join(" ", args), ((DiscordSender) sender).getServer())) {
                            sender.sendReply("Set role for `" + String.join(" ", args) + "`");
                        } else {
                            sender.sendReply("No role for `" + String.join(" ", args) + "` found!");
                        }
                        return true;
                    }
                    return false;
                }
                sender.sendReply("Can only be run by a discord user!");
                return true;
            });
        }
    }

    private boolean updateRoles(User user, Collection<Activity> activities) {
        boolean r = false;
        for (Server server : user.getMutualServers()) {
            if (getConfig(server) != null) {
                r |= updateRoles(user, activities, server);
            }
        }
        return r;
    }

    private boolean updateRoles(User user, ActivityType type, String name) {
        boolean r = false;
        for (Server server : user.getMutualServers()) {
            if (getConfig(server) != null) {
                r |= updateRoles(user, type, name, server);
            }
        }
        return r;
    }

    private boolean updateRoles(User user, Collection<Activity> activities, Server server) {
        if (!activities.isEmpty()) {
            for (Activity activity : activities) {
                if (updateRoles(user, activity.getType(), activity.getName(), server)) {
                    return true;
                }
            }
            return false;
        } else {
            return updateRoles(user, null, null, server);
        }
    }

    private boolean updateRoles(User user, ActivityType type, String name, Server server) {
        if (user == null) {
            return false;
        }

        if (server == null) {
            return updateRoles(user, type, name);
        }

        Config serverConfig = getConfig(server);
        if (serverConfig == null) {
            return false;
        }
        boolean r = false;
        if (type != null && serverConfig.hasPath("dynamicPrefix." + type.name().toLowerCase())) {
            for (Role role : server.getRolesByNameIgnoreCase(serverConfig.getString("dynamicPrefix." + type.name().toLowerCase()) + name)) {
                r = true;
                if (!role.hasUser(user)) {
                    user.addRole(role).thenAccept(a -> logDebug("[Activity-Dynamic] Added role " + role.getName() + " to " + user.getDiscriminatedName()));
                }
            }
        }
        for (String roleId : serverConfig.root().keySet()) {
            Optional<Role> role = server.getRoleById(roleId);
            if (role.isPresent()) {
                Config roleConfig = serverConfig.getConfig(roleId).withFallback(defaultRoleConfig);
                boolean matches = false;
                if (type != null) {
                    List<String> matching = Utils.getList(roleConfig, type.name().toLowerCase());
                    for (String match : matching) {
                        if (name.equalsIgnoreCase(match)
                                || (match.startsWith(REGEX_PREFIX) && name.matches(match.substring(REGEX_PREFIX.length())))) {
                            matches = true;
                            break;
                        }
                    }
                }

                if (matches) {
                    if (!role.get().hasUser(user)) {
                        user.addRole(role.get()).thenAccept(a -> logDebug("[Activity] Added role " + role.get().getName() + " to " + user.getDiscriminatedName()));
                    }
                    r = true;
                } else if (roleConfig.getBoolean("temporary") && role.get().hasUser(user)) {
                    user.removeRole(role.get()).thenAccept(a -> logDebug("[Activity] Removed temporary role " + role.get().getName() + " from " + user.getDiscriminatedName()));
                }
            }
        }
        return r;
    }
}
