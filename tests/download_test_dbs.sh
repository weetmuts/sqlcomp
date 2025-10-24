#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

if [ -z "$SQLCOMP" ]; then SQLCOMP=target/sqlcomp; fi
if [ -z "$OUTPUT_BASE" ]; then OUTPUT_BASE=target/test_output; fi

mkdir -p cached_test_dbs

cd cached_test_dbs

if [ ! -f employees.sql ]
then
    wget -nc https://github.com/datacharmer/test_db/raw/refs/heads/master/employees.sql
    wget -nc https://github.com/datacharmer/test_db/raw/refs/heads/master/load_departments.dump
    wget -nc https://github.com/datacharmer/test_db/raw/refs/heads/master/load_dept_emp.dump
    wget -nc https://github.com/datacharmer/test_db/raw/refs/heads/master/load_dept_manager.dump
    wget -nc https://github.com/datacharmer/test_db/raw/refs/heads/master/load_employees.dump
    wget -nc https://github.com/datacharmer/test_db/raw/refs/heads/master/load_salaries1.dump
    wget -nc https://github.com/datacharmer/test_db/raw/refs/heads/master/load_salaries2.dump
    wget -nc https://github.com/datacharmer/test_db/raw/refs/heads/master/load_salaries3.dump
    wget -nc https://github.com/datacharmer/test_db/raw/refs/heads/master/load_titles.dump
    wget -nc https://github.com/datacharmer/test_db/raw/refs/heads/master/show_elapsed.sql
    wget -nc https://github.com/datacharmer/test_db/raw/refs/heads/master/test_employees_md5.sql
    wget -nc https://github.com/datacharmer/test_db/raw/refs/heads/master/test_employees_sha.sql
    wget -nc https://github.com/datacharmer/test_db/raw/refs/heads/master/test_versions.sh
fi
