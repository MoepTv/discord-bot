package tv.moep.discord.bot;

/*
 * MoepsBot
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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.Getter;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import tv.moep.discord.bot.commands.Command;
import tv.moep.discord.bot.commands.CommandSender;
import tv.moep.discord.bot.commands.ListCommand;
import tv.moep.discord.bot.managers.JoinLeaveManager;
import tv.moep.discord.bot.managers.MessageManager;
import tv.moep.discord.bot.managers.PrivateConversationManager;
import tv.moep.discord.bot.managers.StreamingManager;
import tv.moep.discord.bot.managers.TextChannelManager;
import tv.moep.discord.bot.managers.VoiceChannelManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.logging.Level;

@Getter
public class MoepsBot {
    private static final DateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");

    public static String VERSION = "Unknown Version";
    public static String NAME = MoepsBot.class.getSimpleName();

    private Config config = ConfigFactory.load();

    private Map<String, Command> commands = new HashMap<>();

    private DiscordApi discordApi;

    private StreamingManager streamingManager;
    private VoiceChannelManager voiceChannelManager;
    private PrivateConversationManager privateConversationManager;
    private JoinLeaveManager joinLeaveManager;
    private TextChannelManager textChannelManager;
    private MessageManager messageManager;

    public static void main(String[] args) {
        try {
            Properties p = new Properties();
            InputStream is = MoepsBot.class.getResourceAsStream("/META-INF/app.properties");
            if (is != null) {
                p.load(is);
                NAME = p.getProperty("application.name", "");
                VERSION = p.getProperty("application.version", "");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        log(Level.INFO, "Starting " + NAME + " v" + VERSION);
        new MoepsBot();
    }

    // TODO: Proper logging
    public static void log(Level level, String message) {
        System.out.println(TIME_FORMAT.format(new Date(System.currentTimeMillis())) + " " + level.getName() + " " + message);
    }

    public MoepsBot() {
        loadConfig();
        registerCommand("reload", Permission.OPERATOR, (sender, args) -> {
            loadConfig();
            sender.sendMessage("Config reloaded!");
            return true;
        });
        registerCommand("stop", Permission.OPERATOR, (sender, args) -> {
            sender.sendMessage("Stopping " + NAME + " v" + VERSION);
            synchronized (MoepsBot.this) {
                this.notifyAll();
            }
            return true;
        });
        registerCommand(new ListCommand(this));
        synchronized (MoepsBot.this) {
            try {
                wait();
            } catch (InterruptedException ignored) { }
            log(Level.INFO, "Shutting down " + NAME + " v" + VERSION);
            discordApi.disconnect();
            try {
                wait(1000);
            } catch (InterruptedException ignored) { }
        }
        log(Level.INFO, "Bye!");
    }

    public static String replacePlaceholders(String response) {
        return replace(response, "version", VERSION, "name", NAME);
    }

    public static String replace(String string, String... replacements) {
        for (int i = 0; i+1 < replacements.length; i+=2) {
            string = string.replace("%" + replacements[i] + "%", replacements[i+1]);
        }
        return string;
    }

    private void loadConfig() {
        if (discordApi != null) {
            discordApi.disconnect();
        }
        config = getConfig("bot");
        try {
            discordApi = new DiscordApiBuilder().setToken(getConfig().getString("discord.token")).login().join();
            voiceChannelManager = new VoiceChannelManager(this);
            streamingManager = new StreamingManager(this);
            privateConversationManager = new PrivateConversationManager(this);
            joinLeaveManager = new JoinLeaveManager(this);
            textChannelManager = new TextChannelManager(this);
            messageManager = new MessageManager(this);
            log(Level.INFO, "You can invite the bot by using the following url: " + discordApi.createBotInvite());
        } catch (CompletionException e) {
            log(Level.SEVERE, "Error connecting to discord! Is the token correct?");
        }
    }

    public void saveResource(String name) {
        InputStream inputStream = getClass().getResourceAsStream("/" + name);
        if (inputStream != null) {
            File file = new File(name);
            if (!file.exists()) {
                try {
                    Files.copy(inputStream, file.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            log(Level.WARNING, "No resource " + name + " found!");
        }
    }

    public Config getConfig(String name) {
        saveResource(name + ".conf");
        return ConfigFactory.parseFile(new File(name + ".conf")).withFallback(ConfigFactory.load(name + ".conf"));
    }

    private void registerCommand(String usage, Permission permission, BiFunction<CommandSender, String[], Boolean> execute) {
        registerCommand(new Command(usage, permission) {
            @Override
            public boolean execute(CommandSender sender, String[] args) {
                return execute.apply(sender, args);
            }
        });
    }

    private void registerCommand(Command command) {
        commands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            commands.putIfAbsent(alias, command);
        }
    }

    private Command getCommand(String name) {
        return commands.get(name.toLowerCase());
    }

    public boolean runCommand(CommandSender sender, String commandStr) {
        String[] args = commandStr.split(" ");
        if (args.length == 0) {
            return false;
        }

        Command command = getCommand(args[0]);
        if (command == null) {
            return false;
        }

        sender.confirm();

        if (!command.runCommand(sender, Arrays.copyOfRange(args, 1, args.length))) {
            sender.sendMessage("Usage: " + command.getName() + " " + command.getUsage());
        }
        return true;
    }
}
