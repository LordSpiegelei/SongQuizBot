package Commands;

import Core.Main;
import Manager.GameManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Start extends ListenerAdapter {

    private static HashMap<Guild, GuildMessageReceivedEvent> regMessageEvent = new HashMap<>();
    public static HashMap<Guild, List<Member>> gameUserMods = new HashMap<>();
    public static HashMap<Guild, Integer> gameJokerAmount = new HashMap<>();
    public static HashMap<Guild, HashMap<User, Integer>> userJokerAmount = new HashMap<>();
    public static HashMap<Guild, Message> settingsMessage = new HashMap<>();
    public static HashMap<Guild, HashMap<String, Boolean>> gameSettings = new HashMap<>();
    // Keys:
    // - guessSongTitle
    // - guessArtist
    // - ignoreFeatures
    // - quickMode

    public static void execute(GuildMessageReceivedEvent event) {

        String[] args = event.getMessage().getContentRaw().split(" ");
        Guild guild = event.getGuild();

        // Check if args contain URL
        if(args.length >= 2){

            // Check if guild has setup run
            if(Main.settingsConfig.containsKey(guild.getId()) && Main.settingsConfig.get(guild.getId()).containsKey("regChannel")){

                //
                // Start song loading
                //
                String songUrl = args[1];

                // Check if url is valid
                if(!songUrl.startsWith("http://") && !songUrl.startsWith("https://")){
                    // TODO: No valid link
                    return;
                }

                // Check if user is in voice channel
                if(!event.getMember().getVoiceState().inVoiceChannel()){
                    // TODO: Not in channel
                    return;
                }

                // Check for other users in voice channel
                if(!(event.getMember().getVoiceState().getChannel().getMembers().size() > 0)){
                    // TODO: No others in voice channel
                    return;
                }

                // Setup game channel
                TextChannel gameChannel = setupGameChannel(guild);

                // Mute self headphones
                guild.getAudioManager().setSelfDeafened(true);

                AudioSourceManagers.registerRemoteSources(GameManager.audioManager);

                // Clear queue and stop playing
                if(GameManager.players.containsKey(guild)) {
                    GameManager.getManager(guild).purgeQueue();
                    GameManager.getPlayer(guild).stopTrack();
                }

                // Remove old settingsMessage
                if(settingsMessage.containsKey(guild))
                    settingsMessage.remove(guild);

                // Add active role to user
                Role passiveRole = guild.getRolesByName("SongQuiz Active Users", false).get(0);
                guild.addRoleToMember(event.getMember(), passiveRole).queue();

                // Add spectator role to other users
                Role spectatorRole = guild.getRolesByName("SongQuiz Spectator", false).get(0);
                for(Member member : event.getMember().getVoiceState().getChannel().getMembers()) {
                    guild.addRoleToMember(member, spectatorRole).queue();
                }

                // Load tracks
                GameManager.loadTrack(songUrl, event.getMember(), event.getMessage());

                // Pause songs
                GameManager.getPlayer(guild).setPaused(true);

                //
                // Register Game
                //

                // Send loading message
                Message loadingMsg = gameChannel.sendMessage("Loading songs... [" + event.getAuthor().getAsMention() + "]").complete();

                // Delay to load songs
                new Timer().schedule(
                        new TimerTask() {
                            @Override
                            public void run() {

                                loadingMsg.delete().queue();

                                // Set win limit
                                if(args.length >= 3)
                                    GameManager.registeredWinLimit.put(guild, Integer.parseInt(args[2].replaceAll("[^0-9]", "")));
                                else
                                    GameManager.registeredWinLimit.put(guild, -1);

                                // Set joker amount
                                if(args.length >= 4)
                                    gameJokerAmount.put(guild, Integer.parseInt(args[3].replaceAll("[^0-9]", "")));
                                else
                                    gameJokerAmount.put(guild, 3);

                                // Register Event
                                regMessageEvent.put(guild, event);

                                // Create message
                                sendSettingsMessage(guild, gameChannel);

                            }
                        },
                        10000
                );

            }else{
                //TODO: Write no registered channel
            }

        }else{
            //TODO: Write no url given message
        }

    }

    private static TextChannel setupGameChannel(Guild guild){

        // Category and position of channel
        Category parentCategory = null;
        int channelPosition = 0;

        // Check if channel is already registered
        if(Main.settingsConfig.containsKey(guild.getId()) && Main.settingsConfig.get(guild.getId()).containsKey("regChannel")){

            // Check if channel exists -> get position and delete text channel
            if(guild.getTextChannelById(Main.settingsConfig.get(guild.getId()).get("regChannel")) != null) {
                parentCategory = guild.getTextChannelById(Main.settingsConfig.get(guild.getId()).get("regChannel")).getParent();
                channelPosition = guild.getTextChannelById(Main.settingsConfig.get(guild.getId()).get("regChannel")).getPosition();
                guild.getTextChannelById(Main.settingsConfig.get(guild.getId()).get("regChannel")).delete().queue();
            }
        }

        // Get Role
        Role activeUsersRole;
        Role passiveUsersRole;
        Role spectatorUsersRole;
        Role publicRole = guild.getPublicRole();

        // Get activeUsersRole
        if(guild.getRolesByName("SongQuiz Active Users", false).size() == 0){
            // Create new Role
            activeUsersRole = guild.createRole().setName("SongQuiz Active Users").complete();
        }else
            activeUsersRole = guild.getRolesByName("SongQuiz Active Users", false).get(0);

        // Get passiveUsersRole
        if(guild.getRolesByName("SongQuiz Passive Users", false).size() == 0){
            // Create new Role
            passiveUsersRole = guild.createRole().setName("SongQuiz Passive Users").complete();
        }else
            passiveUsersRole = guild.getRolesByName("SongQuiz Passive Users", false).get(0);

        // Get spectatorUsersRole
        if(guild.getRolesByName("SongQuiz Spectator", false).size() == 0){
            // Create new Role
            spectatorUsersRole = guild.createRole().setName("SongQuiz Spectator").complete();
        }else
            spectatorUsersRole = guild.getRolesByName("SongQuiz Spectator", false).get(0);


        // Create new channel and register

        // Set public permissions (deny)
        java.util.List<Permission> permsList = new ArrayList<>();
        permsList.add(Permission.MESSAGE_WRITE);
        permsList.add(Permission.MESSAGE_READ);
        permsList.add(Permission.MESSAGE_ADD_REACTION);

        // Set passive permissions (allow)
        List<Permission> activePermsList = new ArrayList<>();
        activePermsList.add(Permission.MESSAGE_WRITE);
        activePermsList.add(Permission.MESSAGE_READ);
        activePermsList.add(Permission.MESSAGE_ADD_REACTION);

        // Set passive permissions (allow)
        List<Permission> passivePermsList = new ArrayList<>();
        passivePermsList.add(Permission.MESSAGE_READ);
        passivePermsList.add(Permission.MESSAGE_ADD_REACTION);

        // Set spectator permissions (allow)
        List<Permission> spectatorPermsList = new ArrayList<>();
        spectatorPermsList.add(Permission.MESSAGE_READ);


        // Create channel with permissions
        TextChannel newChannel;

        // Check if old channel existed
        if(parentCategory != null){
            // Create on old position
            newChannel = guild.createTextChannel("SongQuiz")
                    .addPermissionOverride(publicRole, null, permsList)
                    .addPermissionOverride(activeUsersRole, activePermsList, null)
                    .addPermissionOverride(passiveUsersRole, passivePermsList, null)
                    .addPermissionOverride(spectatorUsersRole, spectatorPermsList, null)
                    .setParent(parentCategory)
                    .setPosition(channelPosition)
                    .complete();
        }else {
            // Create on new position
            newChannel = guild.createTextChannel("SongQuiz")
                    .addPermissionOverride(publicRole, null, permsList)
                    .addPermissionOverride(activeUsersRole, activePermsList, null)
                    .addPermissionOverride(passiveUsersRole, passivePermsList, null)
                    .addPermissionOverride(spectatorUsersRole, spectatorPermsList, null)
                    .complete();
        }

        // Add to HashMap
        HashMap<String, String> guildMap = new HashMap<>();
        guildMap.put("regChannel", newChannel.getId());

        Main.settingsConfig.put(guild.getId(), guildMap);

        Main.saveConfig();

        return newChannel;
    }

    private static void sendSettingsMessage(Guild guild, TextChannel gameChannel){
        EmbedBuilder builder = new EmbedBuilder();

        // Get rounds if exists
        String winLimit = "not selected";

        if(GameManager.registeredWinLimit.get(guild) >= 1)
            winLimit = String.valueOf(GameManager.registeredWinLimit.get(guild));

        builder.setDescription("**GAME SETTINGS**\n\n" +
                "Win Limit: ``" + winLimit + "``\n" +
                "Loaded Songs: ``" + GameManager.getManager(guild).getQueue().size() + "``\n" +
                "*Game started by* " + regMessageEvent.get(guild).getAuthor().getAsMention() + "\n\n" +
                "Cmds: ``addMod (User Mention)``, ``removeMod (User Mention)``, ``listMods``\n\n" +
                "**To start the game press** " + "✅" + " !");
        builder.setColor(Color.YELLOW);

        // Check if settings are registered
        if(gameSettings.containsKey(guild)){
            // Use guild settings

            // Guess SongTitle Status
            if(gameSettings.get(guild).get("guessSongTitle"))
                builder.addField("**Guess Song Title** " + "(1️⃣)", "**ON**", true);
            else
                builder.addField("**Guess Song Title** " + "(1️⃣)", "**OFF**", true);

            // Guess Artist Status
            if(gameSettings.get(guild).get("guessArtist"))
                builder.addField("**Guess Artist** " + "(2️⃣)", "**ON**", true);
            else
                builder.addField("**Guess Artist** " + "(2️⃣)", "**OFF**", true);

            // Ignore features Status
            if(gameSettings.get(guild).get("ignoreFeatures"))
                builder.addField("**Ignore Features** " + "(3️⃣)", "**ON**", false);
            else
                builder.addField("**Ignore Features** " + "(3️⃣)", "**OFF**", false);

            // Ignore features Status
            if(gameSettings.get(guild).get("quickMode"))
                builder.addField("**Quick Round Mode** " + "(⏭)", "**ON**", false);
            else
                builder.addField("**Quick Round Mode** " + "(⏭)", "**OFF**", false);

        }else{
            // Use default settings

            // Set default into hashMap
            HashMap<String, Boolean> guildMap = new HashMap<>();
            guildMap.put("guessSongTitle", false);
            guildMap.put("guessArtist", false);
            guildMap.put("ignoreFeatures", false);
            guildMap.put("quickMode", false);

            gameSettings.put(guild, guildMap);

            builder.addField("**Guess Song Title** " + "(1️⃣)", "**OFF**", true);
            builder.addField("**Guess Artist** " + "(2️⃣)", "**OFF**", true);
            builder.addField("**Ignore Features** " + "(3️⃣)", "**OFF**", false);
            builder.addField("**Quick Round Mode** " + "(⏭)", "**OFF**", false);
        }

        if(settingsMessage.containsKey(guild)){

            settingsMessage.get(guild).editMessage(builder.build()).queue();

        }else{
            Message message = gameChannel.sendMessage(builder.build()).complete();

            // Add reactions
            message.addReaction("1️⃣").queue();
            message.addReaction("2️⃣").queue();
            message.addReaction("3️⃣").queue();
            message.addReaction("⏭").queue();
            message.addReaction("✅").queue();

            settingsMessage.put(guild, message);
        }

    }

    @Override
    public void onGuildMessageReactionAdd(@Nonnull GuildMessageReactionAddEvent event) {

        if(event.getUser().isBot())
            return;

        Guild guild = event.getGuild();

        // Check if message is settings message
        if(settingsMessage.containsKey(guild) && event.getReaction().getMessageId().equals(settingsMessage.get(guild).getId())) {

            // Get settings of guild
            HashMap<String, Boolean> guildMap = gameSettings.get(guild);

            // Get game channel
            TextChannel gameChannel = guild.getTextChannelById(Main.settingsConfig.get(guild.getId()).get("regChannel"));

            // Check if setting got changes
            if (event.getReaction().getReactionEmote().getName().equals("1️⃣")) {
                guildMap.put("guessSongTitle", true);

                gameSettings.put(guild, guildMap);

                // Update Message
                sendSettingsMessage(guild, gameChannel);

            }else if (event.getReaction().getReactionEmote().getName().equals("2️⃣")) {
                guildMap.put("guessArtist", true);

                gameSettings.put(guild, guildMap);

                // Update Message
                sendSettingsMessage(guild, gameChannel);

            }else if (event.getReaction().getReactionEmote().getName().equals("3️⃣")){
                guildMap.put("ignoreFeatures", true);

                gameSettings.put(guild, guildMap);

                // Update Message
                sendSettingsMessage(guild, gameChannel);

            }else if(event.getReaction().getReactionEmote().getName().equals("⏭")) {
                guildMap.put("quickMode", true);

                gameSettings.put(guild, guildMap);

                // Update Message
                sendSettingsMessage(guild, gameChannel);

            }else if(event.getReaction().getReactionEmote().getName().equals("✅")){
                settingsMessage.get(guild).clearReactions().queue();
                settingsMessage.remove(guild);

                // Setup mod channel
                setupModChannel(guild, gameChannel);

                // Start game
                GameManager.startGame(guild, regMessageEvent.get(guild).getMember());
            }

        }

    }

    @Override
    public void onGuildMessageReactionRemove(@Nonnull GuildMessageReactionRemoveEvent event) {

        if(event.getUser().isBot())
            return;

        Guild guild = event.getGuild();

        // Check if message is settings message
        if(settingsMessage.containsKey(guild) && event.getReaction().getMessageId().equals(settingsMessage.get(guild).getId())){

            // Get settings of guild
            HashMap<String, Boolean> guildMap = gameSettings.get(guild);

            // Get game channel
            TextChannel gameChannel = guild.getTextChannelById(Main.settingsConfig.get(guild.getId()).get("regChannel"));

            // Check if setting got changes
            if(event.getReaction().getReactionEmote().getName().equals("1️⃣"))
                guildMap.put("guessSongTitle", false);

            else if(event.getReaction().getReactionEmote().getName().equals("2️⃣"))
                guildMap.put("guessArtist", false);

            else if(event.getReaction().getReactionEmote().getName().equals("3️⃣"))
                guildMap.put("ignoreFeatures", false);

            else if(event.getReaction().getReactionEmote().getName().equals("⏭"))
                guildMap.put("quickMode", false);

            gameSettings.put(guild, guildMap);

            // Update Message
            sendSettingsMessage(guild, gameChannel);

        }
    }

    //
    // Moderator Manager
    //

    public static HashMap<Guild, TextChannel> moderatorChannel = new HashMap<>();

    private static void setupModChannel(Guild guild, TextChannel gameChannel){

        // Check if moderators are registered
        if((!gameUserMods.containsKey(guild)) || (gameUserMods.get(guild).isEmpty()))
            return;

        // Get Role
        Role publicRole = guild.getPublicRole();
        Role activeModRole;
        Role passiveModRole;

        // Get activeModRole
        if(guild.getRolesByName("SongQuiz active Mod", false).size() == 0){
            // Create new Role
            activeModRole = guild.createRole().setName("SongQuiz active Mod").complete();
        }else
            activeModRole = guild.getRolesByName("SongQuiz active Mod", false).get(0);

        // Get passiveModRole
        if(guild.getRolesByName("SongQuiz passive Mod", false).size() == 0){
            // Create new Role
            passiveModRole = guild.createRole().setName("SongQuiz passive Mod").complete();
        }else
            passiveModRole = guild.getRolesByName("SongQuiz passive Mod", false).get(0);

        // Create new channel and register

        // Set public permissions (deny)
        java.util.List<Permission> permsList = new ArrayList<>();
        permsList.add(Permission.MESSAGE_WRITE);
        permsList.add(Permission.MESSAGE_READ);

        // Set active moderator permissions (allow)
        List<Permission> activePermsList = new ArrayList<>();
        activePermsList.add(Permission.MESSAGE_WRITE);
        activePermsList.add(Permission.MESSAGE_READ);

        // Set passive moderator permissions (allow)
        List<Permission> passivePermsList = new ArrayList<>();
        passivePermsList.add(Permission.MESSAGE_READ);


        // Create channel with permissions
        TextChannel modChannel;

        // Create on position of game channel
        modChannel = guild.createTextChannel("SongQuiz Moderators")
                .addPermissionOverride(publicRole, null, permsList)
                .addPermissionOverride(activeModRole, activePermsList, null)
                .addPermissionOverride(passiveModRole, passivePermsList, null)
                .setParent(gameChannel.getParent())
                .setPosition(gameChannel.getPosition() + 1)
                .complete();

        // Register channel
        moderatorChannel.put(guild, modChannel);

        String modMentions = "";

        // Add each moderator role
        for(Member member : gameUserMods.get(guild)){
            guild.addRoleToMember(member, passiveModRole).queue();

            modMentions += " " + member.getAsMention();
        }

        // Write moderator channel info message
        EmbedBuilder builder = new EmbedBuilder().setColor(Color.YELLOW);
        builder.setDescription("**MODERATOR ROLE**\n\n" +
                "Mods: " + modMentions + "\n\n" +
                "Mission: " + "``The moderator has one guess each round, which can be made until a normal player makes a right guess or the time runs out. If a player uses a joker, he gets to see this channel.``");

        modChannel.sendMessage(builder.build()).queue();
    }

    @Override
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {

        Guild guild = event.getGuild();

        // Check if guild has an active setup
        if(settingsMessage.containsKey(guild)){

            // Check if channel is registered channel
            if(settingsMessage.get(guild).getTextChannel() == event.getChannel()){

                // Check if message equal command
                String[] args = event.getMessage().getContentRaw().split(" ");

                // Add moderator
                if(args[0].equalsIgnoreCase("addMod") && args.length >= 2){
                    // Get member of message
                    Member member = guild.getMemberById(args[1].replace("<@!", "").replace(">", ""));

                    // Add user to list
                    List<Member> modList = new ArrayList<>();
                    if(gameUserMods.containsKey(guild))
                        modList = gameUserMods.get(guild);

                    // Check if user is already registered
                    if(!modList.contains(member)){
                        modList.add(member);

                        // Send message
                        EmbedBuilder builder = new EmbedBuilder().setColor(Color.GREEN);
                        builder.setDescription("**Successfully added** " + member.getAsMention() + " **to the moderators!** [``" + modList.size() + "`` Moderator(s) registered]");
                        event.getChannel().sendMessage(builder.build()).queue();

                        // Save to hash map
                        gameUserMods.put(guild, modList);
                    }else{
                        // Send message
                        EmbedBuilder builder = new EmbedBuilder().setColor(Color.RED);
                        builder.setDescription("*The user is already registered as an moderator*");
                        event.getChannel().sendMessage(builder.build()).queue();
                    }

                // Remove Moderator
                }else if(args[0].equalsIgnoreCase("removeMod") && args.length >= 2){
                    // Get member of message
                    Member member = guild.getMemberByTag(args[1].replace("<@!", "").replace(">", ""));

                    // Remove user form list
                    List<Member> modList = new ArrayList<>();
                    if(gameUserMods.containsKey(guild))
                        modList = gameUserMods.get(guild);

                    // Check if user is already registered
                    if(modList.contains(member)){
                        modList.remove(member);

                        // Send message
                        EmbedBuilder builder = new EmbedBuilder().setColor(Color.GREEN);
                        builder.setDescription("**Successfully removed** " + member.getAsMention() + " **from the moderators!** [``" + modList.size() + "`` Moderator(s) registered]");
                        event.getChannel().sendMessage(builder.build()).queue();

                        // Save to hash map
                        gameUserMods.put(guild, modList);
                    }else{
                        // Send message
                        EmbedBuilder builder = new EmbedBuilder().setColor(Color.RED);
                        builder.setDescription("*The user is not registered as an moderator*");
                        event.getChannel().sendMessage(builder.build()).queue();
                    }

                // List Moderators
                }else if(args[0].equalsIgnoreCase("listMod") || args[0].equalsIgnoreCase("listMods") || args[0].equalsIgnoreCase("list")){

                    // Get mod list of guild
                    List<Member> modList = new ArrayList<>();
                    if(gameUserMods.containsKey(guild))
                        modList = gameUserMods.get(guild);

                    // Check if list is empty
                    if(!modList.isEmpty()){
                        String modListUsers = "**MODERATORS**";

                        // Add users to string
                        for(Member member : modList){
                            modListUsers += "\n" + member.getAsMention();
                        }

                        // Send message
                        EmbedBuilder builder = new EmbedBuilder().setColor(Color.GREEN);
                        builder.setDescription(modListUsers);
                        event.getChannel().sendMessage(builder.build()).queue();
                    }else{
                        // Send message
                        EmbedBuilder builder = new EmbedBuilder().setColor(Color.RED);
                        builder.setDescription("*No registered moderators*");
                        event.getChannel().sendMessage(builder.build()).queue();
                    }
                }
            }
        }
    }
}
