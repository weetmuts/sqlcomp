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

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Collections;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main
{
    public static void main(String[] args) throws Exception
    {
        boolean ok = true;

        if (args.length == 0)
        {
            printHelp();
            return;
        }

        if (args[0].equals("show-source-tables"))
        {
            Database source = useEnvWithPrefix("SQLCOMP_SOURCE", "");
            ArrayList<Table> tables = new ArrayList<>(source.tables());
            Collections.sort(tables, Table::compareRows);

            for (Table t : tables)
            {
                System.out.println(t.name()+" "+t.numRows()+" ("+t.primaryKey()+")");
            }
            return;
        }

        if (args[0].equals("show-sink-tables"))
        {
            Database sink = useEnvWithPrefix("SQLCOMP_SINK", "");
            ArrayList<Table> tables = new ArrayList<>(sink.tables());
            Collections.sort(tables, Table::compareRows);

            for (Table t : tables)
            {
                System.out.println(t.name()+" "+t.numRows()+" ("+t.primaryKey()+")");
            }
            return;
        }

        if (args[0].equals("stream-data"))
        {
            String table_pattern = "";
            if (args.length > 1 && args[1] != null) table_pattern = args[1].trim();

            Database source = useEnvWithPrefix("SQLCOMP_SOURCE", "");
            Database sink   = useEnvWithPrefix("SQLCOMP_SINK", "");
            StreamData stream = new StreamData(source, sink);

            Thread thread = new Thread(stream::go);
            thread.start();

            sync(table_pattern, false);

            // Stream forever.
            while (true)
            {
                try { Thread.sleep(10000); } catch (InterruptedException e) { }
                LocalTime now = LocalTime.now();
                if (now.getHour() == 3 && now.getMinute() == 0)
                {
                    sync(table_pattern, false);
                }
                // Do not try to sync again during this minutes. Only relevant
                // if you have a really small database.
                while (now.getHour() == 3 && now.getMinute() == 0)
                {
                    try { Thread.sleep(10000); } catch (InterruptedException e) { }
                }
            }
        }

        if (args[0].equals("sync-data"))
        {
            String table_pattern = ""; // Default is all tables
            if (args.length > 1 && args[1] != null) table_pattern = args[1].trim();

            sync(table_pattern, false);
            return;
        }

        if (args[0].equals("compare-data"))
        {
            String table_pattern = ""; // Default is all tables
            if (args.length > 1 && args[1] != null) table_pattern = args[1].trim();

            sync(table_pattern, true);
            return;
        }

        if (args[0].equals("compare-tables"))
        {
            Database from = useEnvWithPrefix("SQLCOMP_SOURCE", "");
            Database to   = useEnvWithPrefix("SQLCOMP_SINK", "");

            ok &= Compare.compareTables(from, to);

            List<String> tables = Compare.inBoth(from.tableNames(), to.tableNames());
            for (String t : tables)
            {
                Table tt = to.table(t);
                Table ft = from.table(t);
                Compare.tableDefinition(ft, tt);
            }
            return;
        }

        System.out.println("sqlcomp: Unknown command "+args[0]);
        System.exit(1);
    }

    static Database useEnvWithPrefix(String prefix, String table_pattern)
    {
        String url = System.getenv(prefix+"_DB_URL"); // "jdbc:postgresql://localhost/fromcomp";

        if (url == null || url.trim().equals(""))
        {
            System.out.println("Please provide the following env variables:\n"+
                               prefix+"_NAME=MySourceDB\n"+
                               prefix+"_DB_URL=jdbc:postgresql://localhost/fromcomp\n"+
                               prefix+"_DB_USER=testuser\n"+
                               prefix+"_DB_PWD=asecret\n"+
                               prefix+"_DB_SCHEMA=");
            System.exit(1);
        }

        if (url.startsWith("jdbc:postgresql")) return new Postgres(prefix, table_pattern);
        if (url.startsWith("jdbc:mariadb")) return new Mysql(prefix, table_pattern);
        if (url.startsWith("jdbc:sqlserver")) return new SQLServer(prefix, table_pattern);

        Log.usageError("No supported database driver found inside url: "+url);
        System.exit(1);

        return null;
    }

    static void printHelp()
    {
        System.out.println("""
                           Usage: sqlcomp [command]

                           Available commands:
                           compare-tables
                           sync-data
                           stream-data
                           show-source-tables
                           show-sink-tables
                           """);
    }

    static void sync(String table, boolean dryrun)
    {
        LocalDateTime now = LocalDateTime.now();
        String iso_time = now.format(DateTimeFormatter.ISO_DATE_TIME);

        System.out.println("Sync started "+iso_time);

        Database source = useEnvWithPrefix("SQLCOMP_SOURCE", table);
        Database sink   = useEnvWithPrefix("SQLCOMP_SINK", table);

        if (table.length() > 0)
        {
            Table t = source.table(table);
            if (t == null)
            {
                System.out.println("sqlcomp: Unknown source table "+table);
                System.exit(1);
            }
            SyncData sync = new SyncData();
            sync.syncData(source, sink, t.name(), "", dryrun);

            now = LocalDateTime.now();
            iso_time = now.format(DateTimeFormatter.ISO_DATE_TIME);
            System.out.println("Sync completed "+iso_time);

            return;
        }

        SyncData sync = new SyncData();

        ArrayList<Table> tables = new ArrayList<>(source.tables());
        // Sync the small tables first!
        Collections.sort(tables, Table::compareRows);

        int i = 1;
        for (Table t : tables)
        {
            if (t.hasIntegerPrimaryKey())
            {
                // Skip tables with bad primary keys
                sync.syncData(source, sink, t.name(), ""+i+"/"+tables.size()+" ", dryrun);
            }
            else
            {
                System.out.println("WARNING: Skipping table "+t.name()+" because primary key is not an integer. ("+t.primaryKey()+")");
            }
            i++;
        }

        now = LocalDateTime.now();
        iso_time = now.format(DateTimeFormatter.ISO_DATE_TIME);
        System.out.println("Sync completed "+iso_time);
    }
}
