package tv.moep.discord.bot.managers;

/*
 * MoepTv - bot
 * Copyright (C) 2020 Max Lee aka Phoenix616 (max@themoep.de)
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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Table;
import com.typesafe.config.Config;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.server.invite.RichInvite;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Utils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class InviteManager extends Manager {
    private Table<Long, String, Integer> inviteCounts = HashBasedTable.create();
    private Multimap<Long, String> dynamicRoles = MultimapBuilder.hashKeys().hashSetValues().build();
    private Map<Long, String> widgetRoles = new HashMap<>();

    public InviteManager(MoepsBot moepsBot) {
        super(moepsBot, "invites");

        for (Server server : moepsBot.getDiscordApi().getServers()) {
            Config serverConfig = getConfig(server);
            if (serverConfig != null) {
                dynamicRoles.putAll(server.getId(), Utils.getList(serverConfig, "dynamicRoles"));
                if (serverConfig.hasPath("widgetRole")) {
                    widgetRoles.put(server.getId(), serverConfig.getString("widgetRole"));
                }
            }
        }

        checkForNewInvites();

        moepsBot.getDiscordApi().addServerMemberJoinListener(event -> {
            Map<String, Integer> inviteMap = inviteCounts.row(event.getServer().getId());
            if (inviteMap != null) {
                event.getServer().getInvites().thenAccept(invites -> {
                    log(Level.FINE, "Checking " + invites.size() + " invites of " + event.getServer().getName() + "/" + event.getServer().getId());
                    RichInvite foundInvite = null;
                    for (RichInvite invite : invites) {
                        if (inviteMap.containsKey(invite.getCode())) {
                            if (invite.getUses() > inviteMap.get(invite.getCode())) {
                                inviteMap.put(invite.getCode(), invite.getUses());
                                foundInvite = invite;
                            }
                        }
                    }
                    if (foundInvite == null) {
                        for (RichInvite invite : invites) {
                            if (!inviteMap.containsKey(invite.getCode()) && isValid(invite)) {
                                if (foundInvite == null || invite.getCreationTimestamp().isAfter(foundInvite.getCreationTimestamp())) {
                                    // Assume missing invite and use last created invite
                                    foundInvite = invite;
                                }
                                inviteMap.put(invite.getCode(), invite.getUses());
                            }
                        }
                    }
                    if (foundInvite != null) {
                        handleInvite(event.getUser(), foundInvite, event.getServer());
                    } else {
                        log(Level.FINE, event.getUser().getDiscriminatedName() + " joined with an unknown invite ");
                    }
                });
            }
        });

        moepsBot.getScheduler().scheduleAtFixedRate(this::checkForNewInvites, 5, 5, TimeUnit.MINUTES);
    }

    private boolean isValid(RichInvite invite) {
        return !invite.isRevoked()
                && (invite.getMaxUses() == 0 || invite.getUses() < invite.getMaxUses())
                && (!invite.isTemporary() || invite.getCreationTimestamp().plusSeconds(invite.getMaxAgeInSeconds()).isAfter(Instant.now()));
    }

    private void checkForNewInvites() {
        for (Server server : getMoepsBot().getDiscordApi().getServers()) {
            if (getConfig(server) != null) {
                logDebug("Checking invites of " +  server.getName() + "/" + server.getId());
                Map<String, Integer> inviteMap = inviteCounts.row(server.getId());
                try {
                    for (RichInvite invite : server.getInvites().get()) {
                        logDebug("Found invite " + invite.getCode() + " " + invite.getUses() + "/" + invite.getMaxUses() + " " + invite.getMaxAgeInSeconds() + " " + (invite.getInviter() != null ? invite.getInviter().getDiscriminatedName() : "null"));
                        if (isValid(invite)) {
                            inviteMap.putIfAbsent(invite.getCode(), invite.getUses());
                        } else {
                            logDebug("Invite is not valid!");
                        }
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logDebug("Could not get invites for server " + server.getName() + "/" + server.getId() + "! " + e.getMessage());
                }
            }
        }
    }

    private void handleInvite(User user, RichInvite invite, Server server) {
        log(Level.FINE, user.getDiscriminatedName() + " joined with invite " + invite.getCode() + " from " + (invite.getInviter() != null ? invite.getInviter().getDiscriminatedName() : "null"));

        List<String> inviteRoles = Utils.getList(getConfig(), server.getId() + ".inviteRoles." + invite.getCode());
        for (String inviteRole : inviteRoles) {
            server.getRoleById(inviteRole).ifPresent(user::addRole);
        }

        if (inviteRoles.isEmpty()) {
            List<Role> availableRoles = dynamicRoles.get(server.getId()).stream()
                    .map(id -> server.getRoleById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            Role addRole = null;
            if (invite.getInviter() != null) {
                for (Role role : invite.getInviter().getRoles(server)) {
                    for (Role availableRole : availableRoles) {
                        if (availableRole.getPosition() <= role.getPosition() && (addRole == null || addRole.getPosition() < availableRole.getPosition())) {
                            addRole = availableRole;
                        }
                    }
                }
            } else if (widgetRoles.containsKey(server.getId())) {
                addRole = server.getRoleById(widgetRoles.get(server.getId())).orElse(null);
            }
            if (addRole != null) {
                user.addRole(addRole);
            }
        }
    }
}
