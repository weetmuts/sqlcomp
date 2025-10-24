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
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

public class Table
{
    private Database database_;
    private String name_;
    private String name_lowercase_;
    private List<Column> columns_;
    private List<String> column_names_;
    private Column[] index_to_column_;
    private Map<String,Column> name_to_column_;
    private String columns_for_select_;
    private String primary_key_; // id or name,age
    private long exact_num_rows_ = -1;
    private long approx_num_rows_;
    private long approx_disk_size_kb_;
    private boolean has_integer_primary_key_;
    private Status status_;

    public Table(Database database, String name)
    {
        database_ = database;
        name_ = name;
        name_lowercase_ = name.toLowerCase();
        Log.prefixNewline(true);
        Log.status("(table) get metrics for "+database().db().name()+" "+name);

        lookupColumns();
        lookupPrimaryKey();
        getMetrics();

        Log.status("");
        Log.prefixNewline(false);
    }

    public void track(Status s)
    {
        status_ = s;
    }

    public Database database()
    {
        return database_;
    }

    public void incInserts()
    {
        if (status_ != null) status_.incInserts(this);
    }

    public void incUpdates()
    {
        if (status_ != null) status_.incUpdates(this);
    }

    public void incDeletes()
    {
        if (status_ != null) status_.incDeletes(this);
    }

    public void incFailures()
    {
        if (status_ != null) status_.incFailures(this);
    }

    public String name()
    {
        return name_;
    }

    public String quotedName()
    {
        return database().db().quoteTableName(name_);
    }

    public String addColumn()
    {
        switch (database().db().dbType())
        {
        case MYSQL:
        case MARIADB:
        case POSTGRES:
            return "ADD COLUMN";
        case SQLSERVER:
            return "ADD";
        }
        return "ADD COLUMN";
    }

    public String lcName()
    {
        return name_lowercase_;
    }

    public String schemaPrefix()
    {
        return database().db().schemaPrefix();
    }

    public List<Column> columns()
    {
        return columns_;
    }

    public List<String> columnNames()
    {
        return column_names_;
    }

    public String columnsForSelect()
    {
        return columns_for_select_;
    }

    public Column column(String name)
    {
        return name_to_column_.get(name);
    }

    public Column column(int i)
    {
        return index_to_column_[i];
    }

    public String primaryKey()
    {
        return primary_key_;
    }

    public String quotedPrimaryKey()
    {
        return "\""+primary_key_+"\"";
    }

    public boolean hasIntegerPrimaryKey()
    {
        return has_integer_primary_key_;
    }

    public long exactNumRows()
    {
        if (exact_num_rows_ < 0)
        {
            exact_num_rows_ = database().db().performQueryInt("select count(*) from "+database().db().schemaPrefix()+
                                                   database().db().quoteTableName(name_));
        }

        return exact_num_rows_;
    }

    public long approxNumRows()
    {
        return approx_num_rows_;
    }

    public long approxDiskSizeKB()
    {
        return approx_disk_size_kb_;
    }

    public static int compareName(Table a, Table b)
    {
        return a.name().compareTo(b.name());
    }

    public static int compareDiskSize(Table a, Table b)
    {
        // Test hack, replace with proper estimation of table size instead of rows.
        if (a.approxDiskSizeKB() == b.approxDiskSizeKB()) return 0;
        if (a.approxDiskSizeKB() > b.approxDiskSizeKB()) return 1;
        return -1;
    }

