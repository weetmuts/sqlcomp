# sqlcomp - Copyright 2025 Fredrik Öhrström (gpl-3.0-or-later)

cat <<EOF | $SOURCE_DB_CMD >> $STDERR 2>&1
drop database if exists test1_from;
create database test1_from;
EOF

cat <<EOF | $SOURCE_DB_CMD $USE_SOURCE_DB >> $STDERR 2>&1
create table alfa (lekey bigint not null, name text NOT NULL, age int NOT NULL DEFAULT 17, primary key (lekey));
create table beta (lekey bigint not null, name varchar(16) DEFAULT 'howdy', speed int NOT NULL DEFAULT 999, primary key (lekey));
EOF

cat <<EOF | $SINK_DB_CMD >> $STDERR 2>&1
drop database if exists test1_to;
create database test1_to;
EOF

cat <<EOF | $SINK_DB_CMD $USE_SINK_DB >> $STDERR 2>&1
create table alfa (lekey bigint not null, name text NOT NULL, primary key (lekey));
create table droopme (lekey bigint not null, speed int NOT NULL DEFAULT 123, primary key (lekey));
EOF
