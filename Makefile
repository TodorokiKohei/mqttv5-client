.PHONY: build run up down

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
START_PUB_TASK_DEFINITION_ARN = $(shell aws ecs list-task-definitions --family-prefix "start-publisher" --status ACTIVE --query "taskDefinitionArns[-1]" --output text)
PUB_COUNT = $(shell cat aws/parameters.json | jq -r '.pubCount')
LOW_SUB_COUNT = $(shell cat aws/parameters.json | jq -r '.lowSubCount')
HIGH_SUB_COUNT = $(shell cat aws/parameters.json | jq -r '.highSubCount')


# 実行環境パラメータ
SSH_USER := ec2-user
SSH_KEY := ${HOME}/.ssh/todoroki-aws-lab.pem
OUTPUT_DIR := ${HOME}/mqttv5-aws/input


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
	aws ecs register-task-definition --cli-input-json file://aws/start-publisher.json --no-cli-pager

start-tasks:
	@cat /dev/null > /tmp/task_arn.txt
	@cat /dev/null > /tmp/failure_task.txt
	for i in $(shell seq 1 $$(( ${PUB_COUNT} / 10 ))); do \
		aws ecs run-task --cluster ${ECS_CLUSTER_NAME} --task-definition ${PUB_TASK_DEFINITION_ARN} \
			--network-configuration "awsvpcConfiguration={subnets=${SUBNET_ID},securityGroups=${SECURITY_GROUP_ID},assignPublicIp=ENABLED}" \
			--count 10 \
			--overrides "file://aws/publisher-overrides.json" \
			--launch-type FARGATE --no-cli-pager > /tmp/run_task.json ; \
		cat /tmp/run_task.json | jq -r '.tasks[].taskArn' >> /tmp/task_arn.txt ; \
		cat /tmp/run_task.json | jq -r '.failures | length' >> /tmp/failure_task.txt ; \
		if [ $$(( $$i % 10 )) -eq 0 ] ; then \
			echo "Waiting for tasks to be running..." ; \
			while true ; do \
				aws ecs describe-tasks --cluster ${ECS_CLUSTER_NAME} --tasks $$(cat /tmp/task_arn.txt | tr "\n" " ") --query "tasks[*].[lastStatus]" --output text --no-cli-pager | \
				grep -qv "RUNNING" || break ; \
				sleep 10 ; \
			done ; \
			cat /dev/null > /tmp/task_arn.txt ; \
		else \
			sleep 10 ; \
		fi ; \
	done
	if [ $$(( ${PUB_COUNT} % 10 )) -ne 0 ] ; then \
		aws ecs run-task --cluster ${ECS_CLUSTER_NAME} --task-definition ${PUB_TASK_DEFINITION_ARN} \
				--network-configuration "awsvpcConfiguration={subnets=${SUBNET_ID},securityGroups=${SECURITY_GROUP_ID},assignPublicIp=ENABLED}" \
				--count $$(( ${PUB_COUNT} % 10 )) \
				--overrides "file://aws/publisher-overrides.json" \
				--launch-type FARGATE --no-cli-pager > /tmp/run_task.json ; \
		cat /tmp/run_task.json | jq -r '.tasks[].taskArn' >> /tmp/task_arn.txt ; \
		cat /tmp/run_task.json | jq -r '.failures | length' >> /tmp/failure_task.txt ; \
		@while true ; do \
			aws ecs describe-tasks --cluster ${ECS_CLUSTER_NAME} --tasks $$(cat /tmp/task_arn.txt | tr "\n" " ") --query "tasks[*].[lastStatus]" --output text --no-cli-pager | \
			grep -qv "RUNNING" || break ; \
			sleep 10 ; \
		done
	fi
	@echo "Failed tasks: $$(cat /tmp/failure_task.txt | awk '{s+=$$1} END {print s}')"

	aws ecs run-task --cluster ${ECS_CLUSTER_NAME} --task-definition ${HIGH_SUB_TASK_DEFINITION_ARN} \
    		--network-configuration "awsvpcConfiguration={subnets=${SUBNET_ID},securityGroups=${SECURITY_GROUP_ID}}" \
    		--count ${HIGH_SUB_COUNT} \
    		--overrides "file://aws/high-subscriber-overrides.json" \
    		--launch-type EC2 --no-cli-pager | \
			jq -r '.tasks[].taskArn' > /tmp/sub_task_arn.txt
	aws ecs run-task --cluster ${ECS_CLUSTER_NAME} --task-definition ${LOW_SUB_TASK_DEFINITION_ARN} \
    		--network-configuration "awsvpcConfiguration={subnets=${SUBNET_ID},securityGroups=${SECURITY_GROUP_ID}}" \
    		--count ${LOW_SUB_COUNT} \
    		--overrides "file://aws/low-subscriber-overrides.json" \
    		--launch-type EC2 --no-cli-pager | \
			jq -r '.tasks[].taskArn' >> /tmp/sub_task_arn.txt
	@echo "Waiting for tasks to be running..."
	@while true ; do \
		aws ecs describe-tasks --cluster ${ECS_CLUSTER_NAME} --tasks $$(cat /tmp/sub_task_arn.txt | tr "\n" " ") --query "tasks[*].[lastStatus]" --output text --no-cli-pager | \
		grep -qv "RUNNING" || break ; \
		sleep 10 ; \
	done

