package eu.caec.anonmc;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedServerPing;
import org.bukkit.BanList;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public final class AnonMC extends JavaPlugin implements Listener {
    int post_no = 0;
    Map<UUID, String> names_map = new HashMap<UUID, String>();
    Map<UUID, String> chatID_map = new HashMap<UUID, String>();

    public static String generate_chatID() {
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

        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        getServer().getPluginManager().registerEvents(this, this);

        //S2C
        manager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.PLAYER_INFO) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();

                List<PlayerInfoData> list = packet.getPlayerInfoDataLists().read(0);

                for (int i=0; i < list.size(); i++) {
                    PlayerInfoData data = list.get(i);
                    if (data == null) {
                        continue;
                    }

                    UUID uniqueID = data.getProfile().getUUID();

                    list.set(i, new PlayerInfoData(
                            new WrappedGameProfile(uniqueID, names_map.get(uniqueID)),
                            getConfig().getBoolean("spoof-ping") ? ThreadLocalRandom.current().nextInt(1, 149) : data.getLatency(),
                            data.getGameMode(),
                            WrappedChatComponent.fromLegacyText(names_map.get(uniqueID))
                    ));
                }

                packet.getPlayerInfoDataLists().write(0, list);

            }
        });

        manager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.TAB_COMPLETE) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                /*this disables the tab complete entirely kek
                you will still be able to see the list of commands
                it won't break the tab complete of vanilla commands because they are client side*/
                event.setCancelled(true);
            }
        });

        manager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Status.Server.SERVER_INFO) {
            @Override
            public void onPacketSending(PacketEvent event) {
                WrappedServerPing packet = event.getPacket().getServerPings().read(0);

                WrappedGameProfile p1[] = new WrappedGameProfile[packet.getPlayersOnline()];
                for (short i=0; i<packet.getPlayersOnline(); i++) {
                    p1[i] = new WrappedGameProfile(UUID.randomUUID(), "Anon" + (i+1));
                }
                Iterable<WrappedGameProfile> p2 = Arrays.asList(p1);

                //packet.setPlayersVisible(false);
                packet.setPlayers(p2);
            }
        });
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
        }

        String msg = getConfig().getString("join-message");
        if (msg != null) {
            msg = msg.replace("%player%", names_map.get(e.getPlayer().getUniqueId()));
            e.setJoinMessage(msg);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        String msg = getConfig().getString("quit-message");
        if (msg != null) {
            msg = msg.replace("%player%", names_map.get(e.getPlayer().getUniqueId()));
            e.setQuitMessage(msg);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        String msg = getConfig().getString("death-message");
        if (msg != null) {
            msg = msg.replace("%player%", names_map.get(e.getEntity().getPlayer().getUniqueId()));
            e.setDeathMessage(msg);
        }
    }

    @EventHandler
    public void onChatMessage(AsyncPlayerChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();;

        post_no++;
        e.setCancelled(true);
        String msg = getConfig().getString("chat-format");
        msg = msg.replace("%name%", names_map.get(id));
        msg = msg.replace("%number%", "No." + post_no);
        msg = msg.replace("%chatid%", chatID_map.get(id));

        if (e.getMessage().charAt(0) == '>') {
            msg += "§a";
        }

        msg += e.getMessage();
        getServer().broadcastMessage(msg);
    }

    @EventHandler
    public void onBookEdit(PlayerEditBookEvent e) { //this breaks the nbt data if you sign the book, need to fix
        BookMeta newer = e.getNewBookMeta();
        newer.setAuthor(names_map.get(e.getPlayer().getName()));
        if (e.isSigning()) {
            e.setNewBookMeta(newer);
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
                    if (args[0].length() < 17) {
                        Player p = (Player) sender;
                        names_map.put(p.getUniqueId(), args[0]);
                        sender.sendMessage("§bSuccessfully changed name! Relog for your new name to display outside of chat.");
                    } else {
                        sender.sendMessage("§cYou cannot set a name longer than 16 characters");
                    }
                }
            } else {
                sender.sendMessage("§cYou cannot run this command from console");
            }
        }

        if (cmd.getName().equalsIgnoreCase("moderate")) {
            /*if (sender instanceof Player) {*/
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
            /*} else {
                sender.sendMessage("§cYou cannot run this command from console");
            }*/
        }
        return true;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
