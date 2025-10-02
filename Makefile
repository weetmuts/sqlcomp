HOME:=$(shell echo $$HOME)
M2REPO:=$(HOME)/.m2/repository
GIT_ROOT=$(CURDIR)
JAVA:=$(shell which java)
JAVA_HOME:=$(shell cd $(dir $(JAVA))/..; pwd)
TARGET:=$(GIT_ROOT)/target
PROJECT_DEPS:=$(GIT_ROOT)/target/dependency

VERSION=1.0-SNAPSHOT

$(shell mkdir -p $(PROJECT_DEPS))

VERSION_FILE:=src/main/resources/version.txt
$(shell echo $(VERSION) > $(VERSION_FILE))

AT=@
DROP_ROOT=$(subst $(GIT_ROOT)/,./,$1)

mvn: $(TARGET)/sqlcomp

javac: $(TARGET)/javac.timestamp

# Locate the jar dependencies automatically downloaded by maven.
JARS:=$(shell find $(PROJECT_DEPS)/ -name "*.jar" | tr '\n' ':')
$(file >target/sqlcomp.sh,java -cp $(JARS):$(TARGET)/classes org.ammunde.SQLComp.Main $$*)
$(shell chmod a+x target/sqlcomp.sh)

# Find all java sources.
SOURCES:=$(shell find src/main/java/ -type f -name "*.java")

$(TARGET)/sqlcomp: $(PROJECT_DEPS)/updated.timestamp $(SOURCES)
	@echo Compiling using maven
	$(AT)mvn -B -q package 2>&1 | grep -v "WARNING: A restrict" | grep -v "System::load" | grep -v "Use --enable-native" | grep -v "WARNING: Restr" | grep -v Unsafe
	$(AT)cp $(TARGET)/SQLComp-1.0-SNAPSHOT.jar $(TARGET)/sqlcomp

$(TARGET)/javac.timestamp: $(SOURCES)
	@echo Compiling javac
	$(AT)javac -classpath $(JARS) $(SOURCES)
	$(AT)touch $@

# The mvn tree command generates lines like this:
# [INFO] \- org.jsoup:jsoup:jar:1.11.3:compile
# from this info build the path:
# ~/.m2/repository/org/jsoup/jsoup/1.11.3/jsoup-1.11.3.jar
$(PROJECT_DEPS)/updated.timestamp: pom.xml
	@rm -rf $(PROJECT_DEPS)
	@mkdir -p $(PROJECT_DEPS)
	@echo Storing java dependencies into $(PROJECT_DEPS)
	$(AT)mvn dependency:copy-dependencies
	@touch $(PROJECT_DEPS)/updated.timestamp

dump_databases:
	pg_dump --clean --schema-only fromcomp > fromcomp.sql
	pg_dump --clean --schema-only tocomp > tocomp.sql

recreate_database:
	psql -d fromcomp -f fromcomp.sql
	psql -d tocomp -f tocomp.sql

run:
	@java -cp $(JARS):target/classes org.ammunde.SQLComp.Main $(ARGS)

test:
	$(AT)java -cp $(JARS):target/classes org.ammunde.SQLComp.TestInternals
	$(AT)./tests/test.sh

clean:
	@echo -n "Removing target directory..."
	@rm -rf target
	@echo "done."

.PHONY: clean

MAKEFLAGS += --no-builtin-rules
