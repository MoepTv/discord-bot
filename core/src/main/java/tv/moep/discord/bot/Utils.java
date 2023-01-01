package tv.moep.discord.bot;

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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class Utils {

    private static ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

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

    public static Color getRandomColor() {
        return new Color((int) (MoepsBot.RANDOM.nextDouble() * 0x1000000));
    }

    public static String replacePlaceholders(String response) {
        return replace(response, "version", MoepsBot.VERSION, "name", MoepsBot.NAME);
    }

    public static String replace(String string, String... replacements) {
        for (int i = 0; i+1 < replacements.length; i+=2) {
            string = string.replace("%" + replacements[i] + "%", replacements[i+1]);
        }
        return string;
    }

    public static boolean hasRole(User user, Server server, List<String> roles) {
        for (Role role : user.getRoles(server)) {
            if (roles.contains(role.getName()) || roles.contains(role.getIdAsString())) {
                return true;
            }
        }
        return false;
    }

    public static ServerTextChannel getTextChannel(Server server, String channelStr) {
        return server.getTextChannelById(channelStr).orElseGet(() -> {
            List<ServerTextChannel> channels = server.getTextChannelsByNameIgnoreCase(channelStr);
            return channels.isEmpty() ? null : channels.get(0);
        });
    }

    public static CompletableFuture<Paste> uploadToPaste(Message message, List<MessageAttachment> attachments) {
        CompletableFuture<Paste> future = new CompletableFuture<>();

        executor.submit(() -> {
            try {
                URL url = new URL("https://api.paste.gg/v1/pastes");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestProperty("User-Agent", MoepsBot.NAME + " " + MoepsBot.VERSION);
                con.setDoOutput(true);
                con.setRequestMethod("POST");

                JsonObject json = new JsonObject();

                List<String> names = new ArrayList<>();
                JsonArray files = new JsonArray();

                attachments.stream().filter(Utils::isTextFile).forEach(a -> {
                    names.add(a.getFileName());
                    JsonObject fileJson = new JsonObject();
                    fileJson.addProperty("name", a.getFileName());

                    JsonObject contentJson = new JsonObject();
                    contentJson.addProperty("format", "text");
                    try {
                        contentJson.addProperty("value", readInputStream(a.asInputStream()));
                        fileJson.add("content", contentJson);

                        files.add(fileJson);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                json.addProperty("name", String.join(", ", names));
                json.addProperty("description", "Posted by " + message.getAuthor().getDiscriminatedName()
                        + " in #" + ((ServerTextChannel) message.getChannel()).getName()
                        + " of " + ((ServerTextChannel) message.getChannel()).getServer().getName()
                );
                json.addProperty("expires", OffsetDateTime.now().plusDays(30).format(DateTimeFormatter.ISO_DATE_TIME));

                json.add("files", files);

                byte[] out = json.toString().getBytes(Charset.forName("UTF-8"));
                int length = out.length;
                con.setFixedLengthStreamingMode(length);
                con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                con.connect();
                OutputStream os = con.getOutputStream();
                os.write(out);

                JsonObject response = (JsonObject) JsonParser.parseString(getInputString(con));

                if (response.get("status").getAsString().equals("success")) {
                    JsonObject result = response.getAsJsonObject("result");
                    Paste paste = new Paste(result);
                    MoepsBot.log(Level.INFO, "Created paste " + paste.getName()
                            + " for " + message.getAuthor().getDiscriminatedName()
                            + " in #" + ((ServerTextChannel) message.getChannel()).getName()
                            + " of " + ((ServerTextChannel) message.getChannel()).getServer().getName()
                            + " with link " + paste.getLink()
                            + " and deletion key " + paste.getDeletionKey());
                    future.complete(paste);
                } else {
                    future.completeExceptionally(new Exception(response.get("error").getAsString() + " error while pasting! " + response.get("message").getAsString()));
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private static boolean isTextFile(MessageAttachment attachment) {
        if (attachment.isImage() || attachment.isSpoiler() || !attachment.getFileName().contains(".")) {
            return false;
        }
        String fileType = attachment.getFileName().substring(attachment.getFileName().lastIndexOf('.') + 1);
        switch (fileType.toLowerCase(Locale.ROOT)) {
            case "md":
            case "txt":
            case "yml":
            case "log":
            case "conf":
            case "java":
            case "properties":
                return true;
        }
        return false;
    }

    private static String getInputString(HttpURLConnection con) throws IOException {
        return readInputStream(con.getInputStream());
    }

    private static String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder msg = new StringBuilder();
        BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = in.readLine()) != null) {
            if (msg.length() != 0) {
                msg.append("\n");
            }
            msg.append(line);
        }
        in.close();
        return msg.toString();
    }

    public static void deletePaste(Paste paste) {
        executor.submit(() -> {
            try {
                URL url = new URL("https://api.paste.gg/v1/pastes/" + paste.getId());
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestProperty("User-Agent", MoepsBot.NAME + " " + MoepsBot.VERSION);
                con.setRequestProperty("Authorization", "Key " + paste.getDeletionKey());
                con.setRequestMethod("DELETE");
                con.connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void shutdown() {
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static class Paste {

        private final String id;
        private final String name;
        private final String deletionKey;

        public Paste(JsonObject result) {
            id = result.get("id").getAsString();
            name = result.get("name").getAsString();
            deletionKey = result.get("deletion_key").getAsString();
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getLink() {
            return "https://paste.gg/" + id;
        }

        public String getDeletionKey() {
            return deletionKey;
        }
    }
}
