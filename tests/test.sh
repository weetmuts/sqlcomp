#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

./tests/start_docker_mysql.sh
./tests/start_docker_postgres.sh
./tests/start_docker_sqlserver.sh

if [ -n "$VERBOSE" ]
then
    docker ps
fi

SQLCOMP=$1
OUTPUT_BASE=$2
SOURCE=$3
SINK=$4
FILTER=$5

if [ -z "$SQLCOMP" ]; then SQLCOMP=target/sqlcomp; fi
if [ -z "$OUTPUT_BASE" ]; then OUTPUT_BASE=target/test_output; fi

echo "postgres->postgres"
tests/test_run.sh $SQLCOMP ${OUTPUT_BASE}_postgres_postgres postgres postgres $FILTER

echo "mysql->mysql"
tests/test_run.sh $SQLCOMP ${OUTPUT_BASE}_mysql_myql mysql mysql $FILTER

echo "sqlserver->sqlserver"
tests/test_run.sh $SQLCOMP ${OUTPUT_BASE}_sqlserver_sqlserver sqlserver sqlserver $FILTER

echo "postgres->mysql"
tests/test_run.sh $SQLCOMP ${OUTPUT_BASE}_postgres_mysql postgres mysql $FILTER

echo "postgres->sqlserver"
tests/test_run.sh $SQLCOMP ${OUTPUT_BASE}_postgres_mysql postgres sqlserver $FILTER

echo "mysql->postgres"
tests/test_run.sh $SQLCOMP ${OUTPUT_BASE}_mysql_postgres mysql postgres $FILTER

echo "mysql->sqlserver"
tests/test_run.sh $SQLCOMP ${OUTPUT_BASE}_mysql_postgres mysql sqlserver $FILTER
