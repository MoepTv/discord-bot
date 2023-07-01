package tv.moep.discord.bot.commands;

import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.interaction.SlashCommandInteraction;
import org.javacord.api.interaction.callback.InteractionImmediateResponseBuilder;
import tv.moep.discord.bot.MoepsBot;
import tv.moep.discord.bot.Permission;
import tv.moep.discord.bot.Utils;

import java.util.concurrent.CompletableFuture;

public class SlashCommandSender extends DiscordSender {
    private final SlashCommandInteraction interaction;
    private boolean onlySender = false;

    public SlashCommandSender(MoepsBot bot, SlashCommandInteraction interaction) {
        super(bot, interaction.getUser(), interaction.getChannel().orElse(null), interaction.getServer().orElse(null));
        this.interaction = interaction;
    }

    @Override
    public CompletableFuture<Message> sendNaturalMessage(String message) {
        if (interaction.getChannel().isPresent()) {
            return interaction.getChannel().get().sendMessage(message);
        }
        return interaction.getUser().sendMessage(message);
    }

    @Override
    public CompletableFuture<Message> sendMessage(String message) {
        TextChannel channel = interaction.getChannel().orElse(getUser().openPrivateChannel().join());
        CompletableFuture<Message> messageFuture = channel.sendMessage(createEmbed(message));
        if (!(channel instanceof ServerTextChannel)
                || getBot().getTextChannelManager().has((ServerTextChannel) channel, "emojiRemoval")) {
            messageFuture.thenAccept(m -> m.addReaction(MessageReaction.REMOVE));
        }
        return messageFuture;
    }

    private EmbedBuilder createEmbed(String message) {
        EmbedBuilder embed = new EmbedBuilder()
                .setFooter("Answer to " + getUser().getDiscriminatedName())
                .setColor(Utils.getRandomColor());
        if (message.length() > 256) {
            embed.setDescription(message);
        } else {
            embed.setTitle(message);
        }
        return embed;
    }

    @Override
    public CompletableFuture<Message> sendMessage(String title, String message) {
        TextChannel channel = interaction.getChannel().orElse(getUser().openPrivateChannel().join());
        CompletableFuture<Message> messageFuture = channel.sendMessage(createEmbed(title, message));
        if (!(channel instanceof ServerTextChannel)
                || getBot().getTextChannelManager().has((ServerTextChannel) channel, "emojiRemoval")) {
            messageFuture.thenAccept(m -> m.addReaction(MessageReaction.REMOVE));
        }
        return messageFuture;
    }

    private EmbedBuilder createEmbed(String title, String message) {
        return new EmbedBuilder()
                .setAuthor(getBot().getDiscordApi().getYourself())
                .setTitle(title)
                .setDescription(message)
                .setFooter("Answer to " + getUser().getDiscriminatedName())
                .setColor(Utils.getRandomColor());
    }

    @Override
    public void sendReply(String message) {
        InteractionImmediateResponseBuilder respose = interaction.createImmediateResponder()
                .addEmbed(createEmbed(message));
        if (onlySender) {
            respose.setFlags(MessageFlag.EPHEMERAL);
        }
        respose.respond();
    }

    @Override
    public void sendReply(String title, String message) {
        InteractionImmediateResponseBuilder respose = interaction.createImmediateResponder()
                .addEmbed(createEmbed(title, message));
        if (onlySender) {
            respose.setFlags(MessageFlag.EPHEMERAL);
        }
        respose.respond();
    }

    @Override
    public void confirm() {
        InteractionImmediateResponseBuilder respose = interaction.createImmediateResponder()
                .append(MessageReaction.CONFIRM);
        if (onlySender) {
            respose.setFlags(MessageFlag.EPHEMERAL);
        }
        respose.respond();
    }

    @Override
    public String getName() {
        return getUser().getName();
    }

    @Override
    public void removeSource() {
        this.onlySender = true;
    }
}
