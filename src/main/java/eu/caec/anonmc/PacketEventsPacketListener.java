package eu.caec.anonmc;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse;
import net.kyori.adventure.text.Component;
import eu.caec.anonmc.AnonMC;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static eu.caec.anonmc.AnonMC.names_map;
import static org.bukkit.Bukkit.getServer;
import static org.bukkit.plugin.java.JavaPlugin.getPlugin;

public class PacketEventsPacketListener extends PacketListenerAbstract {
    public PacketEventsPacketListener() {
        super(PacketListenerPriority.HIGH);
    }

    FileConfiguration config = AnonMC.instance.getConfig();

    /*
    * SENDING
    * edit player info to spoof username and ping if true in config
    *
    * spoof player names in server list ping
    * */
    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            User user = event.getUser();
            int actual_latency = getServer().getPlayer(user.getUUID()).getPing();

            WrapperPlayServerPlayerInfoUpdate infos = new WrapperPlayServerPlayerInfoUpdate(event);
            List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> pinfo = infos.getEntries();

            for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo info: pinfo) {
                info.setLatency(
                    config.getBoolean("spoof-ping") ? ThreadLocalRandom.current().nextInt(1, 149) : actual_latency
                );
                info.setGameProfile(new UserProfile(user.getUUID(), names_map.get(user.getUUID())));
            }

            event.markForReEncode(true);
        }

        //there is definitely a better way to do this
        if (event.getPacketType() == PacketType.Status.Server.RESPONSE) {
            WrapperStatusServerResponse response = new WrapperStatusServerResponse(event);

            String json = "{\n" +
                    "    \"version\": {\n" +
                    "        \"name\": \"" + getServer().getVersion() + "\",\n" +
                    "        \"protocol\":" + 764 + "\n" +
                    "    },\n" +
                    "    \"players\": {\n" +
                    "        \"max\": " + getServer().getMaxPlayers() + ",\n" +
                    "        \"online\": " + getServer().getOnlinePlayers().size() + ",\n" +
                    "        \"sample\": [\n";

            for (Player p : getServer().getOnlinePlayers()) {
                if ( names_map.containsKey(p.getUniqueId()) ) {
                    json +=
                            "            {\n" +
                            "                \"name\": \"" + names_map.get(p.getUniqueId()) + "\",\n" +
                            "                \"id\": \"" + UUID.randomUUID() + "\"\n" +
                            "            },\n";
                }
            }

            if (!getServer().getOnlinePlayers().isEmpty()) {
                json = json.substring(0, json.length() - 2); //we want to get rid of the comma and newline for the last player
            }

            json +=
                    "\n        ]\n" +
                    "    },\n" +
                    "    \"description\": {\n" +
                    "        \"text\": \"" + getServer().getMotd() + "\"\n" +
                    "    },\n" +
                    "    \"favicon\": \"data:image/png;base64,<data>\",\n" +
                    "    \"enforcesSecureChat\": false,\n" +
                    "    \"previewsChat\": true\n" +
                    "}";

            //getServer().broadcastMessage(json);

            response.setComponentJson(json);
            event.markForReEncode(true);
        }
    }

    /*
    * RECEIVING
    * cancel tab complete
    * preferably if there's usernames, but it's not mandatory
    * */
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.TAB_COMPLETE) {
            event.setCancelled(true);
        }
    }
}
