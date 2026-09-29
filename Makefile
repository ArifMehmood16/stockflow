.DEFAULT_GOAL := help
.PHONY: help run preview stop test lint verify run-docker down
help:
	@echo "Distributed Systems Simulator — browser-only learning tool"
	@echo "make run / make stop   Serve or stop static files (Node.js 24+)"
	@echo "make verify            Short simulation tests and syntax checks"
	@echo "make run-docker / down Optional static-site container"
run preview:
	node scripts/serve.mjs
stop:
	node scripts/serve.mjs stop
test:
	node --test tests/*.test.mjs
lint:
	node --check prototype/app.mjs
	node --check prototype/model.mjs
	node --check prototype/hover.mjs
	node --check prototype/topology.mjs
	node --check scripts/serve.mjs
	git diff --check
verify: test lint
run-docker:
	docker compose up --build -d
down:
	docker compose down
