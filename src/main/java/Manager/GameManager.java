package Manager;

import Commands.Start;
import Core.Main;
import audioCore.AudioPlayerSendHandler;
import audioCore.TrackManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;

import java.awt.*;
import java.time.Duration;
import java.util.*;
import java.util.List;

public class GameManager {

    public static HashMap<Guild, Message> activeSongMessages = new HashMap<>();
    public static HashMap<Guild, Integer> activeRoundPlaying = new HashMap<>();
    public static HashMap<Guild, List<User>> regGameUsers = new HashMap<>();
    public static HashMap<Guild, Integer> registeredWinLimit = new HashMap<>();

    public static HashMap<Guild, HashMap<User, Integer>> gameStatistics = new HashMap<>();

    public static AudioPlayerManager audioManager = new DefaultAudioPlayerManager();

    public static Map<Guild, Map.Entry<AudioPlayer, TrackManager>> players = new HashMap<>();

    public static Timer publicSchedule = new Timer();

    public static void startGame(Guild guild, Member messageAuthor){

        Role passiveRole = guild.getRolesByName("SongQuiz Passive Users", false).get(0);
        Role activeRole = guild.getRolesByName("SongQuiz Active Users", false).get(0);

        TextChannel regChannel = guild.getTextChannelById(Main.settingsConfig.get(guild.getId()).get("regChannel"));

        // Remove passive roles from each member
        for(Member member : guild.getMembersWithRoles(passiveRole)){
            guild.removeRoleFromMember(member, passiveRole).queue();
        }

        // Remove active roles from each member
        for(Member member : guild.getMembersWithRoles(activeRole)){
            guild.removeRoleFromMember(member, activeRole).queue();
        }

        // Add each member a role
        List<User> regUsers = new ArrayList<>();
        for(Member member : messageAuthor.getVoiceState().getChannel().getMembers()){
            // Skip bots
            if(member.getUser().isBot())
                continue;

            // Skip if user is mod
            if(Start.gameUserMods.containsKey(guild) && Start.gameUserMods.get(guild).contains(member))
                continue;

            guild.addRoleToMember(member, passiveRole).queue();

            // Add jokers to member
            if(Start.gameUserMods.containsKey(guild)){
                HashMap<User, Integer> guildMap = new HashMap<>();
                if(Start.userJokerAmount.containsKey(guild))
                    guildMap = Start.userJokerAmount.get(guild);

                guildMap.put(member.getUser(), Start.gameJokerAmount.get(guild));
                Start.userJokerAmount.put(guild, guildMap);
            }


            regUsers.add(member.getUser());
        }

        regGameUsers.put(guild, regUsers);

        //
        // Song Manager
        //

        // Write info message
        final int regUserCount = regUsers.size();
        String infoMsg = "**INFO:** Get ready! The game is starting in 10 seconds. [``" + regUserCount + "`` Users]";

        regChannel.sendMessage(infoMsg)
                .delay(Duration.ofSeconds(7))
                .flatMap(message -> message.editMessage("**INFO:** Get ready! The game is starting in 3 seconds. [``" + regUserCount + "`` Users]"))
                .delay(Duration.ofSeconds(1))
                .flatMap(message -> message.editMessage("**INFO:** Get ready! The game is starting in 2 seconds. [``" + regUserCount + "`` Users]"))
                .delay(Duration.ofSeconds(1))
                .flatMap(message -> message.editMessage("**INFO:** Get ready! The game is starting in 1 seconds. [``" + regUserCount + "`` Users]"))
                .delay(Duration.ofSeconds(1))
                .flatMap(message -> message.editMessage("**INFO:** The game is starting! Good Luck! [``" + regUserCount + "`` Users]"))
                .queue();


        // Manage new round message
        new Timer().schedule(
                new TimerTask() {
                    @Override
                    public void run() {

                        // Build song info message
                        EmbedBuilder startMsgBuilder = new EmbedBuilder().setColor(Color.YELLOW);

                        startMsgBuilder.setDescription("**ROUND 1**\n" +
                                "__Song__: **- - -**\n" +
                                "*Runtime: " + (calculatePlayingTime(guild) / 1000) + " seconds*");

                        // Add each user a win counter field
                        for(User user : regGameUsers.get(guild)) {
                            startMsgBuilder.addField(user.getName(), "**0** Win(s)", true);
                        }

                        // Send Message and waite a few seconds to begin
                        Message currentMsg = regChannel.sendMessage(startMsgBuilder.build()).complete();


                        // Shuffle playlist
                        getManager(guild).shuffleQueue();

                        // Check if quickMode is active
                        if(Start.gameSettings.get(guild).get("quickMode")) {
                            // Skip to middle
                            getPlayer(guild).getPlayingTrack().setPosition((getPlayer(guild).getPlayingTrack().getDuration() / 2) - (calculatePlayingTime(guild) / 2));
                        }

                        // Start song to play
                        getPlayer(guild).setPaused(false);

                        // Skip first song
                        getPlayer(guild).stopTrack();

                        // Add reaction emote
                        currentMsg.addReaction(Main.reactionEmote).queue();

                        activeSongMessages.put(guild, currentMsg);
                        activeRoundPlaying.put(guild, 1);

                        publicSchedule.schedule(
                                new TimerTask() {
                                    @Override
                                    public void run() {

                                        // Check if song is still playing and round is still 1
                                        if(getPlayer(guild).isPaused() == false && activeRoundPlaying.get(guild) == 1){
                                            manageNextRound(guild, false, "The timer ran out!");
                                        }

                                    }
                                },
                                calculatePlayingTime(guild) // Before: 20000
                        );

                    }
                },
                11000
        );



    }