add-missing-tasks:
	$(eval FAILURE_TASK := $(shell cat /tmp/failure_task.txt | awk '{s+=$$1} END {print s}'))
	@echo "Adding missing tasks... (Failed tasks: ${FAILURE_TASK})"
	@cat /dev/null > /tmp/failure_task.txt
	for i in $(shell seq 1 $$(( ${FAILURE_TASK} / 10 ))); do \
		aws ecs run-task --cluster ${ECS_CLUSTER_NAME} --task-definition ${PUB_TASK_DEFINITION_ARN} \
				--network-configuration "awsvpcConfiguration={subnets=${SUBNET_ID},securityGroups=${SECURITY_GROUP_ID},assignPublicIp=ENABLED}" \
				--count 10 \
				--overrides "file://aws/publisher-overrides.json" \
				--launch-type FARGATE --no-cli-pager > /tmp/run_task.json ; \
		cat /tmp/run_task.json | jq -r '.tasks[].taskArn' >> /tmp/task_arn.txt ; \
		cat /tmp/run_task.json | jq -r '.failures | length' >> /tmp/failure_task.txt ; \
		if [ $$(( $$i % 10 )) -eq 0 ] ; then \
			echo "Waiting for tasks to be running..." ; \
			while true ; do \
				aws ecs describe-tasks --cluster ${ECS_CLUSTER_NAME} --tasks $$(cat /tmp/task_arn.txt | tr "\n" " ") --query "tasks[*].[lastStatus]" --output text --no-cli-pager | \
				grep -qv "RUNNING" || break ; \
				sleep 10 ; \
			done ; \
			cat /dev/null > /tmp/task_arn.txt ; \
		else \
			sleep 10 ; \
		fi ; \
	done
	if [ $$(( ${FAILURE_TASK} % 10 )) -ne 0 ] ; then \
		aws ecs run-task --cluster ${ECS_CLUSTER_NAME} --task-definition ${PUB_TASK_DEFINITION_ARN} \
				--network-configuration "awsvpcConfiguration={subnets=${SUBNET_ID},securityGroups=${SECURITY_GROUP_ID},assignPublicIp=ENABLED}" \
				--count $$(( ${FAILURE_TASK} % 10 )) \
				--overrides "file://aws/publisher-overrides.json" \
				--launch-type FARGATE --no-cli-pager > /tmp/run_task.json ; \
		cat /tmp/run_task.json | jq -r '.tasks[].taskArn' >> /tmp/task_arn.txt ; \
		cat /tmp/run_task.json | jq -r '.failures | length' >> /tmp/failure_task.txt ; \
	fi
	@while true ; do \
		aws ecs describe-tasks --cluster ${ECS_CLUSTER_NAME} --tasks $$(cat /tmp/task_arn.txt | tr "\n" " ") --query "tasks[*].[lastStatus]" --output text --no-cli-pager | \
		grep -qv "RUNNING" || break ; \
		sleep 10 ; \
	done
	@echo "Failed tasks: $$(cat /tmp/failure_task.txt | awk '{s+=$$1} END {print s}')"

start-measurement:
	aws ecs run-task --cluster ${ECS_CLUSTER_NAME} --task-definition ${START_PUB_TASK_DEFINITION_ARN} \
			--network-configuration "awsvpcConfiguration={subnets=${SUBNET_ID},securityGroups=${SECURITY_GROUP_ID},assignPublicIp=ENABLED}" \
			--count 1 \
			--launch-type FARGATE --no-cli-pager
	@echo "Waiting for measurement to be stopped..."
	@while true ; do \
		aws ecs describe-tasks --cluster ${ECS_CLUSTER_NAME} --tasks $$(cat /tmp/sub_task_arn.txt | tr "\n" " ") --query "tasks[*].[lastStatus]" --output text --no-cli-pager | \
		grep -qv "DEPROVISIONING" || break ; \
		sleep 10 ; \
	done
	@$(MAKE) get-task-results

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
	 	ssh -i ${SSH_KEY} -n ${SSH_USER}@$$ip "sudo rm -rf /tmp/results/*.csv" || \
		rm -rf  ${OUTPUT_DIR}/${TIMESTAMP}/$$name ;\
	done
	@cp -r aws/* ${OUTPUT_DIR}/${TIMESTAMP}/

