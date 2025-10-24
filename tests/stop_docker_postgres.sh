#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

TEST_POSTGRES_INSTALLED=$(docker ps -a --filter "name=sqlcomp-test-postgres"  | grep -o sqlcomp-test-postgres)
TEST_POSTGRES_ID=$(docker ps -a --filter "name=sqlcomp-test-postgres" | cut -f 1 -d ' ' | grep -v CONTAINER | sort -u)

if [ "$TEST_POSTGRES_INSTALLED" = "sqlcomp-test-postgres" ]
then
    echo "Found sqlcomp-test-sqlsver with id $TEST_POSTGRES_ID"
    TEST_POSTGRES_RUNNING=$(docker ps --filter "name=sqlcomp-test-postgres"  | grep -o sqlcomp-test-postgres)

    if [ "$TEST_POSTGRES_RUNNING" ]
    then
        docker stop sqlcomp-test-postgres
        echo "Stopped docker sqlcomp-test-postgres."
    fi

    echo "Removing docker container $TEST_POSTGRES_ID"
    docker rm $TEST_POSTGRES_ID

    echo "Removing unused volumes"
    docker volume prune

    echo "Cleaned up."

    exit 0
fi
