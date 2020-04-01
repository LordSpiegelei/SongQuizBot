package Manager;

import Commands.Start;
import Core.Main;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.*;

public class MessageManager extends ListenerAdapter {

    @Override
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {

        if(event.getAuthor().isBot())
            return;

        Guild guild = event.getGuild();

        if(GameManager.activeSongMessages.containsKey(guild) && event.getChannel().equals(GameManager.activeSongMessages.get(guild).getTextChannel())){
            // Channel is game channel

            Role activeRole = event.getGuild().getRolesByName("SongQuiz Active Users", false).get(0);

            if(GameManager.players.containsKey(guild) && event.getMember().getRoles().contains(activeRole)){

                // Remove passive mod role from user
                if(Start.gameUserMods.containsKey(guild)) {
                    Role passiveModRole = guild.getRolesByName("SongQuiz passive Mod", false).get(0);
                    // Remove each mod permission to write
                    if (event.getMember().getRoles().contains(passiveModRole)) {
                        // Remove active mod role from user
                        guild.removeRoleFromMember(event.getMember(), passiveModRole).queue();
                    }
                }

                // Remove user role
                guild.removeRoleFromMember(event.getMember(), activeRole).queue();

                String rawVideoTitle = GameManager.getPlayer(guild).getPlayingTrack().getInfo().title.split("\\(")[0].split("\\[")[0];

                // Check artist
                String artistName;
                if(Start.gameSettings.get(guild).get("ignoreFeatures") == true)
                    artistName = rawVideoTitle.split("-")[0].split("ft.")[0].split("feat.")[0];
                else
                    artistName = rawVideoTitle.split("-")[0];

                boolean correctArtist = true;
                for(String titleElement : artistName.split(" ")){
                    // Skip if is nen letter or number
                    if(titleElement.replaceAll("\\W","").replaceAll("x", "").equalsIgnoreCase("")) // .replaceAll("-", "").replaceAll("&", "").replaceAll("|", "")
                        continue;

                    // Check if message contains element
                    if(!Arrays.asList(event.getMessage().getContentRaw().replaceAll(" ", "_").replaceAll("\\W", "").toLowerCase().replaceAll("ft", "").replaceAll("feat", "").split("_")).contains(titleElement.replaceAll("\\W", "").toLowerCase().replaceAll("ft", "").replaceAll("feat", ""))) // .replaceAll("'", "").replaceAll("\"", "").replaceAll(",", "").replaceAll(":", "")
                        correctArtist = false;
                }

                // Check song title
                String songTitle;
                boolean correctTitle = true;

                // Check if video title is split by "-"
                if(rawVideoTitle.split("-").length >= 2) {
                    // Title is split

                    // Check if features are ignored
                    if(Start.gameSettings.get(guild).get("ignoreFeatures") == true)
                        songTitle = rawVideoTitle.split("-")[1].split("ft.")[0].split("feat.")[0];
                    else
                        songTitle = rawVideoTitle.split("-")[1];

                }else{
                    // Title is not split

                    // Check if features are ignored
                    if(Start.gameSettings.get(guild).get("ignoreFeatures") == true)
                        songTitle = rawVideoTitle.split("ft.")[0].split("feat.")[0];
                    else
                        songTitle = rawVideoTitle;

                }

                // Check song title
                for (String titleElement : songTitle.split(" ")) {
                    // Skip if is nen letter or number
                    if(titleElement.replaceAll("\\W","").replaceAll("x", "").equalsIgnoreCase("")) // .replaceAll("-", "").replaceAll("&", "").replaceAll("|", "")
                        continue;

                    // Check if message contains element
                    if (!Arrays.asList(event.getMessage().getContentRaw().replaceAll(" ", "_").replaceAll("\\W", "").toLowerCase().replaceAll("ft", "").replaceAll("feat", "").split("_")).contains(titleElement.replaceAll("\\W", "").toLowerCase().replaceAll("ft", "").replaceAll("feat", ""))) // .replaceAll("'", "").replaceAll("\"", "").replaceAll(",", "").replaceAll(":", "")
                        correctTitle = false;
                }

                // Check if user wrote song title
                if((Start.gameSettings.get(guild).get("guessSongTitle") == true && Start.gameSettings.get(guild).get("guessArtist") == false && correctTitle == true) ||
                        (Start.gameSettings.get(guild).get("guessSongTitle") == false && Start.gameSettings.get(guild).get("guessArtist") == true && correctArtist == true) ||
                        (Start.gameSettings.get(guild).get("guessSongTitle") == true && Start.gameSettings.get(guild).get("guessArtist") == true && correctTitle == true && correctArtist == true)){

                    // Add win to user
                    HashMap<User, Integer> guildMap = new HashMap<>();
                    if(GameManager.gameStatistics.containsKey(guild))
                        guildMap = GameManager.gameStatistics.get(guild);

                    int usersWins = 0;
                    if(guildMap.containsKey(event.getAuthor()))
                        usersWins = guildMap.get(event.getAuthor());

                    usersWins++;

                    guildMap.put(event.getAuthor(), usersWins);
                    GameManager.gameStatistics.put(guild, guildMap);

                    // Check if win limit is reached
                    if(GameManager.registeredWinLimit.get(guild) == usersWins){
                        // Win limit is reached
                        GameManager.finishGame(guild);

                    }else {

                        // GameManager next round
                        GameManager.manageNextRound(guild, true, "**The user** " + event.getAuthor().getAsMention() + " **guessed the right song title!**");
                    }

                }else {

                    // Check if all users pressed the button
                    if (ReactionManager.pressedUsersList.get(guild).size() == GameManager.regGameUsers.get(guild).size()) {
                        // All users pressed the button

                        // GameManager new round
                        GameManager.manageNextRound(guild, false, "All users have guessed!");
                    } else {
                        // Not all users pressed the button

                        //TODO: Send wrong title message

                        // Resume Player
                        GameManager.getPlayer(guild).setPaused(false);

                        try {
                            // Add reaction back
                            GameManager.activeSongMessages.get(guild).addReaction(Main.reactionEmote).queue();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        // Remove each mod permission to write
                        if(Start.gameUserMods.containsKey(guild)) {
                            Role activeModRole = guild.getRolesByName("SongQuiz active Mod", false).get(0);
                            for (Member member : Start.gameUserMods.get(guild)) {
                                if(member.getRoles().contains(activeModRole)) {
                                    // Remove active mod role from user
                                    guild.removeRoleFromMember(member, activeModRole).queue();
                                }
                            }
                        }


                        // Cancel all other timers
                        GameManager.publicSchedule.cancel();
                        GameManager.publicSchedule.purge();
                        GameManager.publicSchedule = new Timer();

                        // Calculate remaining time
                        long remainingTime;
                        if(Start.gameSettings.get(guild).get("quickMode"))
                            remainingTime = (GameManager.calculatePlayingTime(guild) - (GameManager.getPlayer(guild).getPlayingTrack().getPosition() - ((GameManager.getPlayer(guild).getPlayingTrack().getDuration() / 2) - (GameManager.calculatePlayingTime(guild) / 2))));
                        else
                            remainingTime = (GameManager.calculatePlayingTime(guild) - GameManager.getPlayer(guild).getPlayingTrack().getPosition());

                        // Time to next song
                        GameManager.publicSchedule.schedule(
                                new TimerTask() {
                                    @Override
                                    public void run() {

                                        // Check if song is still playing
                                        if (GameManager.getPlayer(guild).isPaused() == false) {
                                            GameManager.manageNextRound(guild, false, "The timer ran out!");

                                        }

                                    }
                                },
                                remainingTime
                        );

                    }
                }
            }
        }else if(Start.moderatorChannel.containsKey(guild) && Start.moderatorChannel.get(guild).equals(event.getChannel())){
            // Channel is moderator channel

            // Check if user is moderator
            if(!Start.gameUserMods.get(guild).contains(event.getMember()))
                return;

            // Check if user has active mod role
            Role activeModRole = guild.getRolesByName("SongQuiz active Mod", false).get(0);
            // Remove each mod permission to write
            if(event.getMember().getRoles().contains(activeModRole)) {
                // Remove active mod role from user
                guild.removeRoleFromMember(event.getMember(), activeModRole).queue();
            }

        }
    }
}
