package com.astral.server;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Spawns bots near players and despawns them when out of range so the game stays smooth. */
public class BotManager {
    public static final int SPAWN_RADIUS = 48, DESPAWN_RADIUS = 80, MAX_PER_PLAYER = 12;
    private static final Random R = new Random();
    private static final Map<String, Entry> BOTS = new HashMap<>();
    private static int counter = 0;

    record Entry(String name, Role role, int rank, boolean war) {}
    public enum Role { CIVILIAN, LAWMAKER, RESISTANCE, STAGECOACH, MERCHANT, HUNTER }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerWorld w = p.getEntityWorld();
            // despawn: bots far from EVERY player leave
            for (var it = BOTS.values().iterator(); it.hasNext();) {
                Entry e = it.next();
                PlayerEntity bot = server.getPlayerManager().getPlayer(e.name());
                if (bot == null) { it.remove(); continue; }
                if (e.war()) continue; // war bots never despawn
                double nearest = Double.MAX_VALUE;
                for (ServerPlayerEntity q : server.getPlayerManager().getPlayerList()) nearest = Math.min(nearest, bot.distanceTo(q));
                if (nearest > DESPAWN_RADIUS) { BotBackend.remove(server, e.name()); it.remove(); }
            }
            long near = BOTS.values().stream().filter(e -> { var b = server.getPlayerManager().getPlayer(e.name()); return b != null && b.distanceTo(p) < DESPAWN_RADIUS; }).count();
            if (near >= MAX_PER_PLAYER) continue;
            PlayerProfile prof = ProfileStore.get(p.getUuid());
            Region reg = Region.at(p.getBlockPos());
            Role role = pickRole(reg, prof);
            int rank = reg == Region.SPAWN ? R.nextInt(2) : R.nextInt(5);   // spawn: only unranked + rank 1
            spawnNear(server, w, p, role, rank);
        }
    }

    private static Role pickRole(Region reg, PlayerProfile prof) {
        if (prof.wanted() && War.ruler == Faction.LAWMAKERS && R.nextInt(3) == 0) return Role.HUNTER;
        if (reg == Region.SPAWN) return R.nextInt(3) == 0 ? Role.LAWMAKER : Role.CIVILIAN; // spawn guards
        if (reg == Region.NEW_MERCHANT_CITY && R.nextInt(4) == 0) return Role.MERCHANT;
        if (R.nextInt(8) == 0) return Role.STAGECOACH;
        int roll = R.nextInt(10);
        return roll < 6 ? Role.CIVILIAN : roll < 8 ? Role.LAWMAKER : Role.RESISTANCE;
    }

    private static void spawnNear(MinecraftServer server, ServerWorld w, ServerPlayerEntity p, Role role, int rank) {
        double a = R.nextDouble() * Math.PI * 2, d = 24 + R.nextDouble() * (SPAWN_RADIUS - 24);
        Vec3d pos = new Vec3d(p.getX() + Math.cos(a) * d, MapBuilder.Y + 1, p.getZ() + Math.sin(a) * d);
        String name = prefix(role) + (++counter);
        BotBackend.spawn(server, name, pos, w);
        BOTS.put(name, new Entry(name, role, rank, false));
        // Equip next tick (bot must exist first).
        server.execute(() -> {
            ServerPlayerEntity bot = server.getPlayerManager().getPlayer(name);
            if (bot == null) return;
            switch (role) {
                case LAWMAKER, HUNTER -> Gear.lawmaker(w, bot, role == Role.HUNTER ? Math.max(rank, 2) : rank);
                case RESISTANCE -> Gear.resistance(w, bot);
                default -> Gear.civilian(w, bot);
            }
            if (role == Role.HUNTER) BotBackend.hunt(server, name, p); else BotBackend.walkRandomly(server, name);
        });
    }

    private static String prefix(Role r) {
        return switch (r) { case LAWMAKER, HUNTER -> "Law"; case RESISTANCE -> "Res"; case STAGECOACH -> "Coach"; case MERCHANT -> "Trader"; default -> "Npc"; };
    }

    /** Spawn a persistent war bot with gear. Returns its name. */
    public static void spawnWar(MinecraftServer server, ServerWorld w, String name, Role role, int rank, Vec3d pos, boolean leader) {
        BotBackend.spawn(server, name, pos, w);
        BOTS.put(name, new Entry(name, role, rank, true));
        server.execute(() -> {
            ServerPlayerEntity bot = server.getPlayerManager().getPlayer(name);
            if (bot == null) return;
            if (role == Role.RESISTANCE) Gear.resistance(w, bot); else Gear.lawmaker(w, bot, rank);
        });
    }
    public static boolean alive(MinecraftServer s, String name) {
        ServerPlayerEntity b = s.getPlayerManager().getPlayer(name); return b != null && b.isAlive();
    }
    public static void forget(MinecraftServer s, String name) { BotBackend.remove(s, name); BOTS.remove(name); }
    public static Entry byName(String n) { return BOTS.get(n); }
    public static Entry lookup(Entity e) { return BOTS.get(e.getName().getString()); }
    public static void clear(MinecraftServer s) { BOTS.keySet().forEach(n -> BotBackend.remove(s, n)); BOTS.clear(); }
}
