#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

SOURCE_SINK_SETUP=$1
SQLCOMP=$2
OUTPUT=$3
SOURCE_DB_TYPE=$4
SINK_DB_TYPE=$5

mkdir -p $OUTPUT
STDERR=$OUTPUT/stderr.log

. tests/environment

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
export SQLCOMP_SOURCE_DB_NAME="test_from"
#export SQLCOMP_SOURCE_DB_USER
#export SQLCOMP_SOURCE_DB_PWD
export SQLCOMP_SOURCE_SCHEMA=""
export SQLCOMP_SOURCE_IGNORED_TABLES=""

export SQLCOMP_SINK_NAME="MyToDB"
# SQLCOMP_SINK_DB_URL set before
export SQLCOMP_SINK_DB_NAME="test_to"
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
