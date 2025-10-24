#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

TEST_SQLSERVER_INSTALLED=$(docker ps -a --filter "name=sqlcomp-test-sqlserver"  | grep -o sqlcomp-test-sqlserver)

if [ "$TEST_SQLSERVER_INSTALLED" = "sqlcomp-test-sqlserver" ]
then
    TEST_SQLSERVER_RUNNING=$(docker ps --filter "name=sqlcomp-test-sqlserver"  | grep -o sqlcomp-test-sqlserver)

    if [ -z "$TEST_SQLSERVER_RUNNING" ]
    then
        docker start sqlcomp-test-sqlserver
        echo "Restarted docker sqlserver."
    fi

    exit 0
fi

# Install a sql server locally using docker, use a nonstandard port to avoid conflicts.
docker run -e "ACCEPT_EULA=Y" -e "SA_PASSWORD=LeRoot5_LeRoot5" -p 5555:1433 --name sqlcomp-test-sqlserver --hostname sqlcomp-test-sqlserver -d mcr.microsoft.com/mssql/server:2025-latest
echo "Started docker sqlserver. Please wait 20 seconds for it to spin up."
