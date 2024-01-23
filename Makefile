.PHONY:build run up down

DOCKER_TAG := latest
build: ## Build docker image to deploy
	cp org.eclipse.paho.sample.mqttv5app/target/org.eclipse.paho.sample.mqttv5app.jar build/
	docker build -t benchmarker:${DOCKER_TAG} -f build/Dockerfile  build/

up:
	docker compose up -d

down:
	docker compose down

run build up:

