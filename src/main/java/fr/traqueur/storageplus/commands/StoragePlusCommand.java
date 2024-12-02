package fr.traqueur.storageplus.commands;

import fr.groupez.api.MainConfiguration;
import fr.groupez.api.commands.ZCommand;
import fr.groupez.api.configurations.Configuration;
import fr.groupez.api.messaging.Formatter;
import fr.groupez.api.messaging.Messages;
import fr.traqueur.commands.api.Arguments;
import fr.traqueur.commands.api.Command;
import fr.traqueur.storageplus.api.StoragePlusPlugin;
import fr.traqueur.storageplus.commands.admin.GiveCommand;
import fr.traqueur.storageplus.commands.admin.PurgeCommand;
import fr.traqueur.storageplus.commands.admin.ReloadCommand;
import org.bukkit.command.CommandSender;

import java.util.List;

public class StoragePlusCommand extends ZCommand<StoragePlusPlugin> {

    public StoragePlusCommand(StoragePlusPlugin plugin) {
        super(plugin, "storageplus");

        this.addAlias(Configuration.get(MainConfiguration.class).getCommandAliases().toArray(String[]::new));
        this.setPermission(Configuration.get(MainConfiguration.class).getCommandPermission());

        this.addSubCommand(new ReloadCommand(plugin), new GiveCommand(plugin), new PurgeCommand(plugin));
    }

    @Override
    public void execute(CommandSender commandSender, Arguments arguments) {
        sendHelp(commandSender, this.getSubcommands());
    }

    public void sendHelp(CommandSender sender, List<Command<?>> commands) {
        if(commands.isEmpty()) {
            return;
        }
        commands.forEach(subCommand -> {
            if(sender.hasPermission(subCommand.getPermission())) {
                Messages.COMMAND_SYNTAX_HELP.send(sender, Formatter.format("%syntax%", subCommand.getUsage()), Formatter.format("%description%", subCommand.getDescription()));
            }
            sendHelp(sender, subCommand.getSubcommands());
        });
    }
}
