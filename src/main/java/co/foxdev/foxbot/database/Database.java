/*
 * This file is part of Foxbot.
 *
 *     Foxbot is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Foxbot is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Foxbot. If not, see <http://www.gnu.org/licenses/>.
 */

package co.foxdev.foxbot.database;

import co.foxdev.foxbot.FoxBot;
import co.foxdev.foxbot.utils.Utils;
import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;
import org.pircbotx.Channel;
import org.pircbotx.User;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Database
{
	private final FoxBot foxbot;

	private Connection connection = null;
	private BoneCP connectionPool = null;

	public Database(FoxBot foxbot)
	{
		this.foxbot = foxbot;
	}

	public void connect()
	{
		Statement statement;
		String databaseType = foxbot.getConfig().getDatabaseType();
		String url = databaseType.equalsIgnoreCase("mysql") ? String.format("jdbc:mysql://%s:%s/%s", foxbot.getConfig().getDatabaseHost(), foxbot.getConfig().getDatabasePort(), foxbot.getConfig().getDatabaseName()) : "jdbc:sqlite:data/bot.db";

		try
		{
			if (databaseType.equalsIgnoreCase("sqlite"))
			{
				Class.forName("org.sqlite.JDBC");
			}

			BoneCPConfig config = new BoneCPConfig();
			String user = foxbot.getConfig().getDatabaseUser();
			String password = foxbot.getConfig().getDatabasePassword();

			config.setJdbcUrl(url);
			config.setUsername(user);
			config.setPassword(password);
			config.setMinConnectionsPerPartition(5);
			config.setMaxConnectionsPerPartition(10);
			config.setPartitionCount(3);

			connectionPool = new BoneCP(config);
			connection = connectionPool.getConnection();
			statement = connection.createStatement();

			statement.executeUpdate("CREATE TABLE IF NOT EXISTS tells (tell_time VARCHAR(32), sender VARCHAR(32), receiver VARCHAR(32), message VARCHAR(1024), used TINYINT)");
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS bans (channel VARCHAR(64), target VARCHAR(32), hostmask VARCHAR(64), reason VARCHAR(1024), banner VARCHAR(32), ban_time BIGINT)");
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS kicks (channel VARCHAR(64), target VARCHAR(32), hostmask VARCHAR(64), reason VARCHAR(1024), kicker VARCHAR(32), kick_time BIGINT)");
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS mutes (channel VARCHAR(64), target VARCHAR(32), hostmask VARCHAR(64), reason VARCHAR(1024), muter VARCHAR(32), mute_time BIGINT)");
			connection.close();
		}
		catch (SQLException ex)
		{
			foxbot.log(ex);
			foxbot.shutdown(true);
		}
		catch (ClassNotFoundException ex)
		{
			foxbot.error("SQLite driver not found!");
			foxbot.shutdown(true);
		}
	}

	public void addTell(String sender, String receiver, String message)
	{
		PreparedStatement statement;

		try
		{
			connection = connectionPool.getConnection();
			statement = connection.prepareStatement("INSERT INTO tells (tell_time, sender, receiver, message, used) VALUES (?, ?, ?, ?, 0);");

			statement.setString(1, new SimpleDateFormat("[yyyy-MM-dd - HH:mm:ss]").format(Calendar.getInstance().getTimeInMillis()));
			statement.setString(2, sender);
			statement.setString(3, receiver);
			statement.setString(4, message);
			statement.executeUpdate();
			connection.close();
		}
		catch (SQLException ex)
		{
			foxbot.log(ex);
		}
	}

	public List<String> getTells(String user, Boolean showAll)
	{
		List<String> tells = new ArrayList<>();
		PreparedStatement statement;
		ResultSet rs;

		try
		{
			connection = connectionPool.getConnection();
			statement = connection.prepareStatement(showAll ? "SELECT * FROM tells WHERE receiver = ?" : "SELECT * FROM tells WHERE receiver = ? AND used = 0");

			statement.setString(1, user);

			rs = statement.executeQuery();

			while (rs.next())
			{
				tells.add(Utils.colourise(String.format("%s &2Message from: &r%s &2Message: &r%s", rs.getString("tell_time"), rs.getString("sender"), rs.getString("message"))));
			}

			statement = connection.prepareStatement("UPDATE tells SET used = 1 WHERE receiver = ? AND used = 0");

			statement.setString(1, user);
			statement.executeUpdate();
			connection.close();
		}
		catch (SQLException ex)
		{
			foxbot.log(ex);
		}
		return tells;
	}

	public void cleanTells(String user)
	{
		PreparedStatement statement;

		try
		{
			connection = connectionPool.getConnection();
			statement = connection.prepareStatement("DELETE FROM tells WHERE receiver = ? AND used = 1");

			statement.setString(1, user);
			statement.executeUpdate();
			connection.close();
		}
		catch (SQLException ex)
		{
			foxbot.log(ex);
		}
	}

	public void addBan(Channel channel, User target, String reason, User banner, long time)
	{
		PreparedStatement statement;

		try
		{
			connection = connectionPool.getConnection();
			statement = connection.prepareStatement("INSERT INTO bans (channel, target, hostmask, reason, banner, ban_time) VALUES (?, ?, ?, ?, ?, ?);");

			statement.setString(1, channel.getName());
			statement.setString(2, target.getNick());
			statement.setString(3, target.getHostmask());
			statement.setString(4, reason);
			statement.setString(5, banner.getNick());
			statement.setLong(6, time);
			statement.executeUpdate();
			connection.close();
		}
		catch (SQLException ex)
		{
			foxbot.log(ex);
		}
	}

	public void addKick(Channel channel, User target, String reason, User kicker, long time)
	{
		PreparedStatement statement;

		try
		{
			connection = connectionPool.getConnection();
			statement = connection.prepareStatement("INSERT INTO kicks (channel, target, hostmask, reason, kicker, kick_time) VALUES (?, ?, ?, ?, ?, ?);");

			statement.setString(1, channel.getName());
			statement.setString(2, target.getNick());
			statement.setString(3, target.getHostmask());
			statement.setString(4, reason);
			statement.setString(5, kicker.getNick());
			statement.setLong(6, time);
			statement.executeUpdate();
			connection.close();
		}
		catch (SQLException ex)
		{
			foxbot.log(ex);
		}
	}

	public void disconnect()
	{
		connectionPool.shutdown();

		if (connection != null)
		{
			try
			{
				connection.close();
			}
			catch (SQLException e)
			{
				e.printStackTrace();
			}
			return;
		}
		foxbot.log("Database is already disconnected!");
	}
}