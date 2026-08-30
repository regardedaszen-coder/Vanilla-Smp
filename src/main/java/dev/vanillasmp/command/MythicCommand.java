package dev.vanillasmp.command;

import dev.vanillasmp.VanillaSMP;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class MythicCommand implements CommandExecutor, TabCompleter {
    private final VanillaSMP plugin;

    public MythicCommand(VanillaSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return plugin.handleCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        return plugin.tabComplete(sender, command, label, args);
    }
}
