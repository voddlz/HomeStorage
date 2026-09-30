package com.leekwater.homestorage.command;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import com.leekwater.homestorage.storage.HomeRemoval;
import com.leekwater.homestorage.storage.StorageHome;
import com.leekwater.homestorage.storage.StorageManager;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Admin commands (game master and above). There is deliberately no command that creates a Home: a Home
 * only ever appears when someone places an Infinity Home Chest.
 *
 *   /homestorage list
 *   /homestorage delete here [force]     the Home whose area you are standing in
 *   /homestorage delete <id> [force]     a Home by id (tab-complete lists them)
 *
 * Deleting a Home that still holds items is refused unless "force" is given, because force destroys them.
 */
public final class HomeCommands {
    private HomeCommands() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
                Commands.literal("homestorage")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("list").executes(HomeCommands::list))
                        .then(Commands.literal("delete")
                                .then(Commands.literal("here")
                                        .executes(ctx -> deleteHere(ctx, false))
                                        .then(Commands.literal("force").executes(ctx -> deleteHere(ctx, true))))
                                .then(Commands.argument("home", UuidArgument.uuid())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                StorageManager.get(ctx.getSource().getServer()).all().stream()
                                                        .map(home -> home.id().toString()),
                                                builder))
                                        .executes(ctx -> deleteById(ctx, false))
                                        .then(Commands.literal("force").executes(ctx -> deleteById(ctx, true)))))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        StorageManager manager = StorageManager.get(source.getServer());
        Collection<StorageHome> homes = manager.all();

        if (homes.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.homestorage.list.none"), false);
            return 0;
        }
        for (StorageHome home : homes) {
            String owner = home.ownerName().isEmpty() ? home.owner().toString() : home.ownerName();
            Component state = Component.translatable(home.active()
                    ? "command.homestorage.state.active" : "command.homestorage.state.inactive");
            source.sendSuccess(() -> Component.translatable("command.homestorage.list.entry",
                    home.id().toString(), owner, home.dimension().identifier().toString(),
                    home.chestPos().toShortString(), state, manager.storageTypeCount(home.id())), false);
        }
        return homes.size();
    }

    private static int deleteById(CommandContext<CommandSourceStack> ctx, boolean force) {
        CommandSourceStack source = ctx.getSource();
        UUID id = UuidArgument.getUuid(ctx, "home");
        Optional<StorageHome> home = StorageManager.get(source.getServer()).find(id);
        if (home.isEmpty()) {
            source.sendFailure(Component.translatable("command.homestorage.delete.unknown"));
            return 0;
        }
        return delete(source, home.get(), force);
    }

    private static int deleteHere(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        BlockPos here = source.getPlayerOrException().blockPosition();
        Optional<StorageHome> home = StorageManager.get(source.getServer()).findContaining(source.getLevel().dimension(), here);
        if (home.isEmpty()) {
            source.sendFailure(Component.translatable("command.homestorage.delete.none_here"));
            return 0;
        }
        return delete(source, home.get(), force);
    }

    private static int delete(CommandSourceStack source, StorageHome home, boolean force) {
        int itemTypes = StorageManager.get(source.getServer()).storageTypeCount(home.id());
        if (itemTypes > 0 && !force) {
            source.sendFailure(Component.translatable("command.homestorage.delete.not_empty", itemTypes));
            return 0;
        }
        HomeRemoval.delete(source.getServer(), home);
        source.sendSuccess(() -> Component.translatable("command.homestorage.delete.done",
                home.ownerName().isEmpty() ? home.owner().toString() : home.ownerName(), home.chestPos().toShortString()), true);
        return 1;
    }
}
