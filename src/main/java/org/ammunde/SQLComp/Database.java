/* sqlcomp - Copyright (C) 2025 Fredrik Öhrström (spdx: MIT)

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

*/

package org.ammunde.SQLComp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

public class Database
{
    private DB db_;
    private List<Table> tables_;
    private List<String> table_names_;
    private Map<String,Table> name_to_table_;
    private int max_table_name_length_;
    private Status status_;

    public Database(String prefix, String table_pattern)
    {
        db_ = new DB(prefix);
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
            if (db().schema() != null && db().schema().length() > 0) s = db().schema();
            String d = null;
            if (db().dbName() != null && db().dbName().length() > 0) d = db().dbName();
            // SQL Server does not accept a catalog (aka database) name here....
            if (db().type().equals("SQLSERVER")) d = null;

            try (ResultSet tables = meta.getTables(d, s, "%", new String[] { "TABLE" }))
            {
                while (tables.next())
                {
                    String name = tables.getString("TABLE_NAME");
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
                Table t = new Table(db(), n);
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
