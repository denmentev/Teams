package org.plugin.teams.models;

import java.util.*;
import java.util.stream.Collectors;

public class Team {
    private final String id;
    private String name;
    private String description; // This is now the short description (desc in DB)
    private UUID ownerId;
    private final long createdAt;
    private final Map<UUID, TeamRole> members;

    // Additional fields
    private String fullDescription; // fulldesc in DB
    private int mid;
    private String bannerLink; // Banner link field

    // Constructor for new teams
    public Team(String id, String name, UUID ownerId) {
        this.id = id;
        this.name = name;
        this.description = "";
        this.ownerId = ownerId;
        this.createdAt = System.currentTimeMillis();
        this.members = new HashMap<>();
        this.members.put(ownerId, TeamRole.OWNER);

        // Initialize additional fields
        this.fullDescription = "";
        this.mid = 0; // Will be set by DatabaseManager
        this.bannerLink = null; // Initialize banner link
    }

    // Constructor for loading from database
    public Team(String id, String name, String description, UUID ownerId, long createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
        this.members = new HashMap<>();

        // Initialize additional fields
        this.fullDescription = "";
        this.mid = 0;
        this.bannerLink = null; // Initialize banner link
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Map<UUID, TeamRole> getMembers() {
        return new HashMap<>(members);
    }

    // Additional getters
    public String getFullDescription() {
        return fullDescription;
    }

    public int getMid() {
        return mid;
    }

    // Getter for banner link
    public String getBannerLink() {
        return bannerLink;
    }

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        // Ensure it's not longer than 100 characters
        if (description != null && description.length() > 100) {
            this.description = description.substring(0, 100);
        } else {
            this.description = description != null ? description : "";
        }
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    // Additional setters
    public void setFullDescription(String fullDescription) {
        this.fullDescription = fullDescription != null ? fullDescription : "";
    }

    public void setMid(int mid) {
        this.mid = mid;
    }

    // Setter for banner link
    public void setBannerLink(String bannerLink) {
        this.bannerLink = bannerLink;
    }

    // Member management
    public void addMember(UUID playerId, TeamRole role) {
        members.put(playerId, role);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public TeamRole getMemberRole(UUID playerId) {
        return members.get(playerId);
    }

    public void setMemberRole(UUID playerId, TeamRole role) {
        if (members.containsKey(playerId)) {
            members.put(playerId, role);
        }
    }

    // Get members by role
    public List<UUID> getMembersByRole(TeamRole role) {
        return members.entrySet().stream()
                .filter(entry -> entry.getValue() == role)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // Count members by role
    public int countMembersByRole(TeamRole role) {
        return (int) members.values().stream()
                .filter(r -> r == role)
                .count();
    }

    // Get all online members
    public List<UUID> getOnlineMembers() {
        return members.keySet().stream()
                .filter(uuid -> org.bukkit.Bukkit.getPlayer(uuid) != null)
                .collect(Collectors.toList());
    }

    // Check if team has space for more managers
    public boolean canPromoteToManager(int maxManagers) {
        return countMembersByRole(TeamRole.MANAGER) < maxManagers;
    }

    // Check if team has a banner
    public boolean hasBanner() {
        return bannerLink != null && !bannerLink.isEmpty();
    }

    // Check if banner link is valid Imgur link
    public boolean hasValidImgurBanner() {
        return hasBanner() && (bannerLink.startsWith("https://imgur.com/") ||
                bannerLink.startsWith("https://i.imgur.com/") ||
                bannerLink.startsWith("http://imgur.com/") ||
                bannerLink.startsWith("http://i.imgur.com/"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Team team = (Team) o;
        return Objects.equals(id, team.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Team{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", members=" + members.size() +
                ", mid=" + String.format("%03d", mid) +
                ", hasBanner=" + hasBanner() +
                '}';
    }
}