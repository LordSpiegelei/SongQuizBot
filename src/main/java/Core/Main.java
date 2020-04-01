package Core;

import Commands.CommandManager;
import Commands.Start;
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
import java.awt.*;
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
            throws LoginException, IOException {
        // Register log channel (gui)
        registerConsole();

        // Output running version
        System.out.println("Running Discord Song Quiz Bot " + SECRETS.BOT_VERSION);

        loadSecrets();

        // Check Discord Token
        if(DISCORD_TOKEN.equalsIgnoreCase("") || DISCORD_TOKEN.equalsIgnoreCase("?")) {
            System.out.println("- - -");
            System.out.println("Write your Discord Bot Token in botSettings.txt and restart");
            return;
        }

        // Connect to Github
        github = GitHub.connectUsingPassword(SECRETS.GITHUB_LOGIN, SECRETS.GITHUB_PW);

        // Check if new version is available
        if(checkForNewVersion()){
            System.out.println("- - -");
            System.out.println("A NEW VERSION IS AVAILABLE! Update to continue");
            System.out.println("https://lordspiegelei.github.io/ or https://github.com/LordSpiegelei/SongQuizBot/releases");
            return;
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

    private static void registerConsole(){
        JFrame frame = new JFrame();
        frame.add( new JLabel(" Console" ), BorderLayout.NORTH );

        JTextArea ta = new JTextArea();
        TextAreaOutputStream taos = new TextAreaOutputStream( ta, 60 );
        PrintStream ps = new PrintStream( taos );
        System.setOut( ps );
        System.setErr( ps );

        frame.add( new JScrollPane( ta )  );

        frame.pack();
        frame.setVisible( true );
        frame.setSize(800,600);

        // Close window event
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                System.exit(0);
            }
        });
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
