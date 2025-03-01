package com.example.dzialki.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;

import java.util.*;

public class Plot implements ConfigurationSerializable {
    private final String tag;
    private final UUID owner;
    private final Set<UUID> members;
    private final Location heartLocation;
    private Location teleportLocation;
    private final Set<UUID> invitedPlayers;

    public Plot(String tag, UUID owner, Location heartLocation) {
        this.tag = tag;
        this.owner = owner;
        this.heartLocation = heartLocation;
        this.teleportLocation = heartLocation.clone().add(0, 1, 0);
        this.members = new HashSet<>();
        this.invitedPlayers = new HashSet<>();
    }

    public Plot(Map<String, Object> map) {
        this.tag = (String) map.get("tag");
        this.owner = UUID.fromString((String) map.get("owner"));
        
        // Load heart location
        World world = Bukkit.getWorld((String) map.get("world"));
        double x = (double) map.get("x");
        double y = (double) map.get("y");
        double z = (double) map.get("z");
        this.heartLocation = new Location(world, x, y, z);
        
        // Load teleport location
        if (map.containsKey("teleport")) {
            Map<String, Object> teleportMap = (Map<String, Object>) map.get("teleport");
            World tpWorld = Bukkit.getWorld((String) teleportMap.get("world"));
            double tpX = (double) teleportMap.get("x");
            double tpY = (double) teleportMap.get("y");
            double tpZ = (double) teleportMap.get("z");
            float yaw = ((Number) teleportMap.get("yaw")).floatValue();
            float pitch = ((Number) teleportMap.get("pitch")).floatValue();
            this.teleportLocation = new Location(tpWorld, tpX, tpY, tpZ, yaw, pitch);
        } else {
            this.teleportLocation = heartLocation.clone().add(0, 1, 0);
        }
        
        // Load members
        this.members = new HashSet<>();
        List<String> membersList = (List<String>) map.get("members");
        if (membersList != null) {
            for (String member : membersList) {
                members.add(UUID.fromString(member));
            }
        }
        
        // Load invited players
        this.invitedPlayers = new HashSet<>();
        List<String> invitedList = (List<String>) map.getOrDefault("invited", new ArrayList<>());
        for (String invited : invitedList) {
            invitedPlayers.add(UUID.fromString(invited));
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("tag", tag);
        map.put("owner", owner.toString());
        map.put("world", heartLocation.getWorld().getName());
        map.put("x", heartLocation.getX());
        map.put("y", heartLocation.getY());
        map.put("z", heartLocation.getZ());
        
        // Save teleport location
        Map<String, Object> teleportMap = new HashMap<>();
        teleportMap.put("world", teleportLocation.getWorld().getName());
        teleportMap.put("x", teleportLocation.getX());
        teleportMap.put("y", teleportLocation.getY());
        teleportMap.put("z", teleportLocation.getZ());
        teleportMap.put("yaw", teleportLocation.getYaw());
        teleportMap.put("pitch", teleportLocation.getPitch());
        map.put("teleport", teleportMap);
        
        // Save members
        List<String> membersList = new ArrayList<>();
        for (UUID member : members) {
            membersList.add(member.toString());
        }
        map.put("members", membersList);
        
        // Save invited players
        List<String> invitedList = new ArrayList<>();
        for (UUID invited : invitedPlayers) {
            invitedList.add(invited.toString());
        }
        map.put("invited", invitedList);
        
        return map;
    }

    public String getTag() {
        return tag;
    }

    public UUID getOwner() {
        return owner;
    }

    public Location getHeartLocation() {
        return heartLocation.clone();
    }

    public Location getTeleportLocation() {
        return teleportLocation.clone();
    }

    public void setTeleportLocation(Location teleportLocation) {
        this.teleportLocation = teleportLocation.clone();
    }

    public boolean isOwner(UUID playerId) {
        return owner.equals(playerId);
    }

    public boolean isMember(UUID playerId) {
        return isOwner(playerId) || members.contains(playerId);
    }

    public void addMember(UUID playerId) {
        members.add(playerId);
        invitedPlayers.remove(playerId);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    public void invitePlayer(UUID playerId) {
        invitedPlayers.add(playerId);
    }

    public boolean isInvited(UUID playerId) {
        return invitedPlayers.contains(playerId);
    }

    public void removeInvite(UUID playerId) {
        invitedPlayers.remove(playerId);
    }

    public Set<UUID> getMembers() {
        return new HashSet<>(members);
    }

    public boolean isInPlot(Location location) {
        if (!location.getWorld().equals(heartLocation.getWorld())) {
            return false;
        }
        
        int x = location.getBlockX();
        int z = location.getBlockZ();
        
        int heartX = heartLocation.getBlockX();
        int heartZ = heartLocation.getBlockZ();
        
        return Math.abs(x - heartX) <= 8 && Math.abs(z - heartZ) <= 8;
    }
}