.PHONY: build run up down login-aws create-aws delete-aws push-aws register-aws

DOCKER_IMAGE := benchmarker
DOCKER_TAG := latest

AWS_ECR_REGISTRY := 924899176789.dkr.ecr.ap-northeast-1.amazonaws.com

build: ## Build docker image to deploy
	cp org.eclipse.paho.sample.mqttv5app/target/org.eclipse.paho.sample.mqttv5app.jar build/
	docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} -f build/Dockerfile  build/

up:
	docker compose up -d

down:
	docker compose down

run build up:


# AWS Operation
login-aws:
	aws ecr get-login-password --region ap-northeast-1 | docker login --username AWS --password-stdin ${AWS_ECR_REGISTRY}

create-aws:
	aws ecr create-repository --repository-name ${DOCKER_IMAGE} --region ap-northeast-1

delete-aws:
	aws ecr delete-repository --repository-name ${DOCKER_IMAGE} --region ap-northeast-1 --force

push-aws: build
	docker tag ${DOCKER_IMAGE}:${DOCKER_TAG} ${AWS_ECR_REGISTRY}/${DOCKER_IMAGE}:${DOCKER_TAG}
	docker push ${AWS_ECR_REGISTRY}/${DOCKER_IMAGE}:${DOCKER_TAG}

register-aws:
	aws ecs register-task-definition --cli-input-json file://aws/publisher-task.json --no-cli-pager
	aws ecs register-task-definition --cli-input-json file://aws/high-subscriber-task.json --no-cli-pager
	aws ecs register-task-definition --cli-input-json file://aws/low-subscriber-task.json --no-cli-pager
