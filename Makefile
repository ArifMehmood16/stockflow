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
MAVEN = $(JAVA) tools/MavenBuild.java
.PHONY: help run preview stop api api-stop api-smoke api-build setup db-init db-import db-status data-fetch test test-java test-api test-api-integration test-integration verify doctor run-docker down
help:
	@echo "StockFlow — Java tools and interactive systems design"
	@echo "  make run               Set up data, build/start real Java API + model UI"
	@echo "  make preview           Serve the browser model without database setup"
	@echo "  make stop              Stop the registered Java UI and API; keep PostgreSQL"
	@echo "  make api               Build/start only the read-only inventory API"
	@echo "  make api-stop          Stop only the registered inventory API"
	@echo "  make api-smoke         Compare a real stock response with the local database"
	@echo "  make setup             Create schema; import up to DATASET_ROWS unique products"
	@echo "  make db-status         Show actual imported rows and bucket distribution"
	@echo "  make test              Cached/offline Java tooling, API and frontend tests"
	@echo "  make test-api-integration  Stock API adapter tests in an owned temporary DB"
	@echo "  make test-integration  JDBC tests in an owned temporary local database"
	@echo "  make verify            Tests, frontend syntax and documentation links"
	@echo "  make run-docker        Isolated PostgreSQL + Java seed + API + UI"
	@echo "  make down              Stop Docker services; preserve data volumes"
run: setup api-build
	$(LAB) run
api: setup api-build
	$(LAB) api
api-stop:
	$(LAB) api-stop
api-smoke:
	$(LAB) api-smoke
api-build:
	$(MAVEN) -q package
test-api:
	$(MAVEN) -q test
test-api-integration:
	$(LAB) test-api-integration
preview:
	$(LAB) preview
stop:
	$(LAB) stop
setup db-init db-import db-status data-fetch doctor:
	$(LAB) $@
test-java:
	$(LAB) test
test: test-java test-api
	node --test tests/model.test.mjs tests/hover.test.mjs
test-integration:
	$(LAB) test-postgres
verify: test
	node --check prototype/app.mjs
	node --check prototype/model.mjs
	node --check prototype/hover.mjs
	$(LAB) check-docs
run-docker:
	docker compose up --build -d
down:
	docker compose down
