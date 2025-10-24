#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

TEST_SQLSERVER_INSTALLED=$(docker ps -a --filter "name=sqlcomp-test-sqlserver"  | grep -o sqlcomp-test-sqlserver)
TEST_SQLSERVER_ID=$(docker ps -a --filter "name=sqlcomp-test-sqlserver" | cut -f 1 -d ' ' | grep -v CONTAINER | sort -u)

if [ "$TEST_SQLSERVER_INSTALLED" = "sqlcomp-test-sqlserver" ]
then
    echo "Found sqlcomp-test-sqlsver with id $TEST_SQLSERVER_ID"
    TEST_SQLSERVER_RUNNING=$(docker ps --filter "name=sqlcomp-test-sqlserver"  | grep -o sqlcomp-test-sqlserver)

    if [ "$TEST_SQLSERVER_RUNNING" ]
    then
        docker stop sqlcomp-test-sqlserver
        echo "Stopped docker sqlcomp-test-sqlserver."
    fi

    echo "Removing docker container $TEST_SQLSERVER_ID"
    docker rm $TEST_SQLSERVER_ID

    echo "Removing unused volumes"
    docker volume prune

    echo "Cleaned up."

    exit 0
fi
