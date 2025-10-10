#!/bin/bash
docker exec -e SQLCMDPASSWORD=LeRoot5_LeRoot5 -it sqlcomp-test-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U SA -C
