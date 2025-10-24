# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

echo "Creating DB test_from in $SOURCE_DB_TYPE"
cat <<EOF | $SOURCE_DB_CMD >> $STDERR 2>&1
drop database if exists test_from;
create database test_from;
EOF

echo "Creating alfa,beta tables in test_from"
cat <<EOF | $SOURCE_DB_CMD $USE_SOURCE_DB >> $STDERR 2>&1
create table alfa (lekey bigint not null, name text NOT NULL, age int NOT NULL DEFAULT 17, primary key (lekey));
create table beta (lekey bigint not null, name varchar(16) DEFAULT 'howdy', speed int NOT NULL DEFAULT 999, primary key (lekey));
EOF

echo "Creating DB test_to in $SINK_DB_TYPE"
cat <<EOF | $SINK_DB_CMD >> $STDERR 2>&1
drop database if exists test_to;
create database test_to;
EOF

echo "Creating alfa,beta tables in test_from"
cat <<EOF | $SINK_DB_CMD $USE_SINK_DB >> $STDERR 2>&1
create table alfa (lekey bigint not null, name text NOT NULL, primary key (lekey));
create table droopme (lekey bigint not null, speed int NOT NULL DEFAULT 123, primary key (lekey));
EOF
