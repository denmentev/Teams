package org.plugin.teams.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.plugin.teams.Teams;
import org.plugin.teams.models.Team;
import org.plugin.teams.models.TeamRole;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager {
    private final Teams plugin;
    private HikariDataSource dataSource;
    private final boolean useMySQL;
    private final ExecutorService asyncExecutor;

    public DatabaseManager(Teams plugin) {
        this.plugin = plugin;
        ConfigurationSection dbConfig = plugin.getConfig().getConfigurationSection("database");
        this.useMySQL = dbConfig.getString("type", "SQLite").equalsIgnoreCase("MySQL");
        this.asyncExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("Teams-DB-Thread");
            return t;
        });
    }

    public boolean initialize() {
        try {
            if (useMySQL) {
                return initializeMySQL();
            } else {
                return initializeSQLite();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean initializeMySQL() {
        try {
            ConfigurationSection mysqlConfig = plugin.getConfig().getConfigurationSection("database.mysql");

            if (mysqlConfig == null) {
                plugin.getLogger().severe("MySQL configuration not found in config.yml!");
                return false;
            }

            String host = mysqlConfig.getString("host", "");
            String database = mysqlConfig.getString("database", "");
            String username = mysqlConfig.getString("username", "");
            String password = mysqlConfig.getString("password", "");
            boolean useSSL = mysqlConfig.getBoolean("useSSL", true);

            if (host.isEmpty() || database.isEmpty() || username.isEmpty()) {
                plugin.getLogger().severe("MySQL configuration incomplete! Please check config.yml");
                return false;
            }

            HikariConfig config = new HikariConfig();

            StringBuilder jdbcUrl = new StringBuilder("jdbc:mysql://");
            jdbcUrl.append(host).append(":").append(mysqlConfig.getInt("port", 3306));
            jdbcUrl.append("/").append(database);
            jdbcUrl.append("?");
            jdbcUrl.append("autoReconnect=true");
            jdbcUrl.append("&allowPublicKeyRetrieval=true");
            jdbcUrl.append("&useUnicode=true");
            jdbcUrl.append("&characterEncoding=UTF-8");
            jdbcUrl.append("&serverTimezone=UTC");
            jdbcUrl.append("&rewriteBatchedStatements=true");
            jdbcUrl.append("&tcpKeepAlive=true");
            jdbcUrl.append("&socketTimeout=30000");
            jdbcUrl.append("&connectTimeout=10000");

            if (useSSL) {
                jdbcUrl.append("&useSSL=true");
                jdbcUrl.append("&requireSSL=false");
                jdbcUrl.append("&verifyServerCertificate=false");
                jdbcUrl.append("&sslMode=PREFERRED");
                jdbcUrl.append("&enabledTLSProtocols=TLSv1.2,TLSv1.3");
            } else {
                jdbcUrl.append("&useSSL=false");
                jdbcUrl.append("&sslMode=DISABLED");
            }

            config.setJdbcUrl(jdbcUrl.toString());
            config.setUsername(username);
            config.setPassword(password);
            config.setPoolName("Teams-MySQL-Pool");

            // Updated pool settings
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(5000);
            config.setIdleTimeout(240000); // 4 minutes
            config.setMaxLifetime(540000); // 9 minutes
            config.setLeakDetectionThreshold(10000); // 10 seconds
            config.setConnectionTestQuery("/* ping */ SELECT 1");
            config.setValidationTimeout(3000);
            config.setConnectionInitSql("/* ping */ SELECT 1");

            // Try to set keepaliveTime if available (HikariCP 4.0.0+)
            try {
                config.setKeepaliveTime(300000); // 5 minutes
            } catch (NoSuchMethodError e) {
                // Method not available in this version of HikariCP
                plugin.getLogger().info("HikariCP keepaliveTime not available in this version");
            }

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
            config.addDataSourceProperty("tcpKeepAlive", "true");
            config.addDataSourceProperty("tcpNoDelay", "true");

            dataSource = new HikariDataSource(config);

            CompletableFuture<Boolean> testFuture = CompletableFuture.supplyAsync(() -> {
                try (Connection conn = dataSource.getConnection();
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    return rs.next();
                } catch (SQLException e) {
                    plugin.getLogger().severe("MySQL connection test failed: " + e.getMessage());
                    return false;
                }
            }, asyncExecutor);

            if (!testFuture.get(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new SQLException("Connection test failed");
            }

            plugin.getLogger().info("MySQL connection test successful!");

            CompletableFuture.runAsync(() -> {
                try {
                    createTables();
                    updateTables();
                } catch (SQLException e) {
                    plugin.getLogger().severe("Failed to create/update tables: " + e.getMessage());
                    e.printStackTrace();
                }
            }, asyncExecutor).join();

            plugin.getLogger().info("Connected to MySQL database successfully!");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to MySQL: " + e.getMessage());
            e.printStackTrace();

            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            return false;
        }
    }

    private boolean initializeSQLite() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + new File(dataFolder, "teams.db").getAbsolutePath());
            config.setMaximumPoolSize(1);
            config.setPoolName("Teams-SQLite-Pool");
            config.setConnectionTestQuery("SELECT 1");
            config.setValidationTimeout(3000);

            dataSource = new HikariDataSource(config);

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                if (!rs.next()) {
                    throw new SQLException("Connection test failed");
                }
            }

            createTables();
            updateTables();
            plugin.getLogger().info("Connected to SQLite database!");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to SQLite: " + e.getMessage());
            e.printStackTrace();

            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            return false;
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized or has been closed");
        }

        try {
            Connection conn = dataSource.getConnection();
            if (conn == null) {
                throw new SQLException("Failed to obtain connection from pool");
            }

            // Removed conn.isValid(1) check - HikariCP handles validation via connectionTestQuery
            return conn;
        } catch (SQLException e) {
            if (!e.getMessage().contains("Connection is not available")) {
                plugin.getLogger().warning("Failed to get connection: " + e.getMessage());
            }
            throw e;
        }
    }

    private void createTables() throws SQLException {
        String teamsTable;
        String membersTable;
        String playerNamesTable;

        if (useMySQL) {
            teamsTable = """
                CREATE TABLE IF NOT EXISTS teams (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(16) NOT NULL,
                    owner_id VARCHAR(36) NOT NULL,
                    created_at BIGINT NOT NULL,
                    `desc` VARCHAR(100) DEFAULT '',
                    fulldesc TEXT,
                    mid INT(3) ZEROFILL UNIQUE,
                    banner_link VARCHAR(255) DEFAULT NULL
                )
            """;

            membersTable = """
                CREATE TABLE IF NOT EXISTS team_members (
                    team_id VARCHAR(36) NOT NULL,
                    player_id VARCHAR(36) NOT NULL,
                    player_name VARCHAR(16) NOT NULL,
                    role VARCHAR(20) NOT NULL,
                    joined_at BIGINT NOT NULL,
                    PRIMARY KEY (team_id, player_id),
                    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
                )
            """;

            playerNamesTable = """
                CREATE TABLE IF NOT EXISTS player_names (
                    player_id VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(16) NOT NULL,
                    last_updated BIGINT NOT NULL
                )
            """;
        } else {
            teamsTable = """
                CREATE TABLE IF NOT EXISTS teams (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    owner_id TEXT NOT NULL,
                    created_at BIGINT NOT NULL,
                    desc TEXT DEFAULT '',
                    fulldesc TEXT DEFAULT '',
                    mid INTEGER UNIQUE CHECK(mid >= 1 AND mid <= 999),
                    banner_link TEXT DEFAULT NULL
                )
            """;

            membersTable = """
                CREATE TABLE IF NOT EXISTS team_members (
                    team_id TEXT NOT NULL,
                    player_id TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    role TEXT NOT NULL,
                    joined_at BIGINT NOT NULL,
                    PRIMARY KEY (team_id, player_id),
                    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
                )
            """;

            playerNamesTable = """
                CREATE TABLE IF NOT EXISTS player_names (
                    player_id TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL,
                    last_updated BIGINT NOT NULL
                )
            """;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(teamsTable);
            stmt.execute(membersTable);
            stmt.execute(playerNamesTable);
        }
    }

    private void updateTables() throws SQLException {
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            ResultSet oldDescCol = meta.getColumns(null, null, "teams", "description");
            boolean hasOldDescription = oldDescCol.next();
            oldDescCol.close();

            ResultSet newDescCol = meta.getColumns(null, null, "teams", "desc");
            boolean hasNewColumns = newDescCol.next();
            newDescCol.close();

            if (!hasNewColumns) {
                plugin.getLogger().info("Updating teams table structure...");

                try (Statement stmt = conn.createStatement()) {
                    if (useMySQL) {
                        stmt.execute("ALTER TABLE teams ADD COLUMN `desc` VARCHAR(100) DEFAULT ''");
                        stmt.execute("ALTER TABLE teams ADD COLUMN fulldesc TEXT");
                        stmt.execute("ALTER TABLE teams ADD COLUMN mid INT(3) ZEROFILL UNIQUE");
                    } else {
                        stmt.execute("ALTER TABLE teams ADD COLUMN desc TEXT DEFAULT ''");
                        stmt.execute("ALTER TABLE teams ADD COLUMN fulldesc TEXT DEFAULT ''");
                        stmt.execute("ALTER TABLE teams ADD COLUMN mid INTEGER UNIQUE CHECK(mid >= 1 AND mid <= 999)");
                    }

                    if (hasOldDescription) {
                        plugin.getLogger().info("Migrating data from old description column...");
                        stmt.execute("UPDATE teams SET `desc` = SUBSTRING(description, 1, 100) WHERE description IS NOT NULL");
                        stmt.execute("ALTER TABLE teams DROP COLUMN description");
                        plugin.getLogger().info("Old description column removed");
                    }

                    assignMidsToExistingTeams();
                    plugin.getLogger().info("Successfully updated teams table structure!");
                }
            } else if (hasOldDescription) {
                try (Statement stmt = conn.createStatement()) {
                    plugin.getLogger().info("Cleaning up old description column...");
                    stmt.execute("UPDATE teams SET `desc` = SUBSTRING(description, 1, 100) WHERE description IS NOT NULL AND `desc` = ''");
                    stmt.execute("ALTER TABLE teams DROP COLUMN description");
                    plugin.getLogger().info("Cleanup completed");
                }
            }

            ResultSet bannerLinkCol = meta.getColumns(null, null, "teams", "banner_link");
            boolean hasBannerLinkColumn = bannerLinkCol.next();
            bannerLinkCol.close();

            if (!hasBannerLinkColumn) {
                plugin.getLogger().info("Adding banner_link column to teams table...");
                try (Statement stmt = conn.createStatement()) {
                    if (useMySQL) {
                        stmt.execute("ALTER TABLE teams ADD COLUMN banner_link VARCHAR(255) DEFAULT NULL");
                    } else {
                        stmt.execute("ALTER TABLE teams ADD COLUMN banner_link TEXT DEFAULT NULL");
                    }
                    plugin.getLogger().info("Successfully added banner_link column to teams table!");
                }
            }

            ResultSet playerNameCol = meta.getColumns(null, null, "team_members", "player_name");
            boolean hasPlayerNameColumn = playerNameCol.next();
            playerNameCol.close();

            if (!hasPlayerNameColumn) {
                plugin.getLogger().info("Adding player_name column to team_members table...");
                try (Statement stmt = conn.createStatement()) {
                    if (useMySQL) {
                        stmt.execute("ALTER TABLE team_members ADD COLUMN player_name VARCHAR(16) NOT NULL DEFAULT 'Unknown'");
                    } else {
                        stmt.execute("ALTER TABLE team_members ADD COLUMN player_name TEXT NOT NULL DEFAULT 'Unknown'");
                    }

                    updateExistingMemberNames();
                    plugin.getLogger().info("Successfully added player_name column to team_members!");
                }
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column") &&
                    !e.getMessage().contains("already exists") &&
                    !e.getMessage().contains("no such column")) {
                throw e;
            }
        }
    }

    private void updateExistingMemberNames() throws SQLException {
        String updateSql = """
            UPDATE team_members tm
            SET player_name = COALESCE(
                (SELECT player_name FROM player_names WHERE player_id = tm.player_id),
                'Unknown'
            )
            WHERE player_name = 'Unknown'
        """;

        if (!useMySQL) {
            updateSql = """
                UPDATE team_members
                SET player_name = COALESCE(
                    (SELECT player_name FROM player_names WHERE player_id = team_members.player_id),
                    'Unknown'
                )
                WHERE player_name = 'Unknown'
            """;
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            int updated = stmt.executeUpdate(updateSql);
            plugin.getLogger().info("Updated " + updated + " team member names from cache");

            String selectSql = "SELECT player_id FROM team_members WHERE player_name = 'Unknown'";
            try (ResultSet rs = stmt.executeQuery(selectSql)) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("player_id"));
                    OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
                    if (player.getName() != null) {
                        updateMemberName(playerId, player.getName());
                    }
                }
            }
        }
    }

    private void updateMemberName(UUID playerId, String playerName) throws SQLException {
        String sql = "UPDATE team_members SET player_name = ? WHERE player_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            pstmt.setString(2, playerId.toString());
            pstmt.executeUpdate();
        }
    }

    private void assignMidsToExistingTeams() throws SQLException {
        String selectSql = "SELECT id FROM teams WHERE mid IS NULL";
        String updateSql = "UPDATE teams SET mid = ? WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

            ResultSet rs = selectStmt.executeQuery();
            while (rs.next()) {
                String teamId = rs.getString("id");
                int mid = generateUniqueMid();

                updateStmt.setInt(1, mid);
                updateStmt.setString(2, teamId);
                updateStmt.executeUpdate();

                plugin.getLogger().info("Assigned MID " + String.format("%03d", mid) + " to team " + teamId);
            }
        }
    }

    private int generateUniqueMid() throws SQLException {
        String sql = "SELECT mid FROM teams WHERE mid IS NOT NULL ORDER BY mid ASC";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            Set<Integer> usedMids = new HashSet<>();
            while (rs.next()) {
                usedMids.add(rs.getInt("mid"));
            }

            for (int i = 1; i <= 999; i++) {
                if (!usedMids.contains(i)) {
                    return i;
                }
            }

            throw new SQLException("No available MID found (all 999 slots used)");
        }
    }

    // Async version of saveTeam
    public CompletableFuture<Void> saveTeamAsync(Team team) {
        return CompletableFuture.runAsync(() -> saveTeam(team), asyncExecutor);
    }

    public void saveTeam(Team team) {
        String sql;
        if (useMySQL) {
            sql = "INSERT INTO teams (id, name, owner_id, created_at, `desc`, fulldesc, mid, banner_link) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE name = VALUES(name), `desc` = VALUES(`desc`), " +
                    "fulldesc = VALUES(fulldesc), banner_link = VALUES(banner_link)";
        } else {
            sql = "INSERT OR REPLACE INTO teams (id, name, owner_id, created_at, desc, fulldesc, mid, banner_link) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        }

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (team.getMid() == 0) {
                team.setMid(generateUniqueMid());
            }

            pstmt.setString(1, team.getId());
            pstmt.setString(2, team.getName());
            pstmt.setString(3, team.getOwnerId().toString());
            pstmt.setLong(4, team.getCreatedAt());
            pstmt.setString(5, team.getDescription());
            pstmt.setString(6, team.getFullDescription());
            pstmt.setInt(7, team.getMid());
            pstmt.setString(8, team.getBannerLink());
            pstmt.executeUpdate();

            saveTeamMembers(team);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save team: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveTeamMembers(Team team) {
        String deleteSql = "DELETE FROM team_members WHERE team_id = ?";
        String insertSql = "INSERT INTO team_members (team_id, player_id, player_name, role, joined_at) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            deleteStmt.setString(1, team.getId());
            deleteStmt.executeUpdate();

            for (Map.Entry<UUID, TeamRole> entry : team.getMembers().entrySet()) {
                UUID playerId = entry.getKey();
                // Use cached name or "Unknown" temporarily, will be updated async
                String playerName = getPlayerNameFromCache(playerId);

                insertStmt.setString(1, team.getId());
                insertStmt.setString(2, playerId.toString());
                insertStmt.setString(3, playerName);
                insertStmt.setString(4, entry.getValue().name());
                insertStmt.setLong(5, System.currentTimeMillis());
                insertStmt.addBatch();

                // Update player name asynchronously
                updatePlayerNameAsync(playerId);
            }
            insertStmt.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save team members: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Quick sync method to get cached name only (no DB access)
    private String getPlayerNameFromCache(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        if (player.getName() != null) {
            return player.getName();
        }
        return "Unknown";
    }

    public void updatePlayerNameAsync(UUID playerId) {
        CompletableFuture.runAsync(() -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
            String playerName = player.getName();

            if (playerName == null) {
                return;
            }

            String sql = "INSERT INTO player_names (player_id, player_name, last_updated) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), last_updated = VALUES(last_updated)";

            if (!useMySQL) {
                sql = "INSERT OR REPLACE INTO player_names (player_id, player_name, last_updated) VALUES (?, ?, ?)";
            }

            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());
                pstmt.setString(2, playerName);
                pstmt.setLong(3, System.currentTimeMillis());
                pstmt.executeUpdate();

                updateMemberName(playerId, playerName);
            } catch (SQLException e) {
                if (!e.getMessage().contains("Connection is not available")) {
                    plugin.getLogger().warning("Failed to update player name for " + playerId + ": " + e.getMessage());
                }
            }
        }, asyncExecutor).exceptionally(ex -> {
            if (!ex.getMessage().contains("Connection is not available")) {
                plugin.getLogger().warning("Async player name update failed: " + ex.getMessage());
            }
            return null;
        });
    }

    public void updatePlayerName(UUID playerId) {
        updatePlayerNameAsync(playerId);
    }

    // Async version of getPlayerName
    public CompletableFuture<String> getPlayerNameAsync(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> getPlayerName(playerId), asyncExecutor);
    }

    // Sync version - should be called only from async context
    public String getPlayerName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        if (player.getName() != null) {
            updatePlayerNameAsync(playerId);
            return player.getName();
        }

        String sql = "SELECT player_name FROM player_names WHERE player_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("player_name");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get player name for " + playerId + ": " + e.getMessage());
        }

        return "Unknown";
    }

    // Async version of loadTeam
    public CompletableFuture<Team> loadTeamAsync(String teamId) {
        return CompletableFuture.supplyAsync(() -> loadTeam(teamId), asyncExecutor);
    }

    public Team loadTeam(String teamId) {
        String sql = useMySQL ?
                "SELECT id, name, owner_id, created_at, `desc`, fulldesc, mid, banner_link FROM teams WHERE id = ?" :
                "SELECT id, name, owner_id, created_at, desc, fulldesc, mid, banner_link FROM teams WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, teamId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Team team = new Team(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("desc") != null ? rs.getString("desc") : "",
                        UUID.fromString(rs.getString("owner_id")),
                        rs.getLong("created_at")
                );

                team.setFullDescription(rs.getString("fulldesc") != null ? rs.getString("fulldesc") : "");
                team.setMid(rs.getInt("mid"));
                team.setBannerLink(rs.getString("banner_link"));

                loadTeamMembers(team);
                return team;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load team: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private void loadTeamMembers(Team team) {
        String sql = "SELECT * FROM team_members WHERE team_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, team.getId());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_id"));
                TeamRole role = TeamRole.valueOf(rs.getString("role"));
                team.addMember(playerId, role);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load team members: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Async version of loadAllTeams
    public CompletableFuture<List<Team>> loadAllTeamsAsync() {
        return CompletableFuture.supplyAsync(this::loadAllTeams, asyncExecutor);
    }

    public List<Team> loadAllTeams() {
        List<Team> teams = new ArrayList<>();
        String sql = "SELECT id FROM teams";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Team team = loadTeam(rs.getString("id"));
                if (team != null) {
                    teams.add(team);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load all teams: " + e.getMessage());
            e.printStackTrace();
        }
        return teams;
    }

    // Async version of deleteTeam
    public CompletableFuture<Void> deleteTeamAsync(String teamId) {
        return CompletableFuture.runAsync(() -> deleteTeam(teamId), asyncExecutor);
    }

    public void deleteTeam(String teamId) {
        String sql = "DELETE FROM teams WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, teamId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete team: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Async version of getPlayerTeamId
    public CompletableFuture<String> getPlayerTeamIdAsync(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> getPlayerTeamId(playerId), asyncExecutor);
    }

    public String getPlayerTeamId(UUID playerId) {
        String sql = "SELECT team_id FROM team_members WHERE player_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("team_id");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player team: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // Async version of getTeamByMid
    public CompletableFuture<Team> getTeamByMidAsync(int mid) {
        return CompletableFuture.supplyAsync(() -> getTeamByMid(mid), asyncExecutor);
    }

    public Team getTeamByMid(int mid) {
        String sql = "SELECT id FROM teams WHERE mid = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, mid);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return loadTeam(rs.getString("id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get team by MID: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public void closeConnection() {
        if (asyncExecutor != null && !asyncExecutor.isShutdown()) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed");
        }
    }
}