package Core;

import Commands.CommandManager;
import Commands.Start;
import Manager.MessageManager;
import Manager.ReactionManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import javax.security.auth.login.LoginException;
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

    public static void main(String[] args)
            throws LoginException
    {
        loadSecrets();

        if(DISCORD_TOKEN.equalsIgnoreCase("") || DISCORD_TOKEN.equalsIgnoreCase("?")) {
            System.out.println("Write your Discord Bot Token in botSettings.txt");
            System.exit(0);
        }

        jda = new JDABuilder(DISCORD_TOKEN).build();

        jda.getPresence().setStatus(OnlineStatus.ONLINE);
        jda.getPresence().setActivity(Activity.watching("+help"));

        loadConfig();

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

    public static void loadSecrets(){
        File file = new File("botSettings.txt");
        if(file.exists()) {
            try {
                FileReader fr = new FileReader(file);
                BufferedReader br = new BufferedReader(fr);
                String line;
                while ((line = br.readLine()) != null) {
                    // Check if line starts with "T"
                    if(line.startsWith("Token=")){
                        DISCORD_TOKEN = line.replaceFirst("Token=", "").replaceAll("\"", "").replaceAll(" ", "");
                    }
                }
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            // Create new file
            try {
                FileWriter writer = new FileWriter(file.getPath(), true);
                writer.write("Token=");
                writer.close();

                DISCORD_TOKEN = "";
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
