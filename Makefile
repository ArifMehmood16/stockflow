.DEFAULT_GOAL := help
# Prefer an explicit JAVA_HOME, then the optional project-local JDK, then PATH.
ifneq ($(JAVA_HOME),)
JAVA := $(JAVA_HOME)/bin/java
else ifneq ($(wildcard .lab/jdk/Contents/Home/bin/java),)
JAVA := .lab/jdk/Contents/Home/bin/java
else
JAVA := java
endif
LAB = $(JAVA) tools/Build.java
.PHONY: help run preview stop setup db-init db-import db-status data-fetch test test-java test-integration verify doctor run-docker down
help:
	@echo "StockFlow — Java tools and interactive systems design"
	@echo "  make run               Set up local PostgreSQL + import data, then serve UI"
	@echo "  make preview           Serve the browser model without database setup"
	@echo "  make stop              Stop only the registered Java preview"
	@echo "  make setup             Create schema; import up to DATASET_ROWS unique products"
	@echo "  make db-status         Show actual imported rows and bucket distribution"
	@echo "  make test              Offline Java tooling + frontend model tests"
	@echo "  make test-integration  JDBC tests in an owned temporary local database"
	@echo "  make verify            Tests, frontend syntax and documentation links"
	@echo "  make run-docker        Isolated PostgreSQL + Java seed + Java UI server"
	@echo "  make down              Stop Docker services; preserve data volumes"
run: setup
	$(LAB) run
preview:
	$(LAB) preview
stop:
	$(LAB) stop
setup db-init db-import db-status data-fetch doctor:
	$(LAB) $@
test-java:
	$(LAB) test
test: test-java
	node --test tests/model.test.mjs
test-integration:
	$(LAB) test-postgres
verify: test
	node --check prototype/app.mjs
	node --check prototype/model.mjs
	$(LAB) check-docs
run-docker:
	docker compose up --build -d
down:
	docker compose down