    public static void manageNextRound(Guild guild, Boolean correctAnswer, String endReason){

        TextChannel regChannel = guild.getTextChannelById(Main.settingsConfig.get(guild.getId()).get("regChannel"));

        //
        // Edit old message and reveal song title
        //

        String rawVideoTitle = getPlayer(guild).getPlayingTrack().getInfo().title.split("\\(")[0].split("\\[")[0];

        // Build song info message
        EmbedBuilder msgBuilder = new EmbedBuilder();

        if(correctAnswer)
            msgBuilder.setColor(Color.GREEN);
        else
            msgBuilder.setColor(Color.RED);


        msgBuilder.setDescription("**ROUND " + activeRoundPlaying.get(guild) + "**\n" +
                "__Song__: **" + rawVideoTitle + "**\n" +
                endReason + " *The next round is starting soon...*");

        // Add each user a win counter field
        for(User user : regGameUsers.get(guild)) {
            int userWins = 0;
            if(gameStatistics.containsKey(guild) && gameStatistics.get(guild).containsKey(user))
                userWins = gameStatistics.get(guild).get(user);

            msgBuilder.addField(user.getName(), "**" + userWins + "** Win(s)", true);
        }

        // Send Message
        activeSongMessages.get(guild).editMessage(msgBuilder.build()).flatMap(Message::clearReactions).queue();

        // Reset pressed users
        ReactionManager.pressedUsersList.remove(guild);

        // Clear all timers
        publicSchedule.cancel();
        publicSchedule.purge();
        publicSchedule = new Timer();


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


        // Update round number
        final int activeRoundCount = activeRoundPlaying.get(guild) + 1;
        activeRoundPlaying.put(guild, activeRoundCount);

        // Refrain
        long refrainTime = calculateRefrainTime(guild);

        // Check if quickMode is active
        if(Start.gameSettings.get(guild).get("quickMode")) {
            // Skip refrain
            refrainTime = 1;
        }else{
            // Go to middle of the song and play refrain
            getPlayer(guild).getPlayingTrack().setPosition((getPlayer(guild).getPlayingTrack().getDuration() / 2) - (calculateRefrainTime(guild) / 2));
            getPlayer(guild).setPaused(false);
        }

        // Play refrain of song for 10 seconds
        new Timer().schedule(
                new TimerTask() {
                    @Override
                    public void run() {

                        // Skip to next song and pause
                        getPlayer(guild).stopTrack();
                        getPlayer(guild).setPaused(true);

                        // Check if playlist is empty
                        if(getManager(guild).getQueue().isEmpty()){
                            // Finish game
                            finishGame(guild);

                            return;
                        }

                        // Build song info message
                        EmbedBuilder startMsgBuilder = new EmbedBuilder().setColor(Color.YELLOW);

                        startMsgBuilder.setDescription("**ROUND " + activeRoundCount + "**\n" +
                                "__Song__: **- - -**\n" +
                                "*Runtime: " + (calculatePlayingTime(guild) / 1000) + " seconds*");

                        // Add each user a win counter field
                        for(User user : regGameUsers.get(guild)) {
                            int userWins = 0;
                            if(gameStatistics.containsKey(guild) && gameStatistics.get(guild).containsKey(user))
                                userWins = gameStatistics.get(guild).get(user);

                            startMsgBuilder.addField(user.getName(), "**" + userWins + "** Win(s)", true);
                        }

                        // Send Message and waite a few seconds to begin

                        Message currentMsg = regChannel.sendMessage(startMsgBuilder.build()).complete();

                        // Manage new round message
                        new Timer().schedule(
                                new TimerTask() {
                                    @Override
                                    public void run() {

                                        // Check if quickMode is active
                                        if(Start.gameSettings.get(guild).get("quickMode")) {
                                            // Skip to middle
                                            getPlayer(guild).getPlayingTrack().setPosition((getPlayer(guild).getPlayingTrack().getDuration() / 2) - (calculatePlayingTime(guild) / 2));
                                        }

                                        // Start song to play
                                        getPlayer(guild).setPaused(false);

                                        // Add reaction emote
                                        currentMsg.addReaction(Main.reactionEmote).queue();

                                        activeSongMessages.put(guild, currentMsg);
                                        activeRoundPlaying.put(guild, activeRoundCount);

                                        publicSchedule.schedule(
                                                new TimerTask() {
                                                    @Override
                                                    public void run() {

                                                        // Check if song is still playing and round is still 1
                                                        if (getPlayer(guild).isPaused() == false && activeRoundPlaying.get(guild) == activeRoundCount) {
                                                            manageNextRound(guild, false, "The timer ran out!");
                                                        }

                                                    }
                                                },
                                                calculatePlayingTime(guild) // Before: 20000
                                        );



                                    }
                                },
                                1000
                        );

                    }
                },
                refrainTime // Before: 10000
        );
    }

