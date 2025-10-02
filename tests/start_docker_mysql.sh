#!/bin/bash

TEST_MYSQL_INSTALLED=$(docker ps -a --filter "name=sqlcomp-test-mysql"  | grep -o sqlcomp-test-mysql)

if [ "$TEST_MYSQL_INSTALLED" = "sqlcomp-test-mysql" ]
then
    TEST_MYSQL_RUNNING=$(docker ps --filter "name=sqlcomp-test-mysql"  | grep -o sqlcomp-test-mysql)

    if [ -z "$TEST_MYSQL_RUNNING" ]
    then
        docker start sqlcomp-test-mysql
    fi

    exit 0
fi

# Install a mysql server locally using docker, use a nonstandard port to avoid conflicts.
docker run --name sqlcomp-test-mysql -e MYSQL_ROOT_PASSWORD=leroot -p 3333:3306 -d mysql:latest
