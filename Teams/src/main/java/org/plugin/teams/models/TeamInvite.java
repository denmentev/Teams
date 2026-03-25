package org.plugin.teams.models;

import java.util.UUID;

public class TeamInvite {
    private final String teamId;
    private final UUID inviterId;
    private final UUID targetId;
    private final long timestamp;
    private static final long EXPIRY_TIME = 20 * 1000; // 20 seconds

    public TeamInvite(String teamId, UUID inviterId, UUID targetId) {
        this.teamId = teamId;
        this.inviterId = inviterId;
        this.targetId = targetId;
        this.timestamp = System.currentTimeMillis();
    }

    public String getTeamId() {
        return teamId;
    }

    public UUID getInviterId() {
        return inviterId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > EXPIRY_TIME;
    }

    public long getRemainingTime() {
        long elapsed = System.currentTimeMillis() - timestamp;
        if (elapsed >= EXPIRY_TIME) return 0;
        return (EXPIRY_TIME - elapsed) / 1000; // return in seconds
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TeamInvite that = (TeamInvite) o;
        return teamId.equals(that.teamId) &&
                inviterId.equals(that.inviterId) &&
                targetId.equals(that.targetId);
    }

    @Override
    public int hashCode() {
        int result = teamId.hashCode();
        result = 31 * result + inviterId.hashCode();
        result = 31 * result + targetId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "TeamInvite{" +
                "teamId='" + teamId + '\'' +
                ", inviterId=" + inviterId +
                ", targetId=" + targetId +
                ", expired=" + isExpired() +
                '}';
    }
}