    public static void finishGame(Guild guild){
        EmbedBuilder builder = new EmbedBuilder();

        String content = "**STATISTICS**\n";

        if(gameStatistics.containsKey(guild))
            for (User user : gameStatistics.get(guild).keySet()){
                content += "\n" + user.getName() + " - **" + gameStatistics.get(guild).get(user) + "** wins";
            }

        builder.setDescription(content);
        builder.setColor(Color.YELLOW);
        builder.addField("Users", "**" + regGameUsers.get(guild).size() + "**", true);
        builder.addField("Rounds", "**" + activeRoundPlaying.get(guild) + "**", true);

        guild.getTextChannelById(Main.settingsConfig.get(guild.getId()).get("regChannel")).sendMessage(builder.build()).queue();

        publicSchedule.cancel();
        publicSchedule.purge();

        guild.getAudioManager().closeAudioConnection();
        activeSongMessages.remove(guild);
        registeredWinLimit.remove(guild);
        Start.settingsMessage.remove(guild);

        // Delete moderator channel
        if(Start.moderatorChannel.containsKey(guild)) {
            Start.moderatorChannel.get(guild).delete().queue();
            Start.moderatorChannel.remove(guild);
        }
    }

    public static long calculatePlayingTime(Guild guild){
        return (getPlayer(guild).getPlayingTrack().getDuration() / 8);
    }

    public static long calculateRefrainTime(Guild guild){
        return (getPlayer(guild).getPlayingTrack().getDuration() / 8) / 2;
    }

    //
    // Song/Audio Manager
    //

    public static AudioPlayer createPlayer(Guild guild){

        AudioPlayer audioPlayer = audioManager.createPlayer();
        TrackManager trackManager = new TrackManager(audioPlayer);
        audioPlayer.addListener(trackManager);

        guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(audioPlayer));

        players.put(guild, new HashMap.SimpleEntry<>(audioPlayer, trackManager));

        return audioPlayer;
    }

    public static boolean hasPlayer(Guild guild){
        return players.containsKey(guild);
    }

    public static AudioPlayer getPlayer(Guild guild){
        if(hasPlayer(guild))
            return players.get(guild).getKey();
        else
            return createPlayer(guild);
    }

    public static TrackManager getManager(Guild guild){
        return players.get(guild).getValue();
    }

    public static void loadTrack(String identifier, Member author, Message message){

        Guild guild = author.getGuild();

        //audioManager.setFrameBufferDuration(2000);
        audioManager.loadItemOrdered(guild, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                getManager(guild).queue(audioTrack, author);
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                for (AudioTrack audioTrack : audioPlaylist.getTracks()){
                    getManager(guild).queue(audioTrack, author);
                }
            }

            @Override
            public void noMatches() {
                System.out.println("noMatches");
            }

            @Override
            public void loadFailed(FriendlyException e) {
                System.out.println("load failed");
            }
        });

    }
}
