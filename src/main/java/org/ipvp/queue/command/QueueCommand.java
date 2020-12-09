package org.ipvp.queue.command;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.ipvp.queue.Queue;
import org.ipvp.queue.QueuePlugin;
import org.ipvp.queue.QueuedPlayer;

import java.io.IOException;

import static net.md_5.bungee.api.ChatColor.*;

public class QueueCommand extends QueuePluginCommand
{
    public QueueCommand(QueuePlugin plugin)
    {
        super(plugin, "queue");
    }

    @Override
    public void execute(CommandSender sender, String[] args)
    {
        if(args.length > 0)
        {
            switch(args[0])
            {
                case "debug":
                {
                    if(sender.hasPermission("queue.debug"))
                    {
                        getPlugin().debug = !getPlugin().debug;
                        sender.sendMessage(TextComponent.fromLegacyText(YELLOW + "Debug mode has been set to " + getPlugin().debug));
                    }
                    else
                    {
                        sender.sendMessage(TextComponent.fromLegacyText(RED + "You don't have permission to use /queue debug."));
                    }
                    return;
                }
                case "list":
                {
                    if(sender.hasPermission("queue.list"))
                    {
                        if(args.length > 1)
                        {
                            Queue queue = getPlugin().getQueue(args[1]);

                            if (queue == null)
                            {
                                sender.sendMessage(TextComponent.fromLegacyText(RED + "There is no queue set up for that server."));
                            }
                            else
                            {
                                StringBuilder response = new StringBuilder();
                                response.append(GOLD).append("-------------- Players in queue --------------\n");
                                response.append(GREEN).append(String.format("%-5s %-40s %s", "Pos.", "Player.", "Priority.\n"));
                                for (QueuedPlayer queuedPlayer : queue)
                                {
                                    response.append(GOLD).append(String.format("%-5s %s%-40s %s(%d)\n", (queuedPlayer.getPosition() + 1) + ".", GREEN, queuedPlayer.getHandle().getName(), GOLD, queuedPlayer.getPriority()));
                                }
                                sender.sendMessage(TextComponent.fromLegacyText(response.toString()));
                            }
                        }
                        else
                        {
                            sender.sendMessage(TextComponent.fromLegacyText(RED + "Invalid arguments."));
                        }
                    }
                    else
                    {
                        sender.sendMessage(TextComponent.fromLegacyText(RED + "You don't have permission to use /queue list."));
                    }
                    return;
                }
                case "forget":
                {
                    if(sender.hasPermission("queue.forget"))
                    {
                        if(args.length > 1)
                        {
                            boolean found = false;
                            for (Queue queue : getPlugin().getQueues())
                            {
                                if (queue.forgetPlayer(args[1]))
                                {
                                    sender.sendMessage(TextComponent.fromLegacyText(YELLOW + "Player position forgotten in the " + queue.getTarget().getName() + " queue."));
                                    found = true;
                                }
                            }
                            if(!found)
                            {
                                sender.sendMessage(TextComponent.fromLegacyText(RED + "Player was not saved in any queue, make sure they have left the queue before running this command."));
                            }
                        }
                        else
                        {
                            sender.sendMessage(TextComponent.fromLegacyText(RED + "Invalid arguments."));
                        }
                    }
                    else
                    {
                        sender.sendMessage(TextComponent.fromLegacyText(RED + "You don't have permission to use /queue forget."));
                    }
                    return;
                }
                case "reload":
                {
                    if(!sender.hasPermission("queue.reload")) {
                        sender.sendMessage(TextComponent.fromLegacyText(RED + "You don't have permission to use /queue reload."));
                        return;
                    }
                    
                    plugin.refreshMaxPlayers();
                    try
                    {
                        plugin.loadConfiguration();
                    }
                    catch (IOException e)
                    {
                        sender.sendMessage(TextComponent.fromLegacyText(RED + "Error reloading config."));
                        plugin.getLogger().severe("Error reloading config:\n" + e);
                        return;
                    }
                    sender.sendMessage(TextComponent.fromLegacyText(YELLOW + "Reloading config and server info."));
                    return;
                }
                default:
                {
                    sender.sendMessage(TextComponent.fromLegacyText(RED + "That command does not exist."));
                    return;
                }
            }

        }


        // All other /queue variations
        if (!(sender instanceof ProxiedPlayer))
        {
            sender.sendMessage(TextComponent.fromLegacyText("You must be a player to use this command."));
        }
        else
        {
            ProxiedPlayer player = (ProxiedPlayer) sender;
            QueuedPlayer queuedPlayer = getPlugin().getQueued(player);

            Queue queue = queuedPlayer.getQueue();

            if (queue == null)
            {
                sender.sendMessage(TextComponent.fromLegacyText(RED + "You are not in a queue."));
            }
            else
            {
                sender.sendMessage(TextComponent.fromLegacyText(String.format(YELLOW + "You are currently in position " + GREEN + "%d " + YELLOW + "of " + GREEN + "%d.", queuedPlayer.getPosition() + 1, queue.size(), queue.getTarget().getName())));
            }
        }
    }
}
