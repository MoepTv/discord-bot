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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.javacord.api.entity.message.Message;
import org.javacord.api.util.NonThrowingAutoCloseable;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Utils;
import tv.moep.discord.bot.commands.ChannelMessageSender;
import tv.moep.discord.bot.commands.DiscordSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class PrivateConversationManager extends Manager {
    private static Map<String, Set<Long>> once = new HashMap<>();
    private static final Random RANDOM = new Random();

    private final long lastMessageCooldown;
    private final Cache<Long, Long> lastMessage;

    private final Map<String, Topic> topics = new LinkedHashMap<>();

    public PrivateConversationManager(MoepsBot moepsBot) {
        super(moepsBot, "private-conversation");
        lastMessage = CacheBuilder.newBuilder().maximumSize(getConfig().getLong("lastMessage.cacheSize")).build();
        lastMessageCooldown = getConfig().getLong("lastMessage.cooldown");
        if (getConfig().hasPath("enabled") && !getConfig().getBoolean("enabled")) {
            return;
        }
        Config defaultConfig = ConfigFactory.parseMap(ImmutableMap.of(
                "onlyOnce", false,
                "triggers", new ArrayList<String>(),
                "responses", new ArrayList<String>()
        ));
        for (String topic : getConfig().getConfig("topics").root().keySet()) {
            addTopic(new Topic(topic, getConfig().getConfig("topics." + topic).withFallback(defaultConfig)));
        }
        moepsBot.getDiscordApi().addMessageCreateListener(event -> {
            if (!event.isPrivateMessage() || event.getMessageAuthor().getId() == moepsBot.getDiscordApi().getClientId()) {
                return;
            }

            if (event.getReadableMessageContent().startsWith("!")
                    && moepsBot.runCommand(new ChannelMessageSender(moepsBot, event.getMessage()), event.getReadableMessageContent().substring(1))) {
                return;
            }

            Long last = lastMessage.getIfPresent(event.getMessageAuthor().getId());
            if (last != null && last + lastMessageCooldown * 1000 > System.currentTimeMillis()) {
                return;
            }
            lastMessage.put(event.getMessageAuthor().getId(), System.currentTimeMillis());

            String sender = event.getMessageAuthor().getDiscriminatedName();
            log(Level.INFO, "PM from " + sender + ": " + event.getReadableMessageContent());

            Topic topic = getTopicFromMessage(event.getMessage());
            if (topic != null) {
                log(Level.FINE, sender + " | Matched topic '" + topic.getName() + "'");
                if (topic.isOnlyOnce()) {
                    once.get(topic.getName()).add(event.getMessageAuthor().getId());
                }
                if (!topic.getResponses().isEmpty()) {
                    String response = topic.getResponses().get(RANDOM.nextInt(topic.getResponses().size()));
                    log(Level.FINE, sender + " | Selected response: " + response);
                    new Thread(() -> {
                        try (NonThrowingAutoCloseable closeable = event.getPrivateChannel().get().typeContinuouslyAfter(1, TimeUnit.SECONDS)) {
                            synchronized (response) {
                                response.wait(Math.min(1000 + response.length() * 100, 10 * 1000));
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        event.getPrivateChannel().get().sendMessage(Utils.replacePlaceholders(response));
                        log(Level.FINE, sender + " | Message sent!");
                    }).start();
                } else {
                    log(Level.FINE, sender + " | Topic has no responses!");
                }
            }
        });
    }

    private void addTopic(Topic topic) {
        topics.put(topic.getName(), topic);
        if (topic.isOnlyOnce()) {
            once.putIfAbsent(topic.getName(), new HashSet<>());
        }
    }

    private Topic getTopicFromMessage(Message message) {
        for (Topic topic : topics.values()) {
            if (topic.isOnlyOnce() && once.get(topic.getName()).contains(message.getAuthor().getId())) {
                continue;
            }

            if (contains(message.getReadableContent(), topic.getTriggers())) {
                return topic;
            }
        }
        return null;
    }

    private boolean contains(String string, Collection<String> checkFor) {
        for (String s : string.split("[ \\?\\!\\.]")) {
            if (checkFor.contains(s.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private class Topic {
        private final String name;
        private final boolean onlyOnce;
        private final HashSet<String> triggers;
        private final List<String> responses;

        public Topic(String name, Config config) {
            this.name = name;
            onlyOnce = config.getBoolean("onlyOnce");
            triggers = new HashSet<>(config.getStringList("triggers"));
            if (triggers.isEmpty() && config.hasPath("trigger")) {
                triggers.add(config.getString("trigger"));
            }
            responses = config.getStringList("responses");
            if (responses.isEmpty() && config.hasPath("response")) {
                responses.add(config.getString("response"));
            }
        }

        public String getName() {
            return name;
        }

        public boolean isOnlyOnce() {
            return onlyOnce;
        }

        public HashSet<String> getTriggers() {
            return triggers;
        }

        public List<String> getResponses() {
            return responses;
        }
    }
}
