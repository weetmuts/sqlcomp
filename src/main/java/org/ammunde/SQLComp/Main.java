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
        if (args.length == 0)
        {
            printHelp();
            return;
        }

        // Pick out --verbose and --debug
        args = Log.parseArgs(args);

        String cmd = args[0];

        if (cmd.equals("show-source-tables"))
        {
            Database source = useEnvWithPrefix("SQLCOMP_SOURCE", "");
            showTables(source);
            return;
        }

        if (cmd.equals("show-sink-tables"))
        {
            Database sink = useEnvWithPrefix("SQLCOMP_SINK", "");
            showTables(sink);
            return;
        }

        if (cmd.equals("stream-data"))
        {
            Status status = new Status("stream-data");
            stream_data(args, status);
            repeat_sync_data(args, status);
        }

        if (cmd.equals("stream-data-no-sync"))
        {
            Status status = new Status("stream-data-no-sync");
            stream_data(args, status);
            while (true)
            {
                try { Thread.sleep(10000); } catch (InterruptedException e) { }
            }
        }

        if (cmd.equals("sync-data"))
        {
            Status status = new Status("sync-data");

            String table_pattern = ""; // Default is all tables
            if (args.length > 1 && args[1] != null) table_pattern = args[1].trim();

            if (!table_pattern.equals("")) Log.verbose("(sync-data) "+table_pattern+"\n");
            else Log.verbose("(sync-data) all tables\n");

            sync(table_pattern, false, status);
            return;
        }

        if (cmd.equals("compare-data"))
        {
            String table_pattern = ""; // Default is all tables
            if (args.length > 1 && args[1] != null) table_pattern = args[1].trim();

            if (!table_pattern.equals("")) Log.verbose("(compare-data) "+table_pattern+"\n");
            else Log.verbose("(comapare-data) all tables\n");

            sync(table_pattern, true, null);
            return;
        }

        if (cmd.equals("compare-tables"))
        {
            Database from = useEnvWithPrefix("SQLCOMP_SOURCE", "");
            Database to   = useEnvWithPrefix("SQLCOMP_SINK", "");

            Log.verbose("(compare-tables) "+from.name()+" --> "+to.name()+"\n");
            boolean change_detected = Compare.compareTables(from, to);

            List<String> tables = Compare.inBoth(from.tableNames(), to.tableNames());
            for (String t : tables)
            {
                Table tt = to.table(t);
                Table ft = from.table(t);
                change_detected |= Compare.tableDefinition(ft, tt);
            }

            if (change_detected)
            {
                // Changes have already been printed.
                System.exit(1);
            }
            else
            {
                Log.verbose("(compare-tables) no changes found\n");
                System.exit(0);
            }
            return;
        }

        Log.usageError("sqlcomp: Unknown command "+cmd+"\n");
        System.exit(1);
    }

    static Database useEnvWithPrefix(String prefix, String table_pattern)
    {
        String url = System.getenv(prefix+"_DB_URL"); // "jdbc:postgresql://localhost/fromcomp";

        if (url == null || url.trim().equals(""))
        {
            Log.info("sqlcomp: please provide the following env variables:\n"+
                     prefix+"_NAME=MySourceDB\n"+
                     prefix+"_DB_URL=jdbc:postgresql://localhost/fromcomp\n"+
                     prefix+"_DB_USER=testuser\n"+
                     prefix+"_DB_PWD=asecret\n"+
                     prefix+"_DB_SCHEMA=\n"+
                     prefix+"_DB_IGNORED_TABLES=");
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
        Log.info("""
                 Usage: sqlcomp [command]

                 Available commands:
                 compare-tables
                 compare-data
                 sync-data
                 stream-data
                 show-source-tables
                 show-sink-tables
                 """);
    }

    static void sync(String table_pattern, boolean dryrun, Status status)
    {
        Log.verbose("(sync-data) start table ["+table_pattern+"] "
                           +System.getenv("SQLCOMP_SOURCE_NAME")
                           +" --> "
                           +System.getenv("SQLCOMP_SINK_NAME")+"\n");

        Database source = useEnvWithPrefix("SQLCOMP_SOURCE", table_pattern);
        Database sink   = useEnvWithPrefix("SQLCOMP_SINK", table_pattern);

        sink.track(status);

        if (table_pattern.length() > 0)
        {
            Table t = source.table(table_pattern);
            if (t == null)
            {
                Log.error("sqlcomp: Unknown source table "+table_pattern+"\n");
                System.exit(1);
            }
            SyncData sync = new SyncData();
            sync.syncData(source, sink, t.name(), "", dryrun);

            Log.verbose("(sync-data) stop sync ["+table_pattern+"] "
                        +System.getenv("SQLCOMP_SOURCE_NAME")
                        +" --> "
                        +System.getenv("SQLCOMP_SINK_NAME")+"\n");

            try
            {
                source.db().close();
                sink.db().close();
            }
            catch (java.sql.SQLException e)
            {
                e.printStackTrace();
            }

            return;
        }

        SyncData sync = new SyncData();

        ArrayList<Table> tables = new ArrayList<>(source.tables());
        // Sync the small tables first!
        Collections.sort(tables, Table::compareDiskSize);

        int i = 1;
        for (Table t : tables)
        {
            if (t.hasIntegerPrimaryKey())
            {
                // Skip tables with bad primary keys
                String step;
                if (tables.size() < 100)
                {
                    step = String.format("%02d/%02d ", i, tables.size());
                }
                else
                {
                    step = String.format("%03d/%03d ", i, tables.size());
                }

                sync.syncData(source, sink, t.name(), step, dryrun);
            }
            else
            {
                Log.warning("(sync-data) skipping table "+t.name()+" because primary key is not an integer. ("+t.primaryKey()+")\n");
            }
            i++;
        }

        Log.verbose("(sync-data) complete ["+table_pattern+"] "
                    +System.getenv("SQLCOMP_SOURCE_NAME")
                    +" --> "
                    +System.getenv("SQLCOMP_SINK_NAME")+"\n");

        try
        {
            source.db().close();
            sink.db().close();
        }
        catch (java.sql.SQLException e)
        {
            e.printStackTrace();
        }
    }

    static void showTables(Database d)
    {
        ArrayList<Table> tables = new ArrayList<>(d.tables());
        Collections.sort(tables, Table::compareDiskSize);

        int i = 1;
        String num = ""+tables.size();
        for (Table t : tables)
        {
            String nr = ""+i+"/"+num;
            Log.info(nr+" "+Util.rightPad(t.name(), d.maxTableNameLength(), ' ')
                     +"  "+Util.rightPad(Util.sizeHR(1024*t.approxDiskSizeKB()),15,' ')
                     +"  "+Util.rightPad(t.approxNumRows()+" c.rows ",15,' ')
                     +" ("+t.primaryKey()+")\n");
            i++;
        }
    }

    static void stream_data(String[] args, Status status)
    {
        String table_pattern = "";
        if (args.length > 1 && args[1] != null) table_pattern = args[1].trim();

        if (!table_pattern.equals("")) Log.verbose("(stream-data) streaming "+table_pattern+"\n");
        else Log.verbose("(stream-data) streaming all tables\n");

        Database source = useEnvWithPrefix("SQLCOMP_SOURCE", table_pattern);
        Database sink   = useEnvWithPrefix("SQLCOMP_SINK", table_pattern);
        sink.track(status);

        StreamData stream = new StreamData(source, sink);

        Thread thread = new Thread(stream::go);
        thread.start();
    }

    static void repeat_sync_data(String[] args, Status status)
    {
        String table_pattern = "";
        if (args.length > 1 && args[1] != null) table_pattern = args[1].trim();

        if (!table_pattern.equals("")) Log.verbose("(repeat-sync-data) "+table_pattern+"\n");
        else Log.verbose("(repeat-sync-data) streaming all tables\n");

        status.clearStats();
        sync(table_pattern, false, status);
        status.batchDone();

        // Stream forever.
        while (true)
        {
            try { Thread.sleep(10000); } catch (InterruptedException e) { }

            LocalTime now = Util.localTime();
            if (now.getHour() == 3 && now.getMinute() == 0)
            {
                Log.verbose("(stream-data) nightly sync started\n");
                status.clearStats();
                sync(table_pattern, false, status);
                status.batchDone();
            }
            // Do not try to sync again during this minutes. Only relevant
            // if you have a really small database.
            while (now.getHour() == 3 && now.getMinute() == 0)
            {
                try { Thread.sleep(10000); } catch (InterruptedException e) { }
            }
        }
    }
}
