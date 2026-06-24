#!/usr/bin/env just --justfile

# Architecture
ARCH := "linux/arm64"
ACT_IMAGE := "catthehacker/ubuntu:act-latest"

docker:
    docker compose up -d
# Register Debezium connectors (run once after docker compose up)
debezium:
    docker compose --profile setup up --wait setup-connector setup-job-outbox-connector

# Run full CI locally (native ARM64)
ci:
    act pull_request --container-architecture {{ARCH}} -P ubuntu-latest={{ACT_IMAGE}}

# Run a specific workflow
ci-workflow workflow:
    act -W .github/workflows/{{workflow}}.yml \
        --container-architecture {{ARCH}} \
        -P ubuntu-latest={{ACT_IMAGE}}

# Run a specific job
ci-job job:
    act pull_request -j {{job}} \
        --container-architecture {{ARCH}} \
        -P ubuntu-latest={{ACT_IMAGE}}

# Dry run
ci-dry:
    act pull_request -n \
        --container-architecture {{ARCH}} \
        -P ubuntu-latest={{ACT_IMAGE}}

# Run with secrets
ci-secrets:
    act pull_request \
        --container-architecture {{ARCH}} \
        -P ubuntu-latest={{ACT_IMAGE}} \
        --secret-file .secrets