package org.ipvp.queue;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.*;
import java.util.logging.Level;

import static net.md_5.bungee.api.ChatColor.*;

public class Queue extends Vector<QueuedPlayer>
{
    /**
     * The time between ticking the queue to send a new player
     */
    private static final long TIME_BETWEEN_SENDING_MILLIS = 1000L;

    private final QueuePlugin plugin;
    private final ServerInfo target;
    private boolean paused;
    private long lastSentTime;
    private long lastPositionMessageSent = 0;
    public int failedAttempts = 0;
    public long unpauseTime = Long.MAX_VALUE;

    private TimedList rememberedPlayers = new TimedList();

    public Queue(QueuePlugin plugin, ServerInfo target)
    {
        Objects.requireNonNull(plugin);
        Objects.requireNonNull(target);
        this.plugin = plugin;
        this.target = target;
    }

    /**
     * Returns the target server for this queue.
     *
     * @return Target server
     */
    public final ServerInfo getTarget() {
        return target;
    }

    /**
     * Returns whether this queue is paused or not.
     *
     * @return True if the queue is paused, false otherwise
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Sets the paused state of this queue.
     *
     * @param paused New paused state
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * Returns whether this queue can send the next player to the target server. This
     * method will only return true when the queue is not paused, has a player to send,
     * when the target server has space for the player, and if a specific interval has
     * passed since the last time a player was sent.
     *
     * @return True if the queue can send the next player, false otherwise
     */
    public boolean canSend()
    {
        if(System.currentTimeMillis() >= unpauseTime)
        {
            QueuePlugin.instance.debugWarn("Unpausing automatically.");
            paused = false;
            failedAttempts = 0;
            unpauseTime = Long.MAX_VALUE;
        }
        return !isPaused() &&
                !isEmpty() &&
                target.getPlayers().size() < plugin.getMaxPlayers(target) &&
                lastSentTime + TIME_BETWEEN_SENDING_MILLIS < System.currentTimeMillis();
    }

    /**
     * Saves players position in the queue when they leave so they can get it back if they rejoin.
     * @param playerName The player to save.
     */
    public void rememberPosition(String playerName, int index)
    {
        rememberedPlayers.rememberPosition(playerName, index);
    }

    /**
     * Removes a player's remembered queue position.
     * @param playerName The name of the player to forget.
     * @return true if player is forgotten, false if not found.
     */
    public boolean forgetPlayer(String playerName)
    {
        return rememberedPlayers.forgetPlayer(playerName);
    }

    public void rememberedPlayerCleanup()
    {
        rememberedPlayers.cleanup();
    }


