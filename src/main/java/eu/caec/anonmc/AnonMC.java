package eu.caec.anonmc;

import org.bukkit.BanList;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;

import java.util.*;
import java.util.logging.Logger;

public final class AnonMC extends JavaPlugin implements Listener {
    public static AnonMC instance;
    int post_no = 0;
    public static Map<UUID, String> names_map = new HashMap<UUID, String>();
    Map<UUID, String> chatID_map = new HashMap<UUID, String>();
    Map<UUID, Long> cooldown_map = new HashMap<UUID, Long>();

    public String generate_chatID() {
        String chs = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder id = new StringBuilder();

        Random random = new Random();
        for (byte i = 0; i < 6; i++) {
            int randomIndex = random.nextInt(chs.length());
            id.append(chs.charAt(randomIndex));
        }
        return id.toString();
    }

    public boolean check_if_id_exists(String id) {
        for (String i : chatID_map.values()) {
            if (i.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean janny_check(CommandSender sender) {
        String username = sender.getName();
        List<String> jannies = this.getConfig().getStringList("jannies");
        for (String j : jannies) {
            if (username.equals(j)) {
                return true;
            }
        }
        return false;
    }

    public boolean canBeInteger(String s) {
        for (int i=0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        instance = this;

        PacketEvents.getAPI().getEventManager().registerListener(new PacketEventsPacketListener());
        PacketEvents.getAPI().init();
    }

    @EventHandler
    public void onLoad(ServerLoadEvent event) {
        Logger log = getServer().getLogger();
        List<World> worlds = getServer().getWorlds();
        for (World wloop : worlds) {
            if(wloop.getGameRuleValue(GameRule.ANNOUNCE_ADVANCEMENTS)) {
                wloop.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
                log.info("[AnonMC] Disabling vanilla advancement messages for " + wloop.getName() + " because they are displaying actual player names");
            }
        }

        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        //Are all listeners read only?
        PacketEvents.getAPI().getSettings().reEncodeByDefault(true)
                .checkForUpdates(true)
                .bStats(false);
        PacketEvents.getAPI().load();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!names_map.containsKey(e.getPlayer().getUniqueId())) {
            names_map.put(e.getPlayer().getUniqueId(), "Anonymous");
            String chatID = generate_chatID();
            //prevents that two ids are identical
            while (check_if_id_exists(chatID)) {
                chatID = generate_chatID();
            }
            chatID_map.put(e.getPlayer().getUniqueId(), chatID);
            cooldown_map.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        }

        String msg = getConfig().getString("join-message");
        if ( getConfig().getBoolean("enable-join-leave-messages") && msg != null ) {
            msg = msg.replace("%player%", names_map.get(e.getPlayer().getUniqueId()));
            e.setJoinMessage(msg);
        } else {
            e.setJoinMessage("");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        String msg = getConfig().getString("quit-message");
        if ( getConfig().getBoolean("enable-join-leave-messages") && msg != null ) {
            msg = msg.replace("%player%", names_map.get(e.getPlayer().getUniqueId()));
            e.setQuitMessage(msg);
        } else {
            e.setQuitMessage("");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        String msg = getConfig().getString("death-message");
        if ( getConfig().getBoolean("enable-death-messages") && msg != null ) {
            msg = msg.replace("%player%", names_map.get(e.getEntity().getPlayer().getUniqueId()));
            e.setDeathMessage(msg);
        } else {
            e.setDeathMessage("");
        }
    }

    @EventHandler
    public void onChatMessage(AsyncPlayerChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        int cooldown = getConfig().getInt("chat-cooldown-ms");

        e.setCancelled(true);
        if (System.currentTimeMillis() - cooldown_map.get(id) < cooldown) {
            e.getPlayer().sendMessage("§cThere is a cooldown of " + cooldown + "ms between each messages.");
        } else {
            post_no++;
            String msg = getConfig().getString("chat-format");
            msg = msg.replace("%name%", names_map.get(id));
            msg = msg.replace("%number%", "No." + post_no);
            msg = msg.replace("%chatid%", chatID_map.get(id));

            if (e.getMessage().charAt(0) == '>') {
                msg += "§a";
            }

            msg += e.getMessage();

            for (int i = post_no; i > post_no - 40; i--) {
                //double blue color code to prevent it from checking individual numbers in the reply
                msg = msg.replace(">>" + i, "§3>>§3" + i + "§f");
            }

            getServer().broadcastMessage(msg);
            cooldown_map.put(id, System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onBookEdit(PlayerEditBookEvent e) {
        if (e.isSigning()) {
            e.getPlayer().sendMessage("§6NOTE: Your real username is displayed as the author of the books you sign.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        //the command to change the name seen to others
        if (cmd.getName().equalsIgnoreCase("name")) {
            if (sender instanceof Player) {
                if (args.length < 1) {
                    sender.sendMessage("§cPlease input a name");
                } else {
                    if (args[0].length() < 17 && !(args[0].contains("\""))) {
                        Player p = (Player) sender;
                        names_map.put(p.getUniqueId(), args[0]);
                        sender.sendMessage("§bSuccessfully changed name! Relog for your new name to display outside of chat.");
                    } else {
                        sender.sendMessage("§cYou cannot set a name longer than 16 characters, you cannot use \" either.");
                    }
                }
            } else {
                sender.sendMessage("§cYou cannot run this command from console");
            }
        }

        if (cmd.getName().equalsIgnoreCase("moderate")) {
                if (sender.isOp() || janny_check(sender)) {
                    if (args.length >= 1) {
                        switch(args[0]) {
                            case "list":
                                String playerlist = "Player list: ";
                                for (Player p : getServer().getOnlinePlayers()) {
                                    playerlist = playerlist + p.getName() + " (" + chatID_map.get(p.getUniqueId()) + "), ";
                                }
                                sender.sendMessage(playerlist);
                                break;

                            case "ban":
                                if (args.length >= 4 && canBeInteger(args[2])) {
                                    int days = Integer.parseInt(args[2]);
                                    Player playerToBan = getServer().getPlayer(args[1]);

                                    String banReason = "";

                                    for(int i = 3; i < args.length; i++){
                                        String arg = args[i] + " ";
                                        banReason = banReason + arg;
                                    }

                                    getServer().getBanList(BanList.Type.NAME).addBan(args[1], banReason, new Date(System.currentTimeMillis() + (86400000L * days)), sender.getName());

                                    if (playerToBan != null) {
                                        getServer().getBanList(BanList.Type.IP).addBan(playerToBan.getAddress().getAddress().getHostAddress(), banReason, new Date(System.currentTimeMillis() + (86400000L * days)), sender.getName());
                                        playerToBan.kickPlayer("You got banned! ;_; Reconnect for more infos");
                                    }

                                    sender.sendMessage("§bSucessfully banned player " + args[1] + " for " + args[2] + " days with the following reason: " + banReason);
                                } else {
                                    sender.sendMessage("§cIncorrect syntax!");
                                    sender.sendMessage("§d/moderate ban <realName> <days> <the reason> §b: Ban a player");
                                }
                                break;

                            default:
                                sender.sendMessage("§cType the command without any arguments to see its correct usages");
                        }
                    } else {
                        sender.sendMessage("§dCommand usage:");
                        sender.sendMessage("§d/moderate list §b: Lists all real usernames with their associated chat IDs");
                        sender.sendMessage("§d/moderate ban <realName> <days> <the reason> §b: Ban a player");
                    }
                } else {
                    sender.sendMessage("§cThis command is only for operators and jannies");
                }
        }
        return true;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
