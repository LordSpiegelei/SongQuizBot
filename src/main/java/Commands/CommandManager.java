package Commands;

import Core.Main;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;

public class CommandManager extends ListenerAdapter {

    @Override
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
        //Commands

        // Check if cmd stats with prefix
        if(event.getMessage().getContentRaw().startsWith(Main.prefix)){

            String[] args = event.getMessage().getContentRaw().split(" ");
            String commandName = args[0].replace(Main.prefix, "");
            TextChannel channel = event.getChannel();

            // Command Help
            if(commandName.equalsIgnoreCase("help")){

                channel.sendMessage("HELP").queue();

            }else if(commandName.equalsIgnoreCase("start")){

                Start.execute(event);

            }else if(commandName.equalsIgnoreCase("stop")){

                Stop.execute(event);

            }

        }
    }
}
