package org.ipvp.queue.command;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.ipvp.queue.Queue;
import org.ipvp.queue.QueuePlugin;
import org.ipvp.queue.QueuedPlayer;

public class LeaveCommand extends QueuePluginCommand
{
    public LeaveCommand(QueuePlugin plugin) {
        super(plugin, "leavequeue");
    }

    @Override
    public void execute(CommandSender sender, String[] args)
    {
        if (!(sender instanceof ProxiedPlayer))
        {
            sender.sendMessage(TextComponent.fromLegacyText("You must be a player to use this command"));
        }
        else
        {
            ProxiedPlayer player = (ProxiedPlayer) sender;
            QueuedPlayer queued = getPlugin().getQueued(player);
            if (!queued.isInQueue())
            {
                sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "You are not in a queue"));
            }
            else
            {
                Queue queue = queued.getQueue();
                try
                {
                    queue.rememberPosition(player.getName(), queued.getPosition());
                    queue.remove(queued);
                }
                catch(NullPointerException e)
                {
                    System.out.println("Remove command tried to remove player from queue but got an exception: " + e);
                }

                queued.setQueue(null);
                sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GREEN + "You have left the queue"));
            }
        }
    }
}
