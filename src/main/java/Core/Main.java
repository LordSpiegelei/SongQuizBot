package Core;

import Commands.CommandManager;
import Commands.Start;
import Manager.InterfaceManager;
import Manager.MessageManager;
import Manager.ReactionManager;
import Utils.SECRETS;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.kohsuke.github.GitHub;

import javax.security.auth.login.LoginException;
import javax.swing.*;
import java.io.*;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class Main {

    public static JDA jda;
    public static String prefix = "+";

    public static final String reactionEmote = "\uD83D\uDD34";
    public static final String jokerEmote = "\uD83C\uDCCF";
    public static final long timeToWrite = 30000;

    public static String DISCORD_TOKEN = "";

    private static GitHub github;

    public static void main(String[] args)
            throws IOException {
        // Register log channel (gui)
        InterfaceManager.start();

        // Output running version
        InterfaceManager.writeLog("Running Discord Song Quiz Bot " + SECRETS.BOT_VERSION);

        loadConfig();

        // Check Discord Token
        InterfaceManager.writeLog("- - -");
        InterfaceManager.writeLog("Loading Token...");

        if(requestToken())
            return;

        // Connect to Github
        InterfaceManager.writeLog("- - -");
        InterfaceManager.writeLog("Checking for new version...");
        github = GitHub.connectUsingPassword(SECRETS.GITHUB_LOGIN, SECRETS.GITHUB_PW);

        // Check if new version is available
        if(checkForNewVersion()){
            InterfaceManager.writeLog("- - -");
            InterfaceManager.writeLog("A NEW VERSION IS AVAILABLE! Update to continue");
            InterfaceManager.writeLog("https://lordspiegelei.github.io/ or https://github.com/LordSpiegelei/SongQuizBot/releases");
            return;
        }else{
            InterfaceManager.writeLog("No new version found");
            InterfaceManager.writeLog("- - -");
            InterfaceManager.writeLog("Starting Bot...");
        }

        try {
            jda = new JDABuilder(DISCORD_TOKEN).build();
        } catch (LoginException e) {
            e.printStackTrace();

            // Send log info
            InterfaceManager.writeLog("ERROR: " + e.getMessage());

            // Remove token from config
            settingsConfig.remove("general");
            saveConfig();
            InterfaceManager.writeLog("Please restart and reenter Token!");

            return;
        }

        // Send success message
        InterfaceManager.writeLog("Success");

        // Enable reset button
        InterfaceManager.resetButton.setEnabled(true);

        jda.getPresence().setStatus(OnlineStatus.ONLINE);
        jda.getPresence().setActivity(Activity.watching("+help"));

        registerListener();

        // Manage new round message
        new Timer().schedule(
                new TimerTask() {
                    @Override
                    public void run() {

                        for(Guild guild : jda.getGuilds()){

                            if(guild.getRolesByName("SongQuiz Passive Users", false).size() >= 1){
                                Role passiveRole = guild.getRolesByName("SongQuiz Passive Users", false).get(0);

                                // Remove passive roles from each member
                                for(Member member : guild.getMembersWithRoles(passiveRole)){
                                    guild.removeRoleFromMember(member, passiveRole).queue();
                                }
                            }

                            if(guild.getRolesByName("SongQuiz Active Users", false).size() >= 1){
                                Role activeRole = guild.getRolesByName("SongQuiz Active Users", false).get(0);

                                // Remove active roles from each member
                                for(Member member : guild.getMembersWithRoles(activeRole)){
                                    guild.removeRoleFromMember(member, activeRole).queue();
                                }
                            }

                            if(guild.getRolesByName("SongQuiz Spectator", false).size() >= 1){
                                Role spectatorRole = guild.getRolesByName("SongQuiz Spectator", false).get(0);

                                // Remove active roles from each member
                                for(Member member : guild.getMembersWithRoles(spectatorRole)){
                                    guild.removeRoleFromMember(member, spectatorRole).queue();
                                }
                            }

                            if(guild.getRolesByName("SongQuiz active Mod", false).size() >= 1){
                                Role activeModRole = guild.getRolesByName("SongQuiz active Mod", false).get(0);

                                // Remove active roles from each member
                                for(Member member : guild.getMembersWithRoles(activeModRole)){
                                    guild.removeRoleFromMember(member, activeModRole).queue();
                                }
                            }

                            if(guild.getRolesByName("SongQuiz passive Mod", false).size() >= 1){
                                Role passiveModRole = guild.getRolesByName("SongQuiz passive Mod", false).get(0);

                                // Remove active roles from each member
                                for(Member member : guild.getMembersWithRoles(passiveModRole)){
                                    guild.removeRoleFromMember(member, passiveModRole).queue();
                                }
                            }

                            if(guild.getAudioManager() != null)
                                guild.getAudioManager().closeAudioConnection();
                        }

                    }
                },
                2000
        );

    }

    private static void registerListener(){
        jda.addEventListener(new CommandManager());
        jda.addEventListener(new ReactionManager());
        jda.addEventListener(new MessageManager());
        jda.addEventListener(new Start());
    }

    private static boolean requestToken(){
        // Check if token is registered
        if(settingsConfig.containsKey("general") && settingsConfig.get("general").containsKey("token")){
            DISCORD_TOKEN = settingsConfig.get("general").get("token");
        }

        if(DISCORD_TOKEN.equalsIgnoreCase("")) {
            // Open window with input & check if input is correct
            String frameResult = JOptionPane.showInputDialog(InterfaceManager.interfaceFrame, "Please enter your Discord Bot Token", "Token Request", JOptionPane.PLAIN_MESSAGE);
            if (frameResult == null){
                // Quit Application
                InterfaceManager.writeLog("No Token were given... Please restart");
                return true;
            }else{
                HashMap<String, String> generalMap = new HashMap<>();
                generalMap.put("token", frameResult.replaceAll(" ", ""));
                settingsConfig.put("general", generalMap);
                saveConfig();

                DISCORD_TOKEN = frameResult;
            }
        }
        System.out.println(DISCORD_TOKEN);

        return false;
    }

    public static boolean checkForNewVersion(){

        String tagName = SECRETS.BOT_VERSION;

        try {
            tagName = github.getRepositoryById(SECRETS.GITHUB_ID).getLatestRelease().getTagName();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(tagName.equalsIgnoreCase(SECRETS.BOT_VERSION))
            return false;
        else
            return true;
    }

    public static HashMap<String, HashMap<String, String>> settingsConfig = new HashMap<>();

    public static void saveConfig(){

        File path = new File("Files/");
        if(!path.exists())
            path.mkdir();

        try{

            FileOutputStream fos = new FileOutputStream("Files/" + "config.dat");
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(settingsConfig);
            oos.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public static void loadConfig(){
        File file = new File("Files/" +"config.dat");
        if(file.exists()) {
            try {
                settingsConfig.clear();
                FileInputStream fis = new FileInputStream(file);
                ObjectInputStream ois = new ObjectInputStream(fis);
                settingsConfig = (HashMap<String, HashMap<String, String>>) ois.readObject();
                ois.close();

            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
            } catch (IOException e1) {
                e1.printStackTrace();
            } catch (ClassNotFoundException e1) {
                e1.printStackTrace();
            }
        }
    }
}
