#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

TEST_MYSQL_INSTALLED=$(docker ps -a --filter "name=sqlcomp-test-mysql"  | grep -o sqlcomp-test-mysql)
TEST_MYSQL_ID=$(docker ps -a --filter "name=sqlcomp-test-mysql" | cut -f 1 -d ' ' | grep -v CONTAINER | sort -u)

if [ "$TEST_MYSQL_INSTALLED" = "sqlcomp-test-mysql" ]
then
    echo "Found sqlcomp-test-sqlsver with id $TEST_MYSQL_ID"
    TEST_MYSQL_RUNNING=$(docker ps --filter "name=sqlcomp-test-mysql"  | grep -o sqlcomp-test-mysql)

    if [ "$TEST_MYSQL_RUNNING" ]
    then
        docker stop sqlcomp-test-mysql
        echo "Stopped docker sqlcomp-test-mysql."
    fi

    echo "Removing docker container $TEST_MYSQL_ID"
    docker rm $TEST_MYSQL_ID

    echo "Removing unused volumes"
    docker volume prune

    echo "Cleaned up."

    exit 0
fi
