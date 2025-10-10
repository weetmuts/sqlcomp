#!/bin/bash
# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

SQLCOMP=$1
OUTPUT_BASE=$2
SOURCE_DB_TYPE=$3
SINK_DB_TYPE=$4
FILTER=$5

if [ -z "$SQLCOMP" ]; then exit 1; fi

for i in tests/compare_[0-9][0-9][0-9]_*.sh
do
    if [ -n $FILTER ] && [[ ! "$i" =~ $FILTER ]]; then continue; fi
    OUTPUT=$OUTPUT_BASE/$(basename $i .sh)
    tests/compare.sh $i $SQLCOMP $OUTPUT $SOURCE_DB_TYPE $SINK_DB_TYPE
    if [ "$?" != 0 ]; then echo "Testing aborted"; exit 1 ; fi
done
