package tv.moep.discord.bot.commands;

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

import tv.moep.discord.bot.Permission;

import java.util.concurrent.CompletableFuture;

public interface CommandSender<M> {
    CompletableFuture<M> sendNaturalMessage(String message);

    CompletableFuture<M> sendMessage(String message);

    CompletableFuture<M> sendMessage(String title, String message);

    default void sendReply(String message) {
        sendMessage(message);
    }

    default void sendReply(String title, String message) {
        sendMessage(title, message);
    }

    boolean hasPermission(Permission permission);

    void confirm();

    String getName();

    void removeSource();
}
