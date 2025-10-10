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
        sink_   = sink;
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
                    Log.verbose("(stream-data) ✅ connected to binlog\n");
                }

                @Override
                public void onCommunicationFailure(BinaryLogClient client, Exception ex) {
                    Log.error("(stream-data) ⚠️  communication failure: " + ex.getMessage()+"\n");
                }

                @Override
                public void onEventDeserializationFailure(BinaryLogClient client, Exception ex) {
                    Log.error("(stream-data) ⚠️  event deserialization failure: " + ex.getMessage()+"\n");
                }

                @Override
                public void onDisconnect(BinaryLogClient client) {
                    Log.error("(stream-data) ❌ disconnected from binlog\n");
                }
            });


            try {
                Log.verbose("(stream-data) connecting to binlog...\n");
                client.connect(20000); // forking call.
            } catch (IOException e) {
                e.printStackTrace();
                Log.error("(stream-data) binlog connection failed: " + e.getMessage()+"\n");
            }

            while (true)
            {
                source_.db().keepalive();
                sink_.db().keepalive();

                sink_.writeStatus();

                try { Thread.sleep(5000); } catch (InterruptedException e) { }
                // Stream until interrupted.
            }

        }
        catch (Exception e)
        {
            e.printStackTrace();
            Log.error("(stream-data) failure\n");
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
                Log.debug("(stream-data) EVENT "+event+"\n");
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
            Log.verbose("Ignoring event from "+table_data.getDatabase()+"\n");
            return;
        }
        Table source_table = source_.table(table_data.getTable());
        if (source_table == null)
        {
            if (!sink_.db().ignored(table_data.getTable().toLowerCase()))
            {
                Log.verbose("(stream-data) unknown source table: "+table_data.getTable()+"\n");
            }
            return;
        }
        Table sink_table = sink_.table(table_data.getTable());
        if (sink_table == null)
        {
            Log.warning("(stream-data) could not find sink table: "+table_data.getTable()+" Please create!\n");
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
            Log.verbose("(stream-data) ignoring "+table_data.getDatabase()+"\n");
            return;
        }
        Table source_table = source_.table(table_data.getTable());
        if (source_table == null)
        {
            if (!sink_.db().ignored(table_data.getTable().toLowerCase()))
            {
                Log.warning("(stream-data) unknown source table: "+table_data.getTable()+"\n");
            }
            return;
        }
        Table sink_table = sink_.table(table_data.getTable());
        if (sink_table == null)
        {
            Log.warning("(stream-data) could not find sink table: "+table_data.getTable()+" Please create!\n");
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
            Log.verbose("(stream-data) ignoring "+table_data.getDatabase()+"\n");
            return;
        }
        Table source_table = source_.table(table_data.getTable());
        if (source_table == null)
        {
            if (!sink_.db().ignored(table_data.getTable().toLowerCase()))
            {
                Log.warning("(stream-data) unknown source table: "+table_data.getTable()+"\n");
            }
            return;
        }
        Table sink_table = sink_.table(table_data.getTable());
        if (sink_table == null)
        {
            Log.warning("(stream-data) could not find sink table: "+table_data.getTable()+" Please create!\n");
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
