package com.astral.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

/**
 * ONLY class that talks to HeroBot. I couldn't verify HeroBot's API, so this
 * version dispatches console commands. Replace the bodies with direct API calls
 * (spawn / remove / equip / walkTo / attack) once you check HeroBot's docs.
 */
public class BotBackend {
    public static void spawn(MinecraftServer s, String name, Vec3d pos, ServerWorld w) {
        run(s, "bot spawn " + name + " at " + pos.x + " " + pos.y + " " + pos.z);
    }
    public static void remove(MinecraftServer s, String name) { run(s, "bot kill " + name); }
    public static void walkRandomly(MinecraftServer s, String name) { run(s, "bot move " + name + " wander"); }
    public static void hunt(MinecraftServer s, String name, ServerPlayerEntity target) { run(s, "bot attack " + name + " " + target.getName().getString()); }
    public static void attackBot(MinecraftServer s, String name, String target) { run(s, "bot attack " + name + " " + target); }
    public static void follow(MinecraftServer s, String name, ServerPlayerEntity target) { run(s, "bot follow " + name + " " + target.getName().getString()); }

    private static void run(MinecraftServer s, String cmd) {
        s.getCommandManager().executeWithPrefix(s.getCommandSource().withSilent(), cmd);
    }
}
