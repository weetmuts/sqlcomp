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

package org.ammunde.sqlcomp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import org.libxmq.Query;

public class Database
{
    private DB db_;
    private List<Table> tables_;
    private List<String> table_names_;
    private Map<String,Table> name_to_table_;
    private int max_table_name_length_;
    private Status status_;

    public Database(Query config, String table_pattern)
    {
        db_ = new DB(config);
        loadTables(table_pattern);
    }

    public void track(Status s)
    {
        status_ = s;
        for (Table t : tables_) t.track(s);
    }

    public void monitor(Table table, String status)
    {
        if (status_ != null)
        {
            status_.monitor(table, status);
        }
    }

    public void writeStatus()
    {
        if (status_ != null) status_.writeStatus();
    }

    public List<Table> tables()
    {
        return tables_;
    }

    public List<String> tableNames()
    {
        return table_names_;
    }

    public Table table(String name)
    {
        return name_to_table_.get(name.toLowerCase());
    }

    public DB db()
    {
        return db_;
    }

    public String name()
    {
        return db_.name();
    }

    public int maxTableNameLength()
    {
        return max_table_name_length_;
    }

    public void loadTables(String table_pattern)
    {
        try
        {
            tables_= new LinkedList<>();
            table_names_ = new LinkedList<>();
            name_to_table_ = new HashMap<>();

            DatabaseMetaData meta = db().connection().getMetaData();
            String s = null;
            if (db().dbSchema() != null && db().dbSchema().length() > 0) s = db().dbSchema();
            // SQL Server should use dbo if no schema is supplied.
            if (s == null && db().dbType() == DBType.SQLSERVER) s = "dbo";

            String d = null;
            if (db().dbName() != null && db().dbName().length() > 0) d = db().dbName();
            // SQL Server does not accept a catalog (aka database) name here....
            if (db().dbType() == DBType.SQLSERVER) d = null;

            Log.verbose("(database) reading meta data for catalog="+d+" schema="+s+"\n");

            try (ResultSet tables = meta.getTables(d, s, "%", new String[] { "TABLE" }))
            {
                while (tables.next())
                {
                    String name = tables.getString("TABLE_NAME");
                    String catalog = tables.getString("TABLE_CAT");
                    String schema = tables.getString("TABLE_SCHEM");
                    Log.verbose("(database) reading table "+name+" catalog="+catalog+" schema="+schema+"\n");
                    // Sequelize used to store the Table with real hardcoded
                    // uppercase characters. Very annoying. Match agains lower case here.
                    String lower_name = name.toLowerCase();
                    if (table_pattern == null || table_pattern.length() == 0 || table_pattern.equals(lower_name))
                    {
                        if (!db().ignored(lower_name))
                        {
                            // Store the exact table name here.
                            table_names_.add(name);
                            if (name.length() > max_table_name_length_) max_table_name_length_ = name.length();
                        }
                    }
                }
            }

            Collections.sort(table_names_);

            for (String n : table_names_)
            {
                Table t = new Table(this, n);
                if (status_ != null) t.track(status_);
                tables_.add(t);
                name_to_table_.put(n.toLowerCase(), t);
            }
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }

        Log.status("");
    }
}
