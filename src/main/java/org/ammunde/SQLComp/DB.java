/*
 sqlcomp - Copyright (C) 2025 Fredrik Öhrström (gpl-3.0-or-later)

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.ammunde.SQLComp;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.UUID;
import java.time.Instant;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import java.util.HashSet;

public class DB
{
    private String prefix_;
    private Connection connection_;
    private String name_;
    private String db_name_;
    private String db_user_;
    private String db_pwd_;
    private String db_host_;
    private String db_url_;
    private String schema_;
    private String schema_prefix_; // The schema_+"." or "" if no schema_
    private DBType type_;
    private long last_connection_check_;
    private Set<String> ignored_tables_;

    public DB(String prefix)
    {
        connect(prefix);
    }

    public void close() throws SQLException
    {
        connection_.close();
        connection_ = null;
    }

    public Connection connection()
    {
        return connection_;
    }

    public String name()
    {
        return name_;
    }

    public String dbName()
    {
        return db_name_;
    }

    public String dbUser()
    {
        return db_user_;
    }

    public String dbPwd()
    {
        return db_pwd_;
    }

    public String dbHost()
    {
        return db_host_;
    }

    public DBType dbType()
    {
        return type_;
    }

    public String schema()
    {
        return schema_;
    }

    public String schemaPrefix()
    {
        return schema_prefix_;
    }

    public void connect(String prefix)
    {
        prefix_ = prefix;
        if (!reconnect())
        {
            Log.error("(db) failed second reconnect attempt to "+name_+"\n");
            System.exit(1);
        }
    }

    public synchronized boolean reconnect()
    {
        if (connection_ != null)
        {
            try
            {
                connection_.close();
            }
            catch (Exception e)
            {
                e.printStackTrace();
                Log.warning("(db) failed to close "+name_+"\n");
            }
            connection_ = null;
        }
        name_ = System.getenv(prefix_+"_NAME"); // "MySourceDB"
        db_url_ = System.getenv(prefix_+"_DB_URL"); // "jdbc:postgresql://localhost/fromcomp";
        db_name_ = System.getenv(prefix_+"_DB_NAME"); // fromcomp
        db_user_ = System.getenv(prefix_+"_DB_USER"); // testuser
        db_pwd_ = System.getenv(prefix_+"_DB_PWD"); // asecret
        db_host_ = Util.jdbcHost(db_url_);
        schema_ = System.getenv(prefix_+"_SCHEMA"); // A schema is an internal grouping of tables inside a database.
        // Store
        String ignores = System.getenv(prefix_+"_IGNORED_TABLES");
        ignored_tables_ = new HashSet<>();
        if (ignores != null && !ignores.trim().equals(""))
        {
            String[] is = ignores.split(",");
            for (String s : is)
            {
                ignored_tables_.add(s);
            }
        }

        if (schema_ != null && schema_.length() > 0) schema_prefix_ = schema_+".";
        else schema_prefix_ = "";

        if (db_url_.startsWith("jdbc:postgresql")) type_ = DBType.POSTGRES;
        if (db_url_.startsWith("jdbc:mysql")) type_ = DBType.MYSQL;
        if (db_url_.startsWith("jdbc:mariadb")) type_ = DBType.MARIADB;
        if (db_url_.startsWith("jdbc:sqlserver")) type_ = DBType.SQLSERVER;

        String sc = schema_;
        if (sc.length() > 0) sc += ".";

        Log.verbose("(db) connecting "+name_+"("+sc+db_name_+":"+type_+") "+db_user_+" "+db_host_+" "+db_url_+"\n");

        try
        {
            connection_ = DriverManager.getConnection(db_url_, db_user_, db_pwd_);
            last_connection_check_ = System.currentTimeMillis();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Log.error("(db) failed to connect to "+db_url_+"\n");
            last_connection_check_ = 0;
            return false;
        }

        if (type_ == DBType.MYSQL || type_ == DBType.MARIADB)
        {
            String session = performQueryString("SELECT @@session.sql_mode");
            if (session.indexOf("ANSI_QUOTES") == -1)
            {
                session = "ANSI_QUOTES,"+session;
                performUpdate("SET SESSION SQL_MODE='"+session+"'");
            }
            Log.verbose("(db) "+name_+" session "+session+"\n");

            // Disable the foreign key checks...not a good solution but necessary
            // for now to be able to update all tables in the wrong order.
            performUpdate("SET FOREIGN_KEY_CHECKS = 0");
            Log.verbose("(db) "+name_+" session SET FOREIGN_KEY_CHECKS = 0\n");
        }

        if (type_ == DBType.SQLSERVER)
        {
            performUpdate("SET QUOTED_IDENTIFIER ON");
        }

        return true;
    }

    public synchronized PreparedStatement prepare(String query, Object[] args)
    {
        try
        {
            PreparedStatement stmnt = connection_.prepareStatement(query);
            {
                int i = 1;
                for (Object o : args)
                {
                    if (o == null)
                    {
                        stmnt.setNull(i, java.sql.Types.NULL);
                    }
                    else if (o.getClass() == Integer.class)
                    {
                        stmnt.setInt(i, ((Integer)o));
                    }
                    else if (o.getClass() == Short.class)
                    {
                        stmnt.setInt(i, ((Short)o));
                    }
                    else if (o.getClass() == String.class)
                    {
                        stmnt.setString(i, (String)o);
                    }
                    else if (o.getClass() == Boolean.class)
                    {
                        stmnt.setBoolean(i, ((Boolean)o));
                    }
                    else if (o.getClass() == UUID.class)
                    {
                        stmnt.setObject(i, (UUID)o);
                    }
                    else if (o.getClass() == Instant.class)
                    {
                        stmnt.setTimestamp(i, java.sql.Timestamp.from((Instant)o));
                    }
                    else {
                        Log.error("Our class DB cannot handle object class "+o.getClass().toString());
                    }
                    i++;
                }
            }
            return stmnt;
        }
        catch (Exception e)
        {
            Log.error(""+e+"\n"+query);
        }
        return null;
    }

    public synchronized void verifyConnection()
    {
        // If a successful check was done less than 60 seconds ago, skip this.
        if (last_connection_check_ + 60*1000 < System.currentTimeMillis()) return;

        try
        {
            if (connection_.isValid(0))
            {
                last_connection_check_ = System.currentTimeMillis();
            }
            else
            {
                Log.warning("(db) fail connection check to "+name_+"\n");
                // Try to open again. One test only, give up if it fails.
                if (!reconnect())
                {
                    Log.error("(db) failed second reconnect attempt to "+name_+" giving up.\n");
                    System.exit(1);
                }
            }
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            Log.warning("(db) fail connection check to "+name_+"\n");
            // Try to open again. One test only, give up if it fails.
            if (!reconnect())
            {
                Log.error("(db) failed second reconnect attempt to "+name_+" giving up.\n");
                System.exit(1);
            }
        }
    }

    public synchronized int performQuery(ResultCallback cb, String query, Object... args)
    {
        int n = 0;

        verifyConnection();

        try (PreparedStatement stmnt = prepare(query, args))
        {
            ResultSet rs = stmnt.executeQuery();
            while (rs.next())
            {
                cb.handle(rs, n);
                n++;
            }
        }
        catch(Exception e)
        {
            Log.error("ERROR "+e+"\n"+query+"\n\n");
        }

        return n;
    }

    // The select must never return -1, since that means no data found.
    public synchronized int performQueryInt(String query, Object... args)
    {
        verifyConnection();

        try (PreparedStatement stmnt = prepare(query, args))
        {
            ResultSet rs = stmnt.executeQuery();
            if (rs.next())
            {
                return rs.getInt(1);
            }
        }
        catch(Exception e)
        {
            Log.error("ERROR "+e+"\n"+query+"\n\n");
        }

        return -1;
    }

    public synchronized String performQueryString(String query, Object... args)
    {
        verifyConnection();

        try (PreparedStatement stmnt = prepare(query, args))
        {
            ResultSet rs = stmnt.executeQuery();
            if (rs.next())
            {
                return rs.getString(1);
            }
        }
        catch(Exception e)
        {
            Log.error("ERROR "+e+"\n"+query+"\n\n");
        }

        return null;
    }

    public synchronized List<String> performQueryStrings(String query, Object... args)
    {
        verifyConnection();

        List<String> l = new LinkedList<String>();

        try (PreparedStatement stmnt = prepare(query, args))
        {
            ResultSet rs = stmnt.executeQuery();
            while (rs.next())
            {
                l.add(rs.getString(1));
            }
        }
        catch(Exception e)
        {
            Log.error("ERROR "+e+"\n"+query+"\n\n");
        }

        return l;
    }

    public synchronized List<Integer> performQueryInts(String query, Object... args)
    {
        verifyConnection();

        List<Integer> l = new ArrayList<>();

        try (PreparedStatement stmnt = prepare(query, args))
        {
            ResultSet rs = stmnt.executeQuery();
            while (rs.next())
            {
                l.add(rs.getInt(1));
            }
        }
        catch(Exception e)
        {
            Log.error("ERROR "+e+"\n"+query+"\n\n");
        }

        return l;
    }

    public synchronized int performUpdate(String query, List<Object> args)
    {
        Object[] argss = args.toArray(new Object[0]);
        return performUpdate(query, argss);
    }

    public synchronized int performUpdate(String query, Object... args)
    {
        int n = 0;

        verifyConnection();

        try (PreparedStatement stmnt = prepare(query, args))
        {
            stmnt.executeUpdate();
        }
        catch(Exception e)
        {
            Log.error("ERROR "+e+"\n"+query+"\n\n");
        }

        return n;
    }

    public synchronized int performSyncUpdate(Table tt, String query, Object... args)
    {
        int n = 0;

        verifyConnection();

        try (PreparedStatement stmnt = prepare(query, args))
        {
            stmnt.executeUpdate();
        }
        catch(Exception e)
        {
            Log.syncError("ERROR "+e+"\n"+query+"\n\n");
            tt.incFailures();
        }

        return n;
    }

    public String quoteTableName(String t)
    {
        if (type_ == DBType.SQLSERVER) return "["+t+"]";
        if (type_ == DBType.MYSQL || type_ == DBType.MARIADB) return "`"+t+"`";
        if (type_ == DBType.POSTGRES) return "\""+t+"\"";
        return t;
    }

    public boolean ignored(String table)
    {
        return ignored_tables_.contains(table);
    }

    public synchronized void keepalive()
    {
        int v = 0;
        boolean retry = false;
        int retries = 0;

        do {
            Log.debug("(db) keepalive "+name_+"\n");
            try
            {
                retry = false;
                v = performQueryInt("select 1+2+3;");
                if (v != 6)
                {
                    throw new Exception("Expected 6 but got "+v);
                }
            }
            catch (Exception e)
            {
                if (retries > 5)
                {
                    Log.error("(db) five keepalive reconnects attempts to "+name_+" failed! Giving up!\n");
                    System.exit(1);
                }
                e.printStackTrace();
                retries++;
                retry = true;
                reconnect();
            }
        } while (retry);
    }

}
