package tv.moep.discord.bot;

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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.Nameable;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.permission.PermissionsBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.team.TeamMember;
import org.javacord.api.entity.user.User;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.interaction.SlashCommandBuilder;
import org.javacord.api.interaction.SlashCommandInteraction;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;
import tv.moep.discord.bot.commands.Command;
import tv.moep.discord.bot.commands.CommandSender;
import tv.moep.discord.bot.commands.DeleteCommand;
import tv.moep.discord.bot.commands.ListCommand;
import tv.moep.discord.bot.commands.RandomCommand;
import tv.moep.discord.bot.commands.SlashCommandSender;
import tv.moep.discord.bot.managers.InviteManager;
import tv.moep.discord.bot.managers.JoinLeaveManager;
import tv.moep.discord.bot.managers.MessageManager;
import tv.moep.discord.bot.managers.PrivateConversationManager;
import tv.moep.discord.bot.managers.RoleManager;
import tv.moep.discord.bot.managers.StreamingManager;
import tv.moep.discord.bot.managers.TextChannelManager;
import tv.moep.discord.bot.managers.UserChangeManager;
import tv.moep.discord.bot.managers.VoiceChannelManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MoepsBot {
    private static final DateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");

    public static String VERSION = "Unknown Version";
    public static String NAME = MoepsBot.class.getSimpleName();
    public static Random RANDOM = new Random();

    private Config config = ConfigFactory.load();

    private Map<String, Command> commands = new HashMap<>();

    private ScheduledExecutorService scheduler;

    private DiscordApi discordApi;

    private StreamingManager streamingManager = null;
    private VoiceChannelManager voiceChannelManager;
    private PrivateConversationManager privateConversationManager;
    private JoinLeaveManager joinLeaveManager;
    private UserChangeManager userChangeManager;
    private TextChannelManager textChannelManager;
    private MessageManager messageManager;
    private RoleManager roleManager;
    private InviteManager inviteManager;

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

    public static void log(Level level, String message, Throwable throwable) {
        log(level, message);
        throwable.printStackTrace();
    }

    public MoepsBot() {
        loadConfig();
        registerCommand("reload", Permission.OPERATOR, false, (sender, args) -> {
            loadConfig();
            sender.sendReply("Config reloaded!");
            return true;
        });
        registerCommand("stop", Permission.OPERATOR, false, (sender, args) -> {
            sender.sendReply("Stopping " + NAME + " v" + VERSION);
            notifyOperators("Shutdown triggered by " + sender.getName() + " (" + NAME + " v" + VERSION + ")");
            synchronized (MoepsBot.this) {
                this.notifyAll();
            }
            return true;
        });
        registerCommand(new ListCommand(this));
        registerCommand(new RandomCommand());
        registerCommand(new DeleteCommand(this));
        notifyOperators("Started " + NAME + " v" + VERSION);
        log(Level.INFO, "Joined the servers " + discordApi.getServers().stream().map(Nameable::getName).collect(Collectors.joining(", ")));
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
        Utils.shutdown();
        log(Level.INFO, "Bye!");
        System.exit(0);
    }

    public void notifyOperators(String message) {
        List<CompletableFuture<User>> operators = new ArrayList<>();
        getDiscordApi().getOwner().ifPresent(cf -> operators.add(cf));
        try {
            getDiscordApi().requestTeam().get().ifPresent(team -> {
                for (TeamMember teamMember : team.getTeamMembers()){
                    operators.add(teamMember.requestUser());
                }
            });
        } catch (ExecutionException | InterruptedException e) {
            log(Level.SEVERE, e.getMessage());
        }
        for (String id : getConfig().getStringList("discord.operators")) {
            if (id.contains("#")) {
                getDiscordApi().getCachedUserByDiscriminatedName(id).ifPresent(u -> operators.add(CompletableFuture.completedFuture(u)));
            } else {
                operators.add(getDiscordApi().getUserById(id));
            }
        }
        for (CompletableFuture<User> future : operators) {
            try {
                future.get().sendMessage(message).get();
            } catch (InterruptedException | ExecutionException ignored) {}
        }
    }

    private void loadConfig() {
        if (discordApi != null) {
            discordApi.disconnect();
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log(Level.SEVERE, "Error while shutting down scheduler!", e);
            }
        }
        config = getConfig("bot");
        try {
            discordApi = new DiscordApiBuilder().setToken(getConfig().getString("discord.token"))
                    .setAllIntentsExcept(
                            Intent.DIRECT_MESSAGE_TYPING,
                            Intent.DIRECT_MESSAGE_TYPING,
                            Intent.GUILD_WEBHOOKS,
                            Intent.GUILD_INTEGRATIONS,
                            Intent.GUILD_BANS
                    )
                    .login().join();

            scheduler = Executors.newScheduledThreadPool(1);

            voiceChannelManager = new VoiceChannelManager(this);
            if (streamingManager == null) {
                streamingManager = new StreamingManager(this);
            }
            try {
                streamingManager.reload();
            } catch (Exception e) {
                log(Level.SEVERE, "Error while reloading streaming manager!", e);
            }
            privateConversationManager = new PrivateConversationManager(this);
            joinLeaveManager = new JoinLeaveManager(this);
            userChangeManager = new UserChangeManager(this);
            textChannelManager = new TextChannelManager(this);
            messageManager = new MessageManager(this);
            roleManager = new RoleManager(this);
            inviteManager = new InviteManager(this);
            log(Level.INFO, "You can invite the bot by using the following url: "
                    + discordApi.createBotInvite(
                            new PermissionsBuilder()
                                    .setAllowed(
                                            PermissionType.MANAGE_MESSAGES,
                                            PermissionType.READ_MESSAGE_HISTORY,
                                            PermissionType.VIEW_CHANNEL,
                                            PermissionType.SEND_MESSAGES,
                                            PermissionType.ADD_REACTIONS,
                                            PermissionType.MANAGE_ROLES,
                                            PermissionType.MANAGE_CHANNELS,
                                            PermissionType.MOVE_MEMBERS,
                                            PermissionType.EMBED_LINKS)
                                    .build()));
        } catch (CompletionException e) {
            log(Level.SEVERE, "Error connecting to discord! Is the token correct?", e);
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
        Config fallbackConfig;
        try {
            fallbackConfig = ConfigFactory.parseResourcesAnySyntax(name + ".conf");
        } catch (ConfigException e) {
            log(Level.SEVERE, "Error while loading " + name + ".conf fallback config!", e);
            fallbackConfig = ConfigFactory.empty("Empty " + name + ".conf fallback due to loading error: " + e.getMessage());
        }
        try {
            return ConfigFactory.parseFile(new File(name + ".conf")).withFallback(fallbackConfig);
        } catch (ConfigException e) {
            log(Level.SEVERE, "Error while loading " + name + ".conf config!", e);
            return fallbackConfig;
        }
    }

    public <T extends CommandSender> Command<T> registerCommand(String usage, Permission permission, BiFunction<T, String[], Boolean> execute, String... aliases) {
        return registerCommand(usage, permission, true, execute, aliases);
    }

    public <T extends CommandSender> Command<T> registerCommand(String usage, Permission permission, boolean requiresServer, BiFunction<T, String[], Boolean> execute, String... aliases) {
        Command<T> command = new Command<T>(usage, permission, aliases) {
            @Override
            public boolean execute(T sender, String[] args) {
                return execute.apply(sender, args);
            }
        };
        command.setRequiresServer(requiresServer);
        return registerCommand(command);
    }

    public <T extends CommandSender>Command<T> registerCommand(Command<T> command) {
        commands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            commands.putIfAbsent(alias, command);
        }
        // register slash command
        SlashCommandBuilder builder = SlashCommand.with(command.getName(), command.getUsage())
                .setEnabledInDms(!command.doesRequireServer());
        switch (command.getPermission()) {
            case USER, OPERATOR -> builder.setDefaultEnabledForEveryone();
            case ADMIN, OWNER -> builder.setDefaultEnabledForPermissions(PermissionType.ADMINISTRATOR);
        }
        if (!command.getSubCommands().isEmpty()) {
            SlashCommandOptionBuilder optionBuilder = new SlashCommandOptionBuilder()
                    .setType(SlashCommandOptionType.STRING)
                    .setName("subcommand")
                    .setDescription("Sub commands of this command")
                    .setAutocompletable(true);
            for (Map.Entry<String, Command> entry : command.getSubCommands().entrySet()) {
                optionBuilder.addChoice(entry.getValue().getName(), entry.getKey());
            }
            optionBuilder.addOption(SlashCommandOption.create(SlashCommandOptionType.STRING, "arguments", "Sub command arguments"));
            builder.addOption(optionBuilder.build());
        } else {
            builder.addOption(SlashCommandOption.create(SlashCommandOptionType.STRING, "arguments", command.getUsage()));
        }
        discordApi.addSlashCommandCreateListener(event -> {
            SlashCommandInteraction interaction = event.getSlashCommandInteraction();
            if (interaction.getCommandName().equals(command.getName())) {
                List<String> arguments = new ArrayList<>();
                interaction.getArgumentStringValueByName("subcommand").map(s -> s.split(" ")).ifPresent(s -> arguments.addAll(Arrays.asList(s)));
                interaction.getArgumentStringValueByName("arguments").map(s -> s.split(" ")).ifPresent(s -> arguments.addAll(Arrays.asList(s)));

                SlashCommandSender sender = new SlashCommandSender(this, interaction);
                runCommand(sender, command, arguments.toArray(new String[0]));
            }
        });
        builder.createGlobal(discordApi);
        return command;
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
        runCommand(sender, command, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    public boolean runCommand(CommandSender sender, Command command, String[] args) {
        if (!command.runCommand(sender, args)) {
            sender.removeSource();
            sender.sendReply("Usage: " + command.getName() + " " + command.getUsage());
            return false;
        }
        return true;
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public User getUser(String discordId) {
        if (discordId != null) {
            if (discordId.contains("#")) {
                return getDiscordApi().getCachedUserByDiscriminatedName(discordId).orElseGet(() -> {
                    for (Server server : getDiscordApi().getServers()) {
                        Optional<User> member = server.getMemberByDiscriminatedName(discordId);
                        if (member.isPresent()) {
                            return member.get();
                        }
                    }
                    return null;
                });
            } else {
                return getDiscordApi().getCachedUserById(discordId).orElseGet(() -> {
                    for (Server server : getDiscordApi().getServers()) {
                        Optional<User> member = server.getMemberById(discordId);
                        if (member.isPresent()) {
                            return member.get();
                        }
                    }
                    return null;
                });
            }
        }
        return null;
    }

    public Config getConfig() {
        return config;
    }

    public Map<String, Command> getCommands() {
        return commands;
    }

    public DiscordApi getDiscordApi() {
        return discordApi;
    }

    public StreamingManager getStreamingManager() {
        return streamingManager;
    }

    public VoiceChannelManager getVoiceChannelManager() {
        return voiceChannelManager;
    }

    public PrivateConversationManager getPrivateConversationManager() {
        return privateConversationManager;
    }

    public JoinLeaveManager getJoinLeaveManager() {
        return joinLeaveManager;
    }

    public UserChangeManager getUserChangeManager() {
        return userChangeManager;
    }

    public TextChannelManager getTextChannelManager() {
        return textChannelManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public RoleManager getRoleManager() {
        return roleManager;
    }

    public InviteManager getInviteManager() {
        return inviteManager;
    }
}
