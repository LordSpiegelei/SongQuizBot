package Commands;

import Manager.GameManager;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

public class Stop {

    public static void execute(GuildMessageReceivedEvent event){
        event.getGuild().getAudioManager().closeAudioConnection();

        if(GameManager.activeRoundPlaying.containsKey(event.getGuild()) && GameManager.activeRoundPlaying.get(event.getGuild()) > 0){

            GameManager.finishGame(event.getGuild());

            GameManager.activeRoundPlaying.put(event.getGuild(), 0);
            GameManager.getManager(event.getGuild()).purgeQueue();
            GameManager.getPlayer(event.getGuild()).stopTrack();

            // Delete mod channel
            if(Start.moderatorChannel.containsKey(event.getGuild())) {
                Start.moderatorChannel.get(event.getGuild()).delete().queue();
                Start.moderatorChannel.remove(event.getGuild());
            }


            event.getChannel().sendMessage("Successfully stopped all active games!").queue();


        }else
            event.getChannel().sendMessage("There were no active games found on this channel!").queue();
    }

}
