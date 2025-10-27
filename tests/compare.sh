#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

. ./tests/prepare_dbs.sh "$1" "$2" "$3" "$4" "$5"

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
