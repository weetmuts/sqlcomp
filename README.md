# SQLComp a tool to compare database schemas and content.

This is version 0.1, it can sync and stream from MySQL to Mysql/SQLServer.

It expects each table to have a single primary key which is an integer. This
is the case for many ORM databases (like sequelize etc).

If you start "stream-data" from source to sink it will immediatly listen
to binlog events from MySQL to perform incremental updates. However it only
uses the primary key in the CREATE/UPDATE/DELETE event to resync this particular
row of data between the source and sink.

At the same time it starts a full sync of all the tables by reading chunks of
100 rows from source and sink, and comparing the content. If there is an incremental
change it will update the sink with only the fields that need updating. Inserts and
deletes should work as expected.

When the full sync is complete, it will do nothing except listening to binlog changes
and syncing those rows. At 00:02 in the middle of the night, it will trigger a new full
sync, just in case something bad happened to the binlog sync during the day.

### Caveats:

Note! The sink table content is NOT atomically updated! This means that
SQLComp has to disable foreign key checking when writing to the sink database.
Do not use this tool if this is a blocker for your use case!

It has some workarounds for the loss of some UTF8 data in SQLServer,
but generally if you sync UTF8 from MySQL into a SQLServer table that does
not support it, tough luck, you will get repeated updates to
fix the sink table. It also removes zero width spaces from the text fields.

### Benefits:

It should be idempotent, ie you can cancel it, restart it at any time,
it will sync the actual differences. Streaming will take care of changes
happening concurrently.

It gives a reasonable estimate for when the sync is complete for a large table!

It does not use much memory and no disk space. It works on batches of 100 rows.

You have to make sure the source and sink table definitions
are compatible to perform the sync.

Sqlcomp can help you with this to produce suggested CREATE/ALTER/DROP
commands to modify the sink to conform to the source.

This does not happen automatically, since it is such a potentially destructive
process. For example, it cannot detect a rename of a table, this will become
a drop and a create.

### How to use it:

Create a config.xmq file:
```
sqlcomp {
    source {
        name          = MyMysql
        db_url        = jdbc:mariadb://mysourcedatabase/fromcomp?autoReconnect=true&allowMultiQueries=true
        db_name       = fromcomp
        db_user       = testuser
        db_pwd        = asecret
        schema        = ''
        ignore_tables = sequelizedata,sequelizemeta
    }
    sink {
        name          = ToSQLServer
        db_url        = jdbc:sqlserver://mysinkdatabase:1433;databaseName=tocomp;ConnectRetryCount=3;ConnectRetryInterval=10
        db_name       = tocomp
        db_user       = testuser
        db_pwd        = asecret
        schema        = tocomp
        ignore_tables = sequelizedata,sequelizemeta
    }
}
```

(Note that when sinking to MySQL you need to specify `allowMultiQueries=true`
since sqlcomp generates multiple updates in a single statement. Note that when sinking to SQL server
you need `ConnectRetryCount=3;ConnectRetryInterval=10` and for MySQL `autoReconnect=true` since
sqlcomp does not retry for you if the connection is shut down due to an idle timeout.)

The command sync-data command will sync all tables (that have not been ignored)
printing warnings for tables without proper integer primary keys and printing
warning for tables that exist in the source but does not exist in sink.
Wether or not the tables are compatible for syncing will only be found out at runtime.

```
sqlcomp config.xmq sync-data
```

To only sync a single table, just add the name of the table:
```
sqlcomp config.xmq sync-data myaddresstable
```

To perform a dry-run, printing the changes on stdout do:
```
sqlcomp config.xmq compare-data myaddresstable
```

The command stream-data listens to the binlog events from a mysql source
and performs a sync on rows for which the primary keys appear in the binlog.
(I.e. it does not apply the binlog changes, it just use it to trigger the normal sync
on those rows.) It also immediately performs a full sync-data, and repeating
this sync-data in the night 02:00 every day.

```
sqlcomp config.xmq stream-data
```

Likewise add the table name to only stream a single table:

```
sqlcomp config.xmq stream-data myaddresstable
```

The command compare-tables will print suggested commands to modify
the sink databases to be similar to the source database.

```
sqlcomp config.xmq compare-tables
```

Might print:

```
CREATE TABLE names (EntityKey bigint not null, Name text not null, primary key(EntityKey));
DROP TABLE ages;
ALTER TABLE salary ADD LastPaid timestamp;
ALTER TABLE salary DROP COLUMN LastPid;
```
