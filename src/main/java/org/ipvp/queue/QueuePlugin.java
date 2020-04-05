package org.ipvp.queue;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import org.ipvp.queue.command.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class QueuePlugin extends Plugin implements Listener
{
    private Configuration config;

    private ConcurrentHashMap<ServerInfo, Integer> maxPlayers = new ConcurrentHashMap<>();
    public TreeMap<String, Queue> queues = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private ConcurrentHashMap<ProxiedPlayer, QueuedPlayer> queuedPlayers = new ConcurrentHashMap<>();

    public boolean debug = true;

    public static QueuePlugin instance;

    @Override
    public void onEnable()
    {
        instance = this;
        try
        {
            this.config = loadConfiguration();
        }
        catch (IOException e)
        {
            getLogger().log(Level.SEVERE, "Failed to load config.yml", e);
            return;
        }

        getProxy().getServers().values().forEach(this::setupServer);
        getProxy().registerChannel("queue:join");
        getProxy().getPluginManager().registerListener(this, this);

        // Checks if any queues are ready to send players
        getProxy().getScheduler().schedule(this, () ->
        {
            for (Queue queue : queues.values())
            {
                try
                {
                    queue.sendNext();
                }
                catch (Exception e)
                {
                    getLogger().severe("[SendNextHandler] Failed to send next player to server " + queue.getTarget().getName() + ". Error: " + e);
                    queue.failedAttempts++;
                }
            }
        }, 250, 250, TimeUnit.MILLISECONDS);

        // Cleans up the list of all remembered players in all queues
        getProxy().getScheduler().schedule(this, () ->
        {
            for (Queue queue : queues.values())
            {
                try
                {
                    queue.rememberedPlayerCleanup();
                }
                catch (Exception e)
                {
                    getLogger().severe("[QueueCleaner] Failed to clean up the list of remembered positions. Error: " + e);
                }
            }
        }, 10, 10, TimeUnit.SECONDS);

        // Registers commands
        getProxy().getPluginManager().registerCommand(this, new LeaveCommand(this));
        getProxy().getPluginManager().registerCommand(this, new PauseCommand(this));
        getProxy().getPluginManager().registerCommand(this, new QueueCommand(this));
        getProxy().getPluginManager().registerCommand(this, new JoinCommand(this));
    }

    public Configuration loadConfiguration() throws IOException
    {
        File file = new File(getDataFolder(), "config.yml");

        if (file.exists()) {
            return ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        }

        // Create the file to save
        if (!file.getParentFile().exists())
        {
            file.getParentFile().mkdirs();
        }
        file.createNewFile();

        // Load the default provided configuration and save it to the file
        Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(getResourceAsStream("config.yml"));
        ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, file);
        return config;
    }

    public void refreshMaxPlayers()
    {
        for (Map.Entry<ServerInfo, Integer> entry : maxPlayers.entrySet())
        {
            entry.getKey().ping((p, err) ->
            {
                if (p == null || p.getPlayers() == null)
                {
                    return;
                }
                entry.setValue(p.getPlayers().getMax());
            });
        }
    }

    // Gets the max players for a server and caches it for later use
    private void setupServer(ServerInfo info)
    {
        final String name = info.getName();
        info.ping((p, err) ->
        {
            if (p == null || p.getPlayers() == null)
            {
                return;
            }
            maxPlayers.put(info, p.getPlayers().getMax());
            if (!queues.containsKey(name))
            {
                queues.put(name, new Queue(this, info));
            }
        });
    }

    /**
     * Returns all current queues for target servers
     *
     * @return Loaded queues for all servers
     */
    public Collection<Queue> getQueues() {
        return queues.values();
    }

    /**
     * Returns a Queue for a specified server
     *
     * @param server Server to check
     * @return Queue for the server
     */
    public Queue getQueue(String server)
    {
        return queues.get(server);
    }

    /**
     * Returns a players QueuedPlayer wrapper
     *
     * @param player Player to find
     * @return QueuedPlayer wrapper
     */
    public QueuedPlayer getQueued(ProxiedPlayer player)
    {
        if (!queuedPlayers.containsKey(player))
        {
            debugLog("Player did not already exist");
            QueuedPlayer queued = new QueuedPlayer(player, getPriority(player));
            queuedPlayers.put(player, queued);
            return queued;
        }
        return queuedPlayers.get(player);
    }

    /**
     * Returns all QueuedPlayers
     *
     * @return All QueuedPlayer wrappers
     */
    public Collection<QueuedPlayer> getQueued() {
        return queuedPlayers.values();
    }

    /**
     * Returns the queue priority of a player
     * 
     * @param player Player to check
     * @return players priority
     */
    public int getPriority(ProxiedPlayer player)
    {
        for (String rank : config.getSection("priorities").getKeys())
        {
            if (player.hasPermission("queue.priority." + rank))
            {
                return config.getInt("priorities." + rank);
            }
        }
        return 0;
    }

    public void debugLog(String message)
    {
        if(debug)
        {
            getLogger().info(message);
        }
    }

    public void debugWarn(String message)
    {
        if(debug)
        {
            getLogger().warning(message);
        }
    }

    public void debugError(String message)
    {
        if(debug)
        {
            getLogger().severe(message);
        }
    }

    /**
     * Gets the maximum players allowed on a server in this bungee instance
     *
     * @param server the name of the server
     * @return the cached maximum amount of players allowed on the server. will return -1
     * and make a call to load the data when the server exists but the data has not yet
     * been loaded.
     * @throws IllegalArgumentException if a server with the name does not exist
     */
    public int getMaxPlayers(ServerInfo server)
    {
        if (!maxPlayers.containsKey(server))
        {
            setupServer(server);
            return -1;
        }

        return maxPlayers.get(server);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event)
    {
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String channel = event.getTag();
        if (channel.equals("queue:join"))
        {
            UUID uuid = UUID.fromString(in.readUTF());
            ProxiedPlayer player = getProxy().getPlayer(uuid);

            if (player == null)
            {
                return;
            }

            QueuedPlayer queued = getQueued(player);
            String target = in.readUTF();

            if(player.getServer().getInfo().getName().equals(target))
            {
                player.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "You are already on this server!"));
                return;
            }
            Queue queue = getQueue(target);

            if (queue == null)
            {
                ServerInfo server = getProxy().getServerInfo(target); // Find server
                if (server == null)
                {
                    player.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Invalid server provided"));
                    return;
                }
                else
                {
                    queue = new Queue(this, server);
                    queues.put(server.getName(), queue);
                }
            }
            queue.enqueue(queued);
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event)
    {
        handleLeave(event.getPlayer());
        queuedPlayers.remove(event.getPlayer());
    }

    @EventHandler
    public void onSwitchServer(ServerSwitchEvent event)
    {
        QueuedPlayer queued = queuedPlayers.get(event.getPlayer());
        if (queued != null && queued.getQueue() != null)
        {
            // If the server they have connected to is the one they are queueing to remove them from the queue
            if(queued.getQueue().getTarget().getName().equals(event.getPlayer().getServer().getInfo().getName()))
            {
                handleLeave(event.getPlayer());
            }
        }
    }

    public static String capitalizeFirstLetter(String original)
    {
        if (original == null || original.length() == 0)
        {
            return original;
        }
        return original.substring(0, 1).toUpperCase() + original.substring(1);
    }

    private void handleLeave(ProxiedPlayer player)
    {
        QueuedPlayer queued = queuedPlayers.get(player);
        if (queued != null && queued.getQueue() != null)
        {
            Queue queue = queued.getQueue();
            try
            {
                queue.rememberPosition(player.getName(), queued.getPosition());
                queue.remove(queued);
            }
            catch (NullPointerException e)
            {
                System.out.println("Exception occured when player left: " + e);
            }
            queued.setQueue(null);
        }
    }
}
