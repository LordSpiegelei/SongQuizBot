package Manager;

import Commands.Start;
import Core.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ReactionManager extends ListenerAdapter {

    public static HashMap<Guild, List<User>> pressedUsersList = new HashMap<>();

    @Override
    public void onGuildMessageReactionAdd(@Nonnull GuildMessageReactionAddEvent event) {

        if(event.getUser().isBot())
            return;

        Guild guild = event.getGuild();

        // Check if message is registered
        if(GameManager.activeSongMessages.containsKey(guild) && GameManager.activeSongMessages.get(guild).getId().equals(event.getMessageId())){

            // Check if reaction is registered
            if(event.getReactionEmote().getName().equals("\uD83D\uDD34")){

                if(GameManager.players.containsKey(guild) && !GameManager.getPlayer(guild).isPaused()){

                    // Check if user already pressed
                    if(pressedUsersList.containsKey(guild) && pressedUsersList.get(guild).contains(event.getUser()))
                        return;

                    // Check if user is mod
                    if(Start.gameUserMods.containsKey(guild) && Start.gameUserMods.get(guild).contains(event.getMember()))
                        return;

                    // Add user to pressed list
                    List<User> usersList = new ArrayList<>();
                    if(pressedUsersList.containsKey(guild))
                        usersList = pressedUsersList.get(guild);

                    usersList.add(event.getUser());

                    pressedUsersList.put(guild, usersList);

                    // Pause player
                    GameManager.getPlayer(guild).setPaused(true);

                    try {
                        // Remove reaction
                        GameManager.activeSongMessages.get(guild).clearReactions().queue();
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                    // Add joker emote if user has jokers
                    if(Start.gameUserMods.containsKey(guild) && Start.userJokerAmount.get(guild).get(event.getUser()) >= 1)
                        GameManager.activeSongMessages.get(guild).addReaction(Main.jokerEmote).queue();

                    // Add Role to player
                    Role activeRole = event.getGuild().getRolesByName("SongQuiz Active Users", false).get(0);
                    guild.addRoleToMember(event.getMember(), activeRole).queue();

                    // Cancel all other timers
                    GameManager.publicSchedule.cancel();
                    GameManager.publicSchedule.purge();
                    GameManager.publicSchedule = new Timer();

                    // Time to respond
                    GameManager.publicSchedule.schedule(
                            new TimerTask() {
                                @Override
                                public void run() {

                                    // Time to respond ran out
                                    guild.removeRoleFromMember(event.getMember(), activeRole).queue();

                                    // Resume Player
                                    GameManager.getPlayer(guild).setPaused(false);

                                    try {
                                        // Add reaction back
                                        GameManager.activeSongMessages.get(guild).addReaction(Main.reactionEmote).queue();
                                    }catch (Exception e){
                                        e.printStackTrace();
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

                                                    // Check if song is still playing and round is still 1
                                                    if (GameManager.getPlayer(guild).isPaused() == false) {
                                                        GameManager.manageNextRound(guild, false, "The timer ran out!");
                                                    }

                                                }
                                            },
                                            remainingTime
                                    );
                                }
                            },
                            Main.timeToWrite
                    );

                }
            }else if(event.getReactionEmote().getName().equals(Main.jokerEmote)){
                // Joker emote got pressed

                // Check if jokers are enabled
                if(!Start.gameUserMods.containsKey(guild))
                    return;

                Role activeRole = event.getGuild().getRolesByName("SongQuiz Active Users", false).get(0);

                // Check if user has active role
                if(!event.getMember().getRoles().contains(activeRole))
                    return;

                Role passiveModRole = event.getGuild().getRolesByName("SongQuiz passive Mod", false).get(0);

                // Check if user already pressed joker
                if(event.getMember().getRoles().contains(passiveModRole))
                    return;

                // Check if user got jokers left
                if(Start.userJokerAmount.get(guild).get(event.getUser()) >= 1){

                    // Add passive mod role to user
                    guild.addRoleToMember(event.getMember(), passiveModRole).queue();

                    // Remove joker from user
                    int currentJokers = Start.userJokerAmount.get(guild).get(event.getUser());

                    HashMap<User, Integer> guildMap = Start.userJokerAmount.get(guild);
                    guildMap.put(event.getUser(), currentJokers - 1);
                    Start.userJokerAmount.put(guild, guildMap);

                    // Send info message
                    EmbedBuilder builder = new EmbedBuilder().setColor(Color.BLUE);
                    builder.setDescription(event.getUser().getAsMention() + " *used a joker!* [**" + (currentJokers - 1) + "**/" + Start.gameJokerAmount.get(guild) + " left]\n" +
                            "*You can now see the moderator channel and got* ``60`` *seconds to guess!*");

                    event.getChannel().sendMessage(builder.build()).queue();

                    // Manage mods
                    if(Start.gameUserMods.containsKey(guild)) {

                        // Get mod mentions
                        String modMentions = "";

                        // Add each moderator role
                        for(Member member : Start.gameUserMods.get(guild)){
                            guild.addRoleToMember(member, passiveModRole).queue();

                            modMentions += " " + member.getAsMention();
                        }

                        // Send mod info message
                        EmbedBuilder modMsgBuilder = new EmbedBuilder().setColor(Color.GREEN);
                        modMsgBuilder.setDescription(event.getUser().getAsMention() + " *used a joker!* " + modMentions + " **please make your guesses!**");

                        Start.moderatorChannel.get(guild).sendMessage(modMsgBuilder.build()).queue();

                        // Add each mod permission to write
                        Role activeModRole = guild.getRolesByName("SongQuiz active Mod", false).get(0);
                        for (Member member : Start.gameUserMods.get(guild)) {
                            // Add active mod role to user
                            guild.addRoleToMember(member, activeModRole).queue();
                        }
                    }

                    // Cancel all other timers
                    GameManager.publicSchedule.cancel();
                    GameManager.publicSchedule.purge();
                    GameManager.publicSchedule = new Timer();

                    // Time to respond
                    GameManager.publicSchedule.schedule(
                            new TimerTask() {
                                @Override
                                public void run() {

                                    // Time to respond ran out
                                    guild.removeRoleFromMember(event.getMember(), activeRole).queue();
                                    guild.removeRoleFromMember(event.getMember(), passiveModRole).queue();

                                    // Resume Player
                                    GameManager.getPlayer(guild).setPaused(false);

                                    try {
                                        // Remove reactions
                                        GameManager.activeSongMessages.get(guild).clearReactions().queue();
                                        // Add reaction back
                                        GameManager.activeSongMessages.get(guild).addReaction(Main.reactionEmote).queue();
                                    }catch (Exception e){
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

                                                    // Check if song is still playing and round is still 1
                                                    if (GameManager.getPlayer(guild).isPaused() == false) {
                                                        GameManager.manageNextRound(guild, false, "The timer ran out!");
                                                    }

                                                }
                                            },
                                            remainingTime
                                    );
                                }
                            },
                            (Main.timeToWrite * 2)
                    );
                }
            }
        }
    }
}