    void lookupColumns()
    {
        try
        {
            columns_ = new LinkedList<>();
            column_names_ = new LinkedList<>();
            columns_for_select_ = "";
            name_to_column_ = new HashMap<>();
            DatabaseMetaData meta = database().db().connection().getMetaData();
            String s = null;
            if (database().db().schema() != null && database().db().schema().length() > 0) s = database().db().schema();
            // SQL Server should use dbo if no schema is supplied.
            if (s == null && database().db().dbType() == DBType.SQLSERVER) s = "dbo";

            String d = null;
            if (database().db().dbName() != null && database().db().dbName().length() > 0) d = database().db().dbName();
            // SQL Server does not accept a catalog (aka database) name here....
            if (database().db().dbType() == DBType.SQLSERVER) d = null;


            try (ResultSet cols = meta.getColumns(d, s, name_, "%"))
            {
                boolean first = true;
                while (cols.next())
                {
                    String name = cols.getString("COLUMN_NAME");
                    int type = cols.getInt("DATA_TYPE");
                    String type_name = cols.getString("TYPE_NAME");
                    int column_size = cols.getInt("COLUMN_SIZE");
                    column_names_.add(name);
                    boolean not_null = cols.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls;
                    String default_value = cols.getString("COLUMN_DEF");
                    default_value = Translate.defaultValue(default_value, database().db().dbType());
                    Column c = new Column(this, name, type, type_name, not_null, default_value, column_size);
                    columns_.add(c);
                    name_to_column_.put(name, c);
                    if (!first) columns_for_select_ += ",";
                    columns_for_select_ += '"'+name+'"';
                    first = false;
                }
            }

            index_to_column_ = new Column[columns_.size()];
            int i = 0;
            for (Column c : columns_)
            {
                index_to_column_[i] = c;
                i++;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    void lookupPrimaryKey()
    {
        primary_key_ = null;
        try
        {
            DatabaseMetaData meta = database().db().connection().getMetaData();
            String s = null;
            if (database().db().schema() != null && database().db().schema().length() > 0) s = database().db().schema();
            String d = null;
            if (database().db().dbName() != null && database().db().dbName().length() > 0) d = database().db().dbName();
            // SQL Server does not accept a catalog (aka database) name here....
            if (database().db().dbType() == DBType.SQLSERVER) d = null;

            try (ResultSet tables = meta.getTables(d, s, name_, new String[] { "TABLE" }))
            {
                while (tables.next())
                {
                    String catalog = tables.getString("TABLE_CAT");
                    String schema = tables.getString("TABLE_SCHEM");
                    String name = tables.getString("TABLE_NAME");

                    int npks = 0;
                    primary_key_ = "";
                    try (ResultSet pkeys = meta.getPrimaryKeys(catalog, schema, name))
                    {
                        while (pkeys.next())
                        {
                            String pk = pkeys.getString("COLUMN_NAME");
                            if (npks > 0) primary_key_ += ",";
                            npks++;
                            primary_key_ += pk;
                            Column c = column(pk);
                            if (c != null)
                            {
                                int type = c.type();
                                has_integer_primary_key_ = npks == 1 &&
                                    (type == Types.INTEGER ||
                                     type == Types.SMALLINT ||
                                     type == Types.TINYINT ||
                                     type == Types.BIGINT);
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        if (primary_key_ == null)
        {
            Log.error("Table "+name_+" must have a primary key. SLQComp cannot handle tables without a primary key. Sorry.\n");
            System.exit(1);
        }
    }

    public void getMetrics()
    {
        if (database().db().dbType() == DBType.MYSQL || database().db().dbType() == DBType.MARIADB)
        {
            approx_disk_size_kb_ = database().db().performQueryInt(
                "SELECT round((data_length + index_length)/1024,0) AS disk "+
                "FROM information_schema.TABLES "+
                "WHERE table_schema = '"+database().db().dbName()+"' AND table_name = '"+name_+"'");

            approx_num_rows_ = database().db().performQueryInt(
                "SELECT table_rows FROM information_schema.tables "+
                "WHERE table_schema = '"+database().db().dbName()+"' AND table_name = '"+name_+"'");
        }
        else
        {
            approx_num_rows_ = database().db().performQueryInt("select count(*) from "+database().db().schemaPrefix()+
                                                    database().db().quoteTableName(name_));
            approx_disk_size_kb_ = approx_num_rows_;
        }
    }

    public void printCreate(StringBuilder out, Database to)
    {
        // No quote here.... we create the table name without quotes so
        // that a sequelize table SalesForecast table can be referenced as
        // select * from SalesForecast;
        // instead of select * from "SalesForecase".
        out.append("CREATE TABLE "+to.db().quoteTableName(name())+" (");

        boolean first = true;
        for (Column c : columns_)
        {
            if (!first) out.append(", ");
            first = false;
            c.printDefinition(out, to);
        }
        out.append(");\n");
    }

    public void printAlter(StringBuilder out, Table from)
    {
        // Find added columns.
        out.append("ALTER TABLE "+name_+" ");
        Log.info(");\n");
    }

    static boolean isText(int t)
    {
        if (t == java.sql.Types.DATE ||
            t == java.sql.Types.LONGNVARCHAR ||
            t == java.sql.Types.LONGVARCHAR ||
            t == java.sql.Types.NCHAR ||
            t == java.sql.Types.NVARCHAR ||
            t == java.sql.Types.VARCHAR ||
            t == java.sql.Types.CHAR ||
            t == java.sql.Types.OTHER)
        {
            return true;
        }
        return false;
    }

    static boolean useColSize(int t)
    {
        if (t == java.sql.Types.LONGNVARCHAR ||
            t == java.sql.Types.LONGVARCHAR ||
            t == java.sql.Types.NCHAR ||
            t == java.sql.Types.NVARCHAR ||
            t == java.sql.Types.VARCHAR)
        {
            return true;
        }
        return false;
    }

    static boolean isDateTime(int t)
    {
        if (t == java.sql.Types.DATE ||
            t == java.sql.Types.TIME ||
            t == java.sql.Types.TIMESTAMP)
        {
            return true;
        }
        return false;
    }

    static boolean isInt(int t)
    {
        if (t == java.sql.Types.INTEGER ||
            t == java.sql.Types.BOOLEAN ||
            t == java.sql.Types.BIGINT ||
            t == java.sql.Types.SMALLINT ||
            t == java.sql.Types.TINYINT)
        {
            return true;
        }
        return false;
    }

    static boolean isReal(int t)
    {
        if (t == java.sql.Types.REAL ||
            t == java.sql.Types.FLOAT ||
            t == java.sql.Types.DOUBLE ||
            t == java.sql.Types.NUMERIC ||
            t == java.sql.Types.DECIMAL)
        {
            return true;
        }
        return false;
    }

    static String trimReal(String s)
    {
        if (s.endsWith(".0")) s = s.substring(0, s.length()-2);
        return s;
    }

    static String fixDateTime(String s)
    {
        // 2024-05-21 06:20:21
        if (s != null && s.length() == 19 &&
            s.charAt(4) == '-' &&
            s.charAt(7) == '-' &&
            s.charAt(10) == ' ' &&
            s.charAt(13) == ':' &&
            s.charAt(16) == ':')
        {
            String f = s.substring(0, 10)+"T"+s.substring(11);
            return f;
        }
        return s;
    }

    static String wrap(String s, int t)
    {
        if (isText(t))
        {
            if (s == null) return null;
            return "'"+Util.doubleApostrophes(s)+"'";
        }
        if (isDateTime(t))
        {
            if (s == null) return null;
            return "CAST('"+fixDateTime(s)+"' AS DATETIME)";
        }
        if (isReal(t))
        {
            if (s == null) return null;
            return trimReal(s);
        }
        if (isInt(t))
        {
            if (s == null) return null;
            return s;
        }

        Log.error("Unsupported SQL type "+t+"\n");
        System.exit(1);

        return null;
    }

    List<PK> chunkPrimaryKeys(int size)
    {
        int[] pk_from = new int[1];
        pk_from[0] = -1;
        int[] pk_to = new int[1];
        List<Integer> keys = new ArrayList<>();

        List<PK> pks = new LinkedList<>();
        ResultCallback cb = (rs, rownum) -> {
            if (rownum > 0 && rownum % size == 0)
            {
                pks.add(new PK(pk_from[0], pk_to[0], keys.stream().mapToInt(Integer::intValue).toArray()));
                pk_from[0] = -1;
                keys.clear();
            }
            pk_to[0] = rs.getInt(1);
            keys.add(pk_to[0]);
            if (pk_from[0] == -1) pk_from[0] = pk_to[0];
        };

        int n = database().db().performQuery(cb,
                                  "select "+primaryKey()+
                                  " from "+database().db().schemaPrefix()+
                                  database().db().quoteTableName(name_)+
                                  " order by "+primaryKey()+" asc");

        if (pk_from[0] != -1) {
            pks.add(new PK(pk_from[0], pk_to[0], keys.stream().mapToInt(Integer::intValue).toArray()));
        }

        return pks;
    }

    List<PK> chunkPrimaryKeysInto(List<PK> ipks)
    {
        Iterator<PK> pki = (ipks.iterator());
        PK[] pk = new PK[1];
        pk[0] = pki.next();

        int[] pk_from = new int[1];
        pk_from[0] = -1;

        int[] pk_to = new int[1];
        pk_to[0] = -1;

        List<Integer> keys = new ArrayList<>();

        List<PK> pks = new LinkedList<>();
        ResultCallback cb = (rs, rownum) -> {
            int p = rs.getInt(1);
            if (rownum > 0)
            {
                pks.add(new PK(pk_from[0], pk_to[0], keys.stream().mapToInt(Integer::intValue).toArray()));
                pk_from[0] = -1;
                keys.clear();
            }
            pk_to[0] = p;
            keys.add(pk_to[0]);
            if (pk_from[0] == -1) pk_from[0] = pk_to[0];
        };

        int n = database().db().performQuery(cb,
                                  "select "+primaryKey()+
                                  " from "+database().db().schemaPrefix()+
                                  database().db().quoteTableName(name_)+
                                  " order by "+primaryKey()+" asc");

        if (pk_from[0] != -1) {
            pks.add(new PK(pk_from[0], pk_to[0], keys.stream().mapToInt(Integer::intValue).toArray()));
        }

        return pks;
    }

    ArrayList<Row> rows(PK pk, Table source)
    {
        ArrayList<Row> rows = new ArrayList<>();

        int num_cols = source.columns().size();

        ResultCallback cb = (rs, rownum) -> {
            LinkedList<String> cols = new LinkedList<>();
            for (int i = 2; i <= num_cols+1; i++)
            {
                Column c = source.column(i-2);
                cols.add(wrap(rs.getString(i), c.type()));
            }
            rows.add(new Row(rs.getInt(1), cols));
        };

        // Yes, the primary key will be duplicated in the select,
        // because the primary key is also inside the columnsForSelect.
        // But we want the primary key to be the first content column.
        database().db().performQuery(cb,
                          "select "+quotedPrimaryKey()+","+source.columnsForSelect()+
                          " from "+database().db().schemaPrefix()+database().db().quoteTableName(name_)+
                          " where "+quotedPrimaryKey()+">="+pk.from()+" AND "+quotedPrimaryKey()+"<="+pk.to());


        return rows;
    }
}
