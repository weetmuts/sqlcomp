#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (spdx: MIT)

./tests/start_docker_mysql.sh
./tests/start_docker_postgres.sh

SQLCOMP=$1
OUTPUT_BASE=$2
FILTER=$3

if [ -z "$SQLCOMP" ]; then SQLCOMP=target/sqlcomp; fi
if [ -z "$OUTPUT_BASE" ]; then OUTPUT_BASE=target/test_output; fi

for i in tests/compare_[0-9][0-9][0-9]_*.sh
do
    if [ -n $FILTER ] && [[ ! "$i" =~ $FILTER ]]; then continue; fi
    OUTPUT=$OUTPUT_BASE/$(basename $i .sh)
    $i $SQLCOMP $OUTPUT mysql mysql
    if [ "$?" != 0 ]; then echo "Testing aborted"; exit 1 ; fi
done
