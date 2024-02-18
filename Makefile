.PHONY: build run up down login-aws create-aws delete-aws push-aws register-aws

# コンテナパラメータ
DOCKER_IMAGE := benchmarker
DOCKER_TAG := latest

# AWS パラメータ
ACCOUNT := 924899176789
VPC_ID := vpc-022dca84fb15b1536
SUBNET_ID := subnet-0ef955c747b1ae558
SECURITY_GROUP_ID := sg-0e64601b05b91d239

# AWS ECS パラメータ
ECS_CLUSTER_NAME := IoTSimulator
PUB_TASK_DEFINITION_ARN = $(shell aws ecs list-task-definitions --family-prefix "publisher" --status ACTIVE --query "taskDefinitionArns[-1]" --output text)
HIGH_SUB_TASK_DEFINITION_ARN = $(shell aws ecs list-task-definitions --family-prefix "high-subscriber" --status ACTIVE --query "taskDefinitionArns[-1]" --output text)
LOW_SUB_TASK_DEFINITION_ARN = $(shell aws ecs list-task-definitions --family-prefix "low-subscriber" --status ACTIVE --query "taskDefinitionArns[-1]" --output text)
PUB_COUNT := 5
SUB_COUNT := 2


# 実行環境パラメータ
SSH_USER := ec2-user
SSH_KEY := ${HOME}/.ssh/todoroki-aws-lab.pem
OUTPUT_DIR := ${HOME}/mqttv5-aws

# Docker
build:
	cp org.eclipse.paho.sample.mqttv5app/target/org.eclipse.paho.sample.mqttv5app.jar build/
	docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} -f build/Dockerfile  build/

up:
	docker compose up -d

down:
	docker compose down

run build up:


# AWS ECR
login-ecr:
	aws ecr get-login-password --region ap-northeast-1 | docker login --username AWS --password-stdin ${ACCOUNT}.dkr.ecr.ap-northeast-1.amazonaws.com

create-repo:
	aws ecr create-repository --repository-name ${DOCKER_IMAGE} --region ap-northeast-1

delete-repo:
	aws ecr delete-repository --repository-name ${DOCKER_IMAGE} --region ap-northeast-1 --force

push: build login-ecr
	docker tag ${DOCKER_IMAGE}:${DOCKER_TAG} ${ACCOUNT}.dkr.ecr.ap-northeast-1.amazonaws.com/${DOCKER_IMAGE}:${DOCKER_TAG}
	docker push ${ACCOUNT}.dkr.ecr.ap-northeast-1.amazonaws.com/${DOCKER_IMAGE}:${DOCKER_TAG}

# AWS ECS
create-cluster:
	aws ecs create-cluster --cluster-name ${ECS_CLUSTER_NAME} --no-cli-pager

register-task:
	aws ecs register-task-definition --cli-input-json file://aws/publisher-task.json --no-cli-pager
	aws ecs register-task-definition --cli-input-json file://aws/high-subscriber-task.json --no-cli-pager
	aws ecs register-task-definition --cli-input-json file://aws/low-subscriber-task.json --no-cli-pager

start-tasks:
	aws ecs run-task --cluster ${ECS_CLUSTER_NAME} --task-definition ${PUB_TASK_DEFINITION_ARN} \
		--network-configuration "awsvpcConfiguration={subnets=${SUBNET_ID},securityGroups=${SECURITY_GROUP_ID},assignPublicIp=ENABLED}" \
		--count ${PUB_COUNT} \
		--overrides "file://aws/publisher-overrides.json" \
		--launch-type FARGATE --no-cli-pager | \
		jq -r '.tasks[].taskArn' > /tmp/task_arn.txt
	aws ecs run-task --cluster ${ECS_CLUSTER_NAME} --task-definition ${HIGH_SUB_TASK_DEFINITION_ARN} \
    		--network-configuration "awsvpcConfiguration={subnets=${SUBNET_ID},securityGroups=${SECURITY_GROUP_ID}}" \
    		--count $$((${SUB_COUNT}/2)) \
    		--overrides "file://aws/high-subscriber-overrides.json" \
    		--launch-type EC2 --no-cli-pager | \
			jq -r '.tasks[].taskArn' >> /tmp/task_arn.txt
	aws ecs run-task --cluster ${ECS_CLUSTER_NAME} --task-definition ${LOW_SUB_TASK_DEFINITION_ARN} \
    		--network-configuration "awsvpcConfiguration={subnets=${SUBNET_ID},securityGroups=${SECURITY_GROUP_ID}}" \
    		--count $$((${SUB_COUNT}/2)) \
    		--overrides "file://aws/low-subscriber-overrides.json" \
    		--launch-type EC2 --no-cli-pager | \
			jq -r '.tasks[].taskArn' >> /tmp/task_arn.txt
	cat /tmp/task_arn.txt

get-task-results:
	$(eval TIMESTAMP := $(shell date "+%Y%m%d_%H%M%S"))
	@aws ec2 describe-instances \
		--query "Reservations[*].Instances[?State.Name=='running'].{IP:PublicIpAddress,Name:Tags[?Key=='Name'].Value | [0]}" \
		--filters "Name=tag:Owner,Values=todoroki" "Name=tag:Name,Values=*Subscriber*" "Name=instance-state-name,Values=running" \
		--output text | \
	while read ip name ; do \
	 	echo "IP: $$ip, Name: $$name" ; \
		mkdir -p ${OUTPUT_DIR}/${TIMESTAMP}/$$name && \
	 	scp -i ${SSH_KEY} -o StrictHostKeyChecking=no -r ${SSH_USER}@$$ip:/tmp/results/*.csv ${OUTPUT_DIR}/${TIMESTAMP}/$$name && \
	 	ssh -i ${SSH_KEY} -n ${SSH_USER}@$$ip "sudo rm -rf /tmp/results/*.csv" ; \
	done

