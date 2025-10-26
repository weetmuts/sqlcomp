#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

. tests/environment

cd cached_test_dbs
$MYSQL < employees.sql
