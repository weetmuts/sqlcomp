#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

TEST_POSTGRES_INSTALLED=$(docker ps -a --filter "name=sqlcomp-test-postgres"  | grep -o sqlcomp-test-postgres)

if [ "$TEST_POSTGRES_INSTALLED" = "sqlcomp-test-postgres" ]
then
    TEST_POSTGRES_RUNNING=$(docker ps --filter "name=sqlcomp-test-postgres"  | grep -o sqlcomp-test-postgres)

    if [ -z "$TEST_POSTGRES_RUNNING" ]
    then
        docker start sqlcomp-test-postgres
        echo "Restarted postgres docker."
    fi

    exit 0
fi

# Install a postgres server locally using docker, use a nonstandard port to avoid conflicts.
docker run --name sqlcomp-test-postgres -e POSTGRES_PASSWORD=leroot -p 4444:5432 -d postgres:latest
echo "Started postgres docker."
