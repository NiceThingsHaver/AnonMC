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
import org.bukkit.advancement.Advancement;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnonMC extends JavaPlugin implements Listener {
    int post_no = 0;
    Map<UUID, String> names_map = new HashMap<UUID, String>();

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
                if (packet.getPlayerInfoAction().read(0) == EnumWrappers.PlayerInfoAction.ADD_PLAYER) {
                    List<PlayerInfoData> list = packet.getPlayerInfoDataLists().read(0);

                    for (int i=0; i < list.size(); i++) {
                        PlayerInfoData data = list.get(i);

                        if (data == null) {
                            continue;
                        }

                        UUID uniqueID = data.getProfile().getUUID();

                        //stupid way of doing it but it works I guess
                        UUID alternativeID = UUID.nameUUIDFromBytes(data.getProfile().getUUID().toString().getBytes());

                        list.set(i, new PlayerInfoData(
                                new WrappedGameProfile(getConfig().getBoolean("spoof-uuids") ? alternativeID : uniqueID, names_map.get(uniqueID)),
                                data.getLatency(),
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
        post_no++;
        e.setCancelled(true);
        String msg = "<§2" + names_map.get(e.getPlayer().getUniqueId()) + " §fNo." + post_no + " (WIPwip)> ";

        if (e.getMessage().charAt(0) == '>') {
            msg += "§a";
        }

        msg += e.getMessage();
        getServer().broadcastMessage(msg);
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        //the command to change the name seen to others
        if (cmd.getName().equalsIgnoreCase("name")) {
            if ((args.length < 1) || (args[1].length() > 16)) {
                sender.sendMessage("Please input a name (which is less than 17 characters)");
            } else {
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    names_map.put(p.getUniqueId(), args[0]);
                    sender.sendMessage("§bSuccessfully changed name!");
                }
            }
        }

        return true;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