    /**
     * Places a player in the queue, removes them from their old queue if they are already in one.
     * @param player The player to enqueue
     */
    public void enqueue(QueuedPlayer player)
    {
        if (player.getQueue() != null)
        {
            if (player.getQueue().getTarget().getName().equalsIgnoreCase(target.getName()))
            {
                player.getHandle().sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "You are already queued for this server"));
                return;
            }
            else
            {
                player.getHandle().sendMessage(TextComponent.fromLegacyText(ChatColor.YELLOW + "You were removed from the " + player.getQueue().getTarget().getName() + " queue."));
                player.getQueue().remove(player);
            }
        }

        try
        {
            player.setQueue(this);
            int index = getInsertionIndex(player);
            if(index < 0 || index >= size())
            {
                add(player);
            }
            else
            {
                add(index, player);
            }
        }
        catch(NullPointerException | IndexOutOfBoundsException e)
        {
            // Player is added at the end if an error occured when trying to find their position
            player.setQueue(this);
            add(player);
        }

        player.getHandle().sendMessage(TextComponent.fromLegacyText(ChatColor.GREEN + "You have joined the queue for " + QueuePlugin.capitalizeFirstLetter(target.getName())));
        player.getHandle().sendMessage(TextComponent.fromLegacyText(String.format(YELLOW + "You are currently in position " + GREEN + "%d " + YELLOW + "of " + GREEN + "%d", player.getPosition() + 1, size())));
        SendPriorityMessage(player.getHandle());
        if (paused)
        {
            player.getHandle().sendMessage(TextComponent.fromLegacyText(ChatColor.GRAY + "The queue you are currently in is paused"));
        }
    }

    /**
     * Sends EMC specific priority messages.
     * @param player The player to send messages to.
     */
    private void SendPriorityMessage(ProxiedPlayer player)
    {
        if (player.hasPermission("queue.priority.staff"))
        {
            player.sendMessage(TextComponent.fromLegacyText(DARK_GREEN + "Staff" + GREEN + " access activated."));
        }
        else if (player.hasPermission("queue.priority.premium"))
        {
            player.sendMessage(TextComponent.fromLegacyText(LIGHT_PURPLE + "Premium" + GREEN + " access activated."));
        }
        else if (player.hasPermission("queue.priority.donator3"))
        {
            player.sendMessage(TextComponent.fromLegacyText(BLUE + "Blue" + GREEN + " donator access activated."));
        }
        else if (player.hasPermission("queue.priority.donator2"))
        {
            player.sendMessage(TextComponent.fromLegacyText(DARK_PURPLE + "Purple" + GREEN + " donator access activated."));
        }
        else if (player.hasPermission("queue.priority.donator"))
        {
            player.sendMessage(TextComponent.fromLegacyText(YELLOW + "Yellow" + GREEN + " donator access activated."));
        }
        else if (player.hasPermission("queue.priority.priority"))
        {
            player.sendMessage(TextComponent.fromLegacyText(GREEN + "Priority access activated."));
        }
    }

    private int getInsertionIndex(QueuedPlayer player)
    {
        int savedIndex = Math.min(rememberedPlayers.getRememberedPosition(player.getHandle().getName().toLowerCase()), this.size());
        int priorityIndex = getIndexByPriority(player.getPriority());

        if (savedIndex < priorityIndex)
        {
            plugin.debugLog("Inserted player " + player.getHandle().getName() + " into the " + target.getName() + " queue in saved position " + savedIndex + ".");
            plugin.debugLog("Priority position was " + priorityIndex + " and queue size was " + size() + ". Priority weight was " + player.getPriority() + ".");
            return savedIndex;
        }
        else
        {
            plugin.debugLog("Inserted player " + player.getHandle().getName() + " into the " + target.getName() + " queue in priority position " + priorityIndex + ".");
            plugin.debugLog("Saved position was " + savedIndex + " and queue size was " + size() + ". Priority weight was " + player.getPriority() + ".");
            return priorityIndex;
        }
    }

    /**
     * Searches for and returns a valid index to insert a player with a specified
     * priority weight.
     *
     * @param playerWeight Priority weight to search for
     * @return Index to insert the priority at, returned index i will be {@code 0 <= i < {@link #size()}}
     */
    private int getIndexByPriority(int playerWeight)
    {
        if (isEmpty() || playerWeight == -1)
        {
            return 0;
        }

        // Changed to not place priority players in the first 3 slots
        int slot = size();
        for (int i = 3; i < size(); i++)
        {
            if (playerWeight <= get(i).getPriority())
            {
                slot = i+1;
            }
        }

        return slot;
    }

    private void sendProgressMessages()
    {
        if (lastPositionMessageSent + 3000 > System.currentTimeMillis())
        {
            return;
        }

        this.forEach(player ->
        {
            try
            {
                rememberPosition(player.getHandle().getName(), player.getPosition());
                player.getHandle().sendMessage(TextComponent.fromLegacyText(String.format(YELLOW + "You are currently in position " + GREEN + "%d " + YELLOW + "of " + GREEN + "%d " + YELLOW + "for " + QueuePlugin.capitalizeFirstLetter(getTarget().getName()) + "",
                        player.getPosition() + 1, player.getQueue().size(), player.getQueue().getTarget().getName())));
                if (player.getQueue().isPaused())
                {
                    player.getHandle().sendMessage(TextComponent.fromLegacyText(ChatColor.GRAY + "The queue you are currently in is paused"));
                }
            }
            catch (Exception e)
            {
                QueuePlugin.instance.debugError("Error sending update message to player: " + player.getHandle().getName() + " in the queue to " + target.getName());
            }
        });
        lastPositionMessageSent = System.currentTimeMillis();
    }

    /**
     * Sends the next player at index {@code 0} to the target server.
     */
    public void sendNext()
    {
        if (!canSend())
        {
            return;
        }

        if(failedAttempts >= 5)
        {
            paused = true;
            unpauseTime = System.currentTimeMillis() + 20000;
            QueuePlugin.instance.debugError("Queue is paused for 30 seconds due to repeated failed attempts to send players.");
            for (QueuedPlayer player : this)
            {
                player.getHandle().sendMessage(TextComponent.fromLegacyText(RED + "Queue is paused for 30 seconds as the target server refused the last 5 players."));
            }
            return;
        }

        lastSentTime = System.currentTimeMillis();
        QueuedPlayer next = remove(0);

        if(next == null)
        {
            return;
        }

        if(next.getHandle() == null)
        {
            return;
        }

        next.setQueue(null);
        if (next.getHandle().getServer().getInfo().getName().equals(this.target.getName()))
        {
            return;
        }

        next.getHandle().sendMessage(TextComponent.fromLegacyText(GREEN + "Sending you to " + QueuePlugin.capitalizeFirstLetter(getTarget().getName()) + "..."));
        this.rememberPosition(next.getHandle().getName(), 0);
        plugin.getLogger().log(Level.INFO, "Preparing to send " + next.getHandle().getName() + " to " + target.getName() + " via Queue.");

        next.getHandle().connect(target, (result, error) ->
        {
            // What do we do if they can't connect?
            if (result)
            {
                try
                {
                    plugin.getLogger().log(Level.INFO, next.getHandle().getName() + " was sent to " + target.getName() + " via Queue.");
                    next.getHandle().sendMessage(TextComponent.fromLegacyText(GREEN + "You have been sent to " + QueuePlugin.capitalizeFirstLetter(getTarget().getName()) + ""));
                    failedAttempts = 0;
                    sendProgressMessages();
                }
                catch(Exception e)
                {
                    plugin.debugError("[ConnectHandler] Something happened after successful connection: " + e);
                }
            }
            else
            {
                QueuePlugin.instance.debugError("[SendNext] Failed to send player " + next.getHandle().getName() + " to server " + target.getName() + ". Error: " + error);
                next.getHandle().sendMessage(TextComponent.fromLegacyText(RED + "Unable to connect to " + QueuePlugin.capitalizeFirstLetter(getTarget().getName()) + "."));
                next.getHandle().sendMessage(TextComponent.fromLegacyText(RED + "Attempting to requeue you..."));
                // Gets the player object again to make sure there isn't some concurrent modification issue with bungeecord causing issues.
                QueuedPlayer newPlayer = plugin.getQueued(plugin.getProxy().getPlayer(next.getHandle().getName()));
                newPlayer.setQueue(this);
                add(0, newPlayer);
                failedAttempts++;
                QueuePlugin.instance.debugError("Failed count is now at " + failedAttempts);
                lastSentTime = System.currentTimeMillis() + 1000;
            }
        });
    }
}