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
import com.typesafe.config.Config;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.server.invite.RichInvite;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Utils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class InviteManager {
    private final Config config;
    private Map<Long, Map<String, Integer>> inviteCounts = new ConcurrentHashMap<>();
    private Multimap<Long, String> dynamicRoles = MultimapBuilder.hashKeys().hashSetValues().build();
    private MoepsBot moepsBot;

    public InviteManager(MoepsBot moepsBot) {
        this.moepsBot = moepsBot;
        config = moepsBot.getConfig("invites");

        for (Server server : moepsBot.getDiscordApi().getServers()) {
            if (config.hasPath(server.getIdAsString())) {
                dynamicRoles.putAll(server.getId(), Utils.getList(config, server.getId() + ".dynamicRoles"));
            }
        }

        checkForNewInvites();

        moepsBot.getDiscordApi().addServerMemberJoinListener(event -> {
            Map<String, Integer> inviteMap = inviteCounts.get(event.getServer().getId());
            if (inviteMap != null) {
                event.getServer().getInvites().thenAccept(invites -> {
                    RichInvite foundInvite = null;
                    for (RichInvite invite : invites) {
                        if (inviteMap.containsKey(invite.getCode())) {
                            if (invite.getUses() > inviteMap.get(invite.getCode())) {
                                inviteMap.put(invite.getCode(), invite.getUses());
                                foundInvite = invite;
                                break;
                            }
                        } else if (isValid(invite)) {
                            if (foundInvite == null || invite.getCreationTimestamp().isAfter(foundInvite.getCreationTimestamp())) {
                                // Assume missing invite and use last created invite
                                foundInvite = invite;
                            }
                            inviteMap.put(invite.getCode(), invite.getUses());
                        }
                    }
                    if (foundInvite != null) {
                        handleInvite(event.getUser(), foundInvite, event.getServer());
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
        for (Server server : moepsBot.getDiscordApi().getServers()) {
            if (config.hasPath(server.getIdAsString())) {
                Map<String, Integer> inviteMap = inviteCounts.computeIfAbsent(server.getId(), id -> new ConcurrentHashMap<>());
                try {
                    for (RichInvite invite : server.getInvites().get()) {
                        if (isValid(invite)) {
                            inviteMap.putIfAbsent(invite.getCode(), invite.getUses());
                        }
                    }
                } catch (InterruptedException | ExecutionException e) {
                    MoepsBot.log(Level.SEVERE, "Could not get invites for server " + server.getName() + "/" + server.getId() + "! " + e.getMessage());
                }
            }
        }
    }

    private void handleInvite(User user, RichInvite invite, Server server) {
        MoepsBot.log(Level.FINE, user.getDiscriminatedName() + " joined with invite " + invite.getCode() + " from " + invite.getInviter().getDiscriminatedName());

        List<String> inviteRoles = Utils.getList(config, server.getId() + ".inviteRoles." + invite.getCode());
        for (String inviteRole : inviteRoles) {
            server.getRoleById(inviteRole).ifPresent(user::addRole);
        }

        if (inviteRoles.isEmpty()) {
            List<Role> availableRoles = dynamicRoles.get(server.getId()).stream()
                    .map(id -> server.getRoleById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            Role addRole = null;
            for (Role role : invite.getInviter().getRoles(server)) {
                for (Role availableRole : availableRoles) {
                    if (role.getPosition() <= availableRole.getPosition() && (addRole == null || availableRole.getPosition() < addRole.getPosition())) {
                        addRole = availableRole;
                    }
                }
            }
            if (addRole != null) {
                user.addRole(addRole);
            }
        }
    }
}
