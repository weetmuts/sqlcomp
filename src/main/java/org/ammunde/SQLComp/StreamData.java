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

import com.github.shyiko.mysql.binlog.BinaryLogClient.EventListener;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventType;
import com.github.shyiko.mysql.binlog.event.DeleteRowsEventData;
import com.github.shyiko.mysql.binlog.event.UpdateRowsEventData;
import com.github.shyiko.mysql.binlog.event.WriteRowsEventData;
import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class StreamData
{
    Database source_;
    Database sink_;
    SyncData sync_;
    Map<Long,TableMapEventData> table_map = new HashMap<>();

    public StreamData(Database source, Database sink)
    {
        source_ = source;
        sink_ = sink;
        sync_ = new SyncData();
    }

    public void go()
    {
        try
        {
            BinaryLogClient client = new BinaryLogClient(source_.db().dbHost(), 3306, source_.db().dbUser(), source_.db().dbPwd());
            // Create a new client id that is the hash of source and sink.
            client.setServerId(source_.name().hashCode()+sink_.name().hashCode());
            EventDeserializer eventDeserializer = new EventDeserializer();
            /*
            eventDeserializer.setCompatibilityMode(EventDeserializer.CompatibilityMode.DATE_AND_TIME_AS_LONG,
            EventDeserializer.CompatibilityMode.CHAR_AND_BINARY_AS_BYTE_ARRAY);*/
            client.setEventDeserializer(eventDeserializer);
            client.registerEventListener(this::onEvent);
            // Set heartbeat interval in milliseconds (e.g., 10 seconds)
            client.setHeartbeatInterval(10000L); // 10,000 ms = 10 seconds

            // Optional: Set keep-alive interval to ensure reconnection logic
            client.setKeepAliveInterval(60000L); // 60 seconds
            client.setKeepAlive(true);

            client.registerLifecycleListener(new BinaryLogClient.LifecycleListener() {
                @Override
                public void onConnect(BinaryLogClient client) {
                    System.out.println("✅ Connected to MySQL binlog");
                }

                @Override
                public void onCommunicationFailure(BinaryLogClient client, Exception ex) {
                    System.out.println("⚠️ Communication failure: " + ex.getMessage());
                }

                @Override
                public void onEventDeserializationFailure(BinaryLogClient client, Exception ex) {
                    System.out.println("⚠️ Event deserialization failure: " + ex.getMessage());
                }

                @Override
                public void onDisconnect(BinaryLogClient client) {
                    System.out.println("❌ Disconnected from MySQL binlog");
                }
            });


            try {
                System.out.println("Connecting to MySQL binlog...");
                client.connect(20000); // forking call.
            } catch (IOException e) {
                System.err.println("Binlog connection failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Log.error("Failed to stream data! ");
            System.exit(1);
        }
    }

    void onEvent(Event event)
    {
        try
        {
            EventType type = event.getHeader().getEventType();

            if (type == EventType.TABLE_MAP) handleTableMap(event);
            else if (type == EventType.WRITE_ROWS || type == EventType.EXT_WRITE_ROWS) handleInsert(event);
            else if (type == EventType.UPDATE_ROWS || type == EventType.EXT_UPDATE_ROWS) handleUpdate(event);
            else if (type == EventType.DELETE_ROWS || type == EventType.EXT_DELETE_ROWS) handleDelete(event);
            else
            {
                // System.out.println("EVENT "+event);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    void handleTableMap(Event event)
    {
        TableMapEventData data = event.getData();
        table_map.put(data.getTableId(), data);
    }

    void handleInsert(Event event)
    {
        WriteRowsEventData data = event.getData();
        TableMapEventData table_data = table_map.get(data.getTableId());

        if (!table_data.getDatabase().equals(source_.db().dbName()))
        {
            System.out.println("Ignoring "+table_data.getDatabase());
            return;
        }
        Table source_table = source_.table(table_data.getTable());
        if (source_table == null)
        {
            System.out.println("Could not find source table: "+table_data.getTable());
            return;
        }
        Table sink_table = sink_.table(table_data.getTable());
        if (sink_table == null)
        {
            System.out.println("\n\nWARNING STREAM Could not find sink table: "+table_data.getTable()+" Please create!\n");
            return;
        }
        List<Serializable[]> rows = data.getRows();

        for (Serializable[] row : rows)
        {
            // Assuming primary key is the first column (index 0)
            Serializable pks = row[0];
            int pk = Integer.parseInt(""+pks);
            int[] pka = { pk };
            PK chunk = new PK(pk, pk, pka);
            sync_.syncChunk(source_table, sink_table, chunk, true, false);
        }
    }

    void handleUpdate(Event event)
    {
        UpdateRowsEventData data = event.getData();
        TableMapEventData table_data = table_map.get(data.getTableId());

        if (!table_data.getDatabase().equals(source_.db().dbName()))
        {
            System.out.println("Ignoring "+table_data.getDatabase());
            return;
        }
        Table source_table = source_.table(table_data.getTable());
        if (source_table == null)
        {
            System.out.println("Could not find source table: "+table_data.getTable());
            return;
        }
        Table sink_table = sink_.table(table_data.getTable());
        if (sink_table == null)
        {
            System.out.println("Could not find sink table: "+table_data.getTable());
            return;
        }
        for (Map.Entry<Serializable[], Serializable[]> row : data.getRows())
        {
            Serializable[] old_row = row.getKey(); // before update
            Serializable[] new_row = row.getValue(); // after update

            // Assuming primary key is the first column (index 0)
            // This is hardcoded for now!
            Serializable pks = old_row[0];
            int pk = Integer.parseInt(""+pks);

            int[] pka = { pk };
            PK chunk = new PK(pk, pk, pka);
            sync_.syncChunk(source_table, sink_table, chunk, true, false);
        }
    }

    void handleDelete(Event event)
    {
        DeleteRowsEventData data = event.getData();
        TableMapEventData table_data = table_map.get(data.getTableId());

        if (!table_data.getDatabase().equals(source_.db().dbName()))
        {
            System.out.println("Ignoring "+table_data.getDatabase());
            return;
        }
        Table source_table = source_.table(table_data.getTable());
        if (source_table == null)
        {
            System.out.println("Could not find source table: "+table_data.getTable());
            return;
        }
        Table sink_table = sink_.table(table_data.getTable());
        if (sink_table == null)
        {
            System.out.println("Could not find sink table: "+table_data.getTable());
            return;
        }

        List<Serializable[]> rows = data.getRows();

        for (Serializable[] row : rows)
        {
            // Assuming primary key is the first column (index 0)
            Serializable pks = row[0];
            int pk = Integer.parseInt(""+pks);
            int[] pka = { pk };
            PK chunk = new PK(pk, pk, pka);
            sync_.syncChunk(source_table, sink_table, chunk, true, false);
        }
    }

}
