#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

SOURCE_SINK_SETUP=$1
SQLCOMP=$2
OUTPUT=$3
SOURCE_DB_TYPE=$4
SINK_DB_TYPE=$5

mkdir -p $OUTPUT
STDERR=$OUTPUT/stderr.log

# Env variables pass to the client
export MYSQL_PWD=leroot
export PGPASSWORD=leroot
# Stuffed into the docker command
SQLPASSWORD=LeRoot5_LeRoot5

SOURCE_DB_NAME=test1_from
SINK_DB_NAME=test1_to

MYSQL="mysql -h 127.0.0.1 -P 3333 -uroot"
MYSQL_SOURCE_DB_URL="jdbc:mariadb://127.0.0.1:3333/${SOURCE_DB_NAME}?autoReconnect=true&allowMultiQueries=true"
MYSQL_SINK_DB_URL="jdbc:mariadb://127.0.0.1:3333/${SINK_DB_NAME}?autoReconnect=true&allowMultiQueries=true"

POSTGRES="psql -h 127.0.0.1 -p 4444 -U postgres"
POSTGRES_SOURCE_DB_URL="jdbc:postgresql://127.0.0.1:4444/${SOURCE_DB_NAME}?reWriteBatchedInserts=true"
POSTGRES_SINK_DB_URL="jdbc:postgresql://127.0.0.1:4444/${SINK_DB_NAME}?reWriteBatchedInserts=true"

SQLCMD="docker exec -e SQLCMDPASSWORD=${SQLPASSWORD} -i sqlcomp-test-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U SA -C "
SQLSERVER_SOURCE_DB_URL="jdbc:sqlserver://127.0.0.1:5555;databaseName=${SOURCE_DB_NAME};encrypt=true;trustServerCertificate=true"
SQLSERVER_SINK_DB_URL="jdbc:sqlserver://127.0.0.1:5555;databaseName=${SINK_DB_NAME};encrypt=true;trustServerCertificate=true"

echo -n > $STDERR

if [ "$SOURCE_DB_TYPE" = "mysql" ]
then
    SOURCE_DB_CMD="$MYSQL"
    USE_SOURCE_DB=" $SOURCE_DB_NAME"
    export SQLCOMP_SOURCE_DB_URL=$MYSQL_SOURCE_DB_URL
    export SQLCOMP_SOURCE_DB_USER="root"
    export SQLCOMP_SOURCE_DB_PWD="leroot"
elif [ "$SOURCE_DB_TYPE" = "postgres" ]
then
    SOURCE_DB_CMD="$POSTGRES"
    USE_SOURCE_DB=" -d $SOURCE_DB_NAME"
    export SQLCOMP_SOURCE_DB_URL=$POSTGRES_SOURCE_DB_URL
    export SQLCOMP_SOURCE_DB_USER="postgres"
    export SQLCOMP_SOURCE_DB_PWD="leroot"
elif [ "$SOURCE_DB_TYPE" = "sqlserver" ]
then
    SOURCE_DB_CMD="$SQLCMD"
    USE_SOURCE_DB=" -d $SOURCE_DB_NAME"
    export SQLCOMP_SOURCE_DB_URL=$SQLSERVER_SOURCE_DB_URL
    export SQLCOMP_SOURCE_DB_USER="SA"
    export SQLCOMP_SOURCE_DB_PWD="$SQLPASSWORD"
else
    echo "Unknown source db type: $SOURCE_DB_TYPE"
    exit 1
fi

if [ "$SINK_DB_TYPE" = "mysql" ]
then
    SINK_DB_CMD="$MYSQL"
    USE_SINK_DB=" $SINK_DB_NAME"
    export SQLCOMP_SINK_DB_URL=$MYSQL_SINK_DB_URL
    export SQLCOMP_SINK_DB_USER="root"
    export SQLCOMP_SINK_DB_PWD="leroot"
elif [ "$SINK_DB_TYPE" = "postgres" ]
then
    SINK_DB_CMD="$POSTGRES"
    USE_SINK_DB=" -d $SINK_DB_NAME"
    export SQLCOMP_SINK_DB_URL=$POSTGRES_SINK_DB_URL
    export SQLCOMP_SINK_DB_USER="postgres"
    export SQLCOMP_SINK_DB_PWD="leroot"
elif [ "$SINK_DB_TYPE" = "sqlserver" ]
then
    SINK_DB_CMD="$SQLCMD"
    USE_SINK_DB=" -d $SINK_DB_NAME"
    export SQLCOMP_SINK_DB_URL=$SQLSERVER_SINK_DB_URL
    export SQLCOMP_SINK_DB_USER="SA"
    export SQLCOMP_SINK_DB_PWD="$SQLPASSWORD"
else
    echo "Unknown sink db type: $SINK_DB_TYPE"
    exit 1
fi

export SQLCOMP_STATUS_HTML="status.html"

export SQLCOMP_SOURCE_NAME="MyFromDB"
# SQLCOMP_SOURCE_DB_URL set before
export SQLCOMP_SOURCE_DB_NAME="test1_from"
#export SQLCOMP_SOURCE_DB_USER
#export SQLCOMP_SOURCE_DB_PWD
export SQLCOMP_SOURCE_SCHEMA=""
export SQLCOMP_SOURCE_IGNORED_TABLES=""

export SQLCOMP_SINK_NAME="MyToDB"
# SQLCOMP_SINK_DB_URL set before
export SQLCOMP_SINK_DB_NAME="test1_to"
# export SQLCOMP_SINK_DB_USER
# export SQLCOMP_SINK_DB_PWD
export SQLCOMP_SINK_SCHEMA=""
export SQLCOMP_SINK_IGNORED_TABLES=""

# Prepare source and sink databases

. $SOURCE_SINK_SETUP

# Prepare a set set of alter, create and drop commands.

$SQLCOMP $VERBOSE compare-tables > $OUTPUT/alter.sql

# Apply the changes.
cat $OUTPUT/alter.sql | $SINK_DB_CMD $USE_SINK_DB

if [ "$?" != "0" ]
then
    cat $OUTPUT/alter.sql
    echo "ERROR: could not alter sink db with about sql"
fi

# Now there should be no differences.
$SQLCOMP $VERBOSE compare-tables

if [ "$?" != "0" ]
then
    echo "ERROR: expected no differences"
else
    echo "OK: $SOURCE_DB_TYPE->$SINK_DB_TYPE $1"
fi
