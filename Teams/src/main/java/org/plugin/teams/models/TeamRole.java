package org.plugin.teams.models;

public enum TeamRole {
    OWNER("Owner", true, true, true, true, true),
    MANAGER("Manager", true, true, false, false, false),
    MEMBER("Member", false, false, false, false, false);

    private final String displayName;
    private final boolean canInvite;
    private final boolean canKick;
    private final boolean canPromote;
    private final boolean canEditTeam;
    private final boolean canDeleteTeam;

    TeamRole(String displayName, boolean canInvite, boolean canKick, boolean canPromote,
             boolean canEditTeam, boolean canDeleteTeam) {
        this.displayName = displayName;
        this.canInvite = canInvite;
        this.canKick = canKick;
        this.canPromote = canPromote;
        this.canEditTeam = canEditTeam;
        this.canDeleteTeam = canDeleteTeam;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean canInvite() {
        return canInvite;
    }

    public boolean canKick() {
        return canKick;
    }

    public boolean canPromote() {
        return canPromote;
    }

    public boolean canEditTeam() {
        return canEditTeam;
    }

    public boolean canDeleteTeam() {
        return canDeleteTeam;
    }

    public boolean isHigherThan(TeamRole other) {
        return this.ordinal() < other.ordinal();
    }

    public boolean isLowerThan(TeamRole other) {
        return this.ordinal() > other.ordinal();
    }

    public static TeamRole fromString(String role) {
        for (TeamRole r : values()) {
            if (r.name().equalsIgnoreCase(role)) {
                return r;
            }
        }
        return MEMBER;
    }
}