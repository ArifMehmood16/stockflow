.DEFAULT_GOAL := help
.PHONY: help run test verify doctor run-docker down
help:
	@echo "StockFlow design prototype"
	@echo "  make run         Start local preview (Node 24, no Docker)"
	@echo "  make test        Run deterministic model tests"
	@echo "  make verify      Tests, syntax and local doc links"
	@echo "  make doctor      Report available tools"
	@echo "  make run-docker  Start the prototype via Docker"
	@echo "  make down        Stop Docker preview (preserve data)"
run:
	node scripts/serve.mjs
test:
	node --test tests/*.test.mjs
verify: test
	node --check prototype/app.mjs
	node --check prototype/model.mjs
	node --check scripts/serve.mjs
	node --check scripts/check-docs.mjs
	node --check scripts/doctor.mjs
	node scripts/check-docs.mjs
doctor:
	node scripts/doctor.mjs
run-docker:
	docker compose up --build -d
down:
	docker compose down
