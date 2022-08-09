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

import org.javacord.api.entity.channel.ServerVoiceChannel;
import org.javacord.api.entity.channel.VoiceChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

public class RandomCommand extends Command<DiscordSender> {
    private final MoepsBot bot;

    public RandomCommand(MoepsBot bot) {
        super("random [--voice|-v] <option 1>|option 2>|...");
        this.bot = bot;
    }

    @Override
    public boolean execute(DiscordSender sender, String[] args) {
        boolean voice = false;
        int optionIndex = 0;

        if (args.length > 0 && args[0].startsWith("-")) {
            if ("--voice".equalsIgnoreCase(args[0]) || "-v".equalsIgnoreCase(args[0])) {
                voice = true;
                optionIndex = 1;
            } else {
                return false;
            }
        }

        if (args.length <= optionIndex) {
            return false;
        }

        List<String> options = Arrays.stream(Arrays.stream(args).skip(optionIndex).collect(Collectors.joining(" ")).split("\\|"))
                .map(String::trim).collect(Collectors.toList());

        if (voice) {
            Collections.shuffle(options);
            boolean found = false;
            for (Server server : sender.getUser().getMutualServers()) {
                Optional<ServerVoiceChannel> voiceChannel = server.getConnectedVoiceChannel(sender.getUser());
                if (voiceChannel.isPresent()) {
                    found = true;
                    ServerVoiceChannel channel = voiceChannel.get();
                    Collection<User> connected = channel.getConnectedUsers().stream().filter(u -> !u.isBot()).collect(Collectors.toList());
                    if (connected.size() > options.size()) {
                        sender.removeSource();
                        sender.sendReply("More users connected to the voice channel " + channel.getName() + " (" + connected.size() + ") than available options specified! (" + options.size() + ")");
                    } else {
                        for (User user : connected) {
                            String option = options.remove(0);
                            user.openPrivateChannel().whenComplete((c, ex) -> {
                                if (c != null) {
                                    c.sendMessage(new EmbedBuilder()
                                            .setColor(Utils.getRandomColor())
                                            .setDescription(option)
                                            .setAuthor(sender.getUser())
                                            .setFooter("(Private random message sent by " + sender.getUser().getDiscriminatedName() + ")")
                                    ).whenComplete((m, e) -> {
                                        if (m == null) {
                                            if (e != null) {
                                                sender.sendReply("Unable to send message to " + user.getDisplayName(server) + "! " + e.getMessage() + ".");
                                            } else {
                                                sender.sendReply("Unable to send message to " + user.getDisplayName(server) + "! Channel could not be opened!");
                                            }
                                            sender.sendNaturalMessage(user.getDisplayName(server) + "'s option was ||" + option + "||");
                                        }
                                    });
                                    return;
                                } else if (ex != null) {
                                    sender.sendReply("Unable to send message to " + user.getDisplayName(server) + "! " + ex.getMessage() + ".");
                                } else {
                                    sender.sendReply("Unable to send message to " + user.getDisplayName(server) + "! Channel could not be opened!");
                                }
                                sender.sendNaturalMessage(user.getDisplayName(server) + "'s option was ||" + option + "||");
                            });

                        }
                    }
                    break;
                }
            }
            if (!found) {
                sender.removeSource();
                sender.sendMessage("You are not connected to a voice channel that I have access to?");
            }
        } else {
            sender.sendMessage(options.get(new Random().nextInt(options.size())));
        }
        return true;
    }
}
