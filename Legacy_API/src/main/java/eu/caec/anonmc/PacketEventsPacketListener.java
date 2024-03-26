package eu.caec.anonmc;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static eu.caec.anonmc.AnonMC.names_map;
import static org.bukkit.Bukkit.getServer;

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
            WrapperPlayServerPlayerInfoUpdate infos = new WrapperPlayServerPlayerInfoUpdate(event);
            List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> pinfo = infos.getEntries();

            for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo info: pinfo) {
                info.setLatency(config.getBoolean("spoof-ping") ? ThreadLocalRandom.current().nextInt(1, 149) : info.getLatency());
                info.setGameProfile(new UserProfile(info.getGameProfile().getUUID(), names_map.get( info.getGameProfile().getUUID() ) ));
            }

            event.markForReEncode(true);
        }

        //prior to 1.19.3
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO) {
            WrapperPlayServerPlayerInfo infos = new WrapperPlayServerPlayerInfo(event);
            List<WrapperPlayServerPlayerInfo.PlayerData> pinfo = infos.getPlayerDataList();

            for (WrapperPlayServerPlayerInfo.PlayerData info: pinfo) {
                info.setPing(config.getBoolean("spoof-ping") ? ThreadLocalRandom.current().nextInt(1, 149) : info.getPing());
                info.setUserProfile(new UserProfile(info.getUserProfile().getUUID(), names_map.get( info.getUserProfile().getUUID() ) ));
            }

            event.markForReEncode(true);
        }

        //there is definitely a better way to do this
        if (event.getPacketType() == PacketType.Status.Server.RESPONSE) {
            WrapperStatusServerResponse response = new WrapperStatusServerResponse(event);

            String json = "{\n" +
                    "    \"version\": {\n" +
                    "        \"name\": \"" + getServer().getVersion() + "\",\n" +
                    "        \"protocol\": 47\n" +
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
                    /*"    \"favicon\": \"data:image/png;base64," + encodedFile + "\",\n" +*/
                    "    \"enforcesSecureChat\": false,\n" +
                    "    \"previewsChat\": true\n" +
                    "}";

            getServer().broadcastMessage(json);

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
