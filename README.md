# SQLComp a tool to compare database schemas and content.

This is version 0.1, it can sync and stream from MySQL to Mysql/SQLServer.

It has some workarounds for the loss of some UTF8 data in SQLServer,
but generally if you sync UTF8 from MySQL into a SQLServer table that does
not support it, tough luck, you will get repeated updates to
fix the sink table.

Note! It is currently tailored for typical ORM databases (sequelize etc)
where the primary key is the first column in every table and the primary
key is an integer.

It is idempotent, ie you can cancel it, restart it at any time,
it will sync the differences. This is true for the streaming as well.
The sink state is NOT atomically updated for all changes!

However:

It gives a reasonable estimate for when the sync is complete for a large table!

It does not use much memory. It works on batches of 100 rows.

It can listen to the binlog stream from Mysql and perform a sync on changed rows.

It can compare the table definitions as well and propose CREATE and ALTER commands.

Setup environment variables:
```
export SQLCOMP_SOURCE_NAME=MyMysql
export SQLCOMP_SOURCE_DB_URL=jdbc:mysql://localhost/fromcomp
export SQLCOMP_SOURCE_DB_NAME=fromcomp
export SQLCOMP_SOURCE_DB_USER=testuser
export SQLCOMP_SOURCE_DB_PWD=asecret
export SQLCOMP_SOURCE_SCHEMA=
export SQLCOMP_SOURCE_IGNORED_TABLES=sequelizedata,sequelizemeta

export SQLCOMP_SINK_NAME=ToSQLServer
export SQLCOMP_SINK_DB_URL=jdbc:sqlserver://localhost/anotherdatabase
export SQLCOMP_SINK_DB_NAME=anotherdatabase
export SQLCOMP_SINK_DB_USER=testuser
export SQLCOMP_SINK_DB_PWD=asecret
export SQLCOMP_SINK_SCHEMA=tocomp
export SQLCOMP_SOURCE_IGNORED_TABLES=sequelizedata,sequelizemeta
```

The command sync-data command will perform updates/inserts/deletes in the selected
target table and report the progress and estimated time to finish.
The tables in the source and sink must be reasonably similar.

```
sqlcomp sync-data address
```

To just print the changes to stdout do:
```
sqlcomp compare-data address
```

The command stream-data listens to the binlog events from a mysql source
and performs a sync-data on the primary keys that appear in the binlog.

```
sqlcomp stream-data address
```

To sync or stream all tables, just drop the table name.

```
sqlcomp stream-data
```

The command compare-tables will print suggested commands to modify
the sink databases to be similar to the source datbase.

```
sqlcomp compare-tables
```

Might in the future print, not there yet.
```
CREATE TABLE names (EntityKey bigint not null, Name text not null, primary key(EntityKey));
DROP TABLE ages;
ALTER TABLE salary ADD LastPaid timestamp;
ALTER TABLE salary DROP COLUMN LastPid;
```
