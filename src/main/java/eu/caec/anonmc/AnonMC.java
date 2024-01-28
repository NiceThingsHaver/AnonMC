package eu.caec.anonmc;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

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
                if /*((packet.getPlayerInfoAction().read(0) == EnumWrappers.PlayerInfoAction.ADD_PLAYER) || (packet.getPlayerInfoAction().read(0) == EnumWrappers.PlayerInfoAction.UPDATE_LATENCY))*/ (true) {
                    List<PlayerInfoData> list = packet.getPlayerInfoDataLists().read(0);

                    for (int i=0; i < list.size(); i++) {
                        PlayerInfoData data = list.get(i);

                        if (data == null) {
                            continue;
                        }

                        UUID uniqueID = data.getProfile().getUUID();

                        //I wanted to spoof UUIDs but it resulted in other players getting kicked with an error
                        /*UUID alternativeID = UUID.nameUUIDFromBytes(data.getProfile().getUUID().toString().getBytes());*/

                        list.set(i, new PlayerInfoData(
                                new WrappedGameProfile(data.getProfile().getUUID(), names_map.get(uniqueID)),
                                getConfig().getBoolean("spoof-ping") ? ThreadLocalRandom.current().nextInt(1, 149) : data.getLatency(),
                                data.getGameMode(),
                                WrappedChatComponent.fromLegacyText(names_map.get(uniqueID))
                        ));
                    }

                    packet.getPlayerInfoDataLists().write(0, list);
                }
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!names_map.containsKey(e.getPlayer().getUniqueId())) {
            names_map.put(e.getPlayer().getUniqueId(), "Anonymous");
            chatID_map.put(e.getPlayer().getUniqueId(), generate_chatID());
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
        /*String msg = "<§2" + names_map.get(id) + " §fNo." + post_no + " (" + chatID_map.get(id) + ")> ";*/
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
        return true;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
