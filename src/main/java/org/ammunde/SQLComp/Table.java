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
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.text.Normalizer;

public class Table
{
    private DB db_;
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

    public Table(DB db, String name)
    {
        db_ = db;
        name_ = name;
        name_lowercase_ = name.toLowerCase();
        Log.prefixNewline(true);
        Log.status("(table) get metrics for "+db.name()+" "+name);

        lookupColumns();
        lookupPrimaryKey();
        getMetrics();

        Log.status("");
        Log.prefixNewline(false);
    }

    public DB db()
    {
        return db_;
    }

    public String name()
    {
        return name_;
    }

    public String quotedName()
    {
        return db().quoteTableName(name_);
    }

    public String lcName()
    {
        return name_lowercase_;
    }

    public String schemaPrefix()
    {
        return db().schemaPrefix();
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
            exact_num_rows_ = db().performQueryInt("select count(*) from "+db().schemaPrefix()+
                                                   db().quoteTableName(name_));
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
            DatabaseMetaData meta = db().connection().getMetaData();
            String s = null;
            if (db().schema() != null && db().schema().length() > 0) s = db().schema();
            String d = null;
            if (db().dbName() != null && db().dbName().length() > 0) d = db().dbName();
            // SQL Server does not accept a catalog (aka database) name here....
            if (db().type().equals("SQLSERVER")) d = null;

            try (ResultSet cols = meta.getColumns(d, s, name_, "%"))
            {
                boolean first = true;
                while (cols.next())
                {
                    String name = cols.getString("COLUMN_NAME");
                    int type = cols.getInt("DATA_TYPE");
                    String type_name = cols.getString("TYPE_NAME");
                    column_names_.add(name);
                    Column c = new Column(this, name, type, type_name);
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
            DatabaseMetaData meta = db().connection().getMetaData();
            String s = null;
            if (db().schema() != null && db().schema().length() > 0) s = db().schema();
            String d = null;
            if (db().dbName() != null && db().dbName().length() > 0) d = db().dbName();
            // SQL Server does not accept a catalog (aka database) name here....
            if (db().type().equals("SQLSERVER")) d = null;

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
        if (db().type() == DBType.MYSQL || db().type() == DBType.MARIADB)
        {
            approx_disk_size_kb_ = db().performQueryInt(
                "SELECT round((data_length + index_length)/1024,0) AS disk "+
                "FROM information_schema.TABLES "+
                "WHERE table_schema = '"+db().dbName()+"' AND table_name = '"+name_+"'");

            approx_num_rows_ = db().performQueryInt(
                "SELECT table_rows FROM information_schema.tables "+
                "WHERE table_schema = '"+db().dbName()+"' AND table_name = '"+name_+"'");
        }
        else
        {
            approx_num_rows_ = db().performQueryInt("select count(*) from "+db().schemaPrefix()+
                                                    db().quoteTableName(name_));
            approx_disk_size_kb_ = approx_num_rows_;
        }
    }

    public String print()
    {
        StringBuilder out = new StringBuilder();
        out.append("FALTER ");
        int n = 0;
        for (String c : column_names_)
        {
            if (n > 0) out.append(",");
            out.append(c);
            n++;
        }
        out.append(" PRIMARY KEY ("+primary_key_+")");
        return out.toString();
    }

    public void printCreate()
    {
        Log.info("CREATE TABLE "+name_+" (");
        boolean first = true;
        for (Column c : columns_)
        {
            if (!first)
            {
                Log.info(", ");
            }
            else
            {
                first = false;
            }
            c.printCreate();
        }
        Log.info(", PRIMARY KEY ("+primary_key_+")");
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

    static String doubleApostrophes(String s)
    {
        if (s == null) return s;
        StringBuilder out = new StringBuilder();

        char p = 0;
        char c = 0;
        for (int i=0; i < s.length(); ++i)
        {
            p = c;
            c = s.charAt(i);

            if (c == '\'')
            {
                out.append("''");
                continue;
            }
            else if (c == '\\')
            {
                out.append("\\\\");
                continue;
            }
            else if (c > 1000)
            {
                // Ship zero width space. U+200B
                continue;
            }
            else if (c == '¨' || c == 776)
            {
                if (p == 'a') out.append('ä');
                else if (p == 'o') out.append('ö');
                else if (p == 'A') out.append('Ä');
                else if (p == 'O') out.append('Ö');
                else if (p == 'u') out.append('ü');
                else if (p == 'U') out.append('Ü');
                else out.append("?");
                continue;
            }
            else if (c == '°')
            {
                if (p == 'a') {
                    out.append('å');
                    continue;
                }
                // Allow for the use of ° as degrees....  Taklutning 2,5°
            }
            // SQL server cannot store čΩ it changes these into c and O.
            out.append(c);
        }
        s = Normalizer.normalize(s, Normalizer.Form.NFC);

        return out.toString();
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
            return "'"+doubleApostrophes(s)+"'";
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

        int n = db().performQuery(cb,
                                  "select "+primaryKey()+
                                  " from "+db().schemaPrefix()+
                                  db().quoteTableName(name_)+
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

        int n = db().performQuery(cb,
                                  "select "+primaryKey()+
                                  " from "+db().schemaPrefix()+
                                  db().quoteTableName(name_)+
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
        db().performQuery(cb,
                          "select "+quotedPrimaryKey()+","+source.columnsForSelect()+
                          " from "+db().schemaPrefix()+db().quoteTableName(name_)+
                          " where "+quotedPrimaryKey()+">="+pk.from()+" AND "+quotedPrimaryKey()+"<="+pk.to());


        return rows;
    }
}
