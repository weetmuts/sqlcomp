#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

# Env variables pass to the client
export MYSQL_PWD=leroot
export PGPASSWORD=leroot
# Stuffed into the docker command
SQLPASSWORD=LeRoot5_LeRoot5

MYSQL="mysql -h 127.0.0.1 -P 3333 -uroot"
POSTGRES="psql -h 127.0.0.1 -p 4444 -U postgres"
SQLCMD="docker exec -e SQLCMDPASSWORD=${SQLPASSWORD} -i sqlcomp-test-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U SA -C "

DROP="drop database if exists test_from;"
echo $DROP| $POSTGRES
echo $DROP| $POSTGRES
echo $DROP| $SQLSERVER

DROP="drop database if exists test_to;"
echo $DROP| $POSTGRES
echo $DROP| $POSTGRES
echo $DROP| $SQLSERVER
