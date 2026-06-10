#!/usr/bin/env python3
"""Trigger AWS Secrets Manager rotation, wait, smoke-test, rollback on fail.

This script does NOT create users or rotate passwords itself. The actual
zero-downtime "alternating users" logic lives in the AWS-managed rotation
Lambda configured on the secret (see Terraform module rds_secret_rotation).

Order of operations:
    1. RotateSecret API call -> AWS invokes the rotation Lambda
    2. Lambda alters the OTHER DB user, updates the secret, swaps labels
    3. This script polls until AWSCURRENT moves to a new version
    4. Smoke-test the application health endpoint
    5. On failure, swap AWSCURRENT back to the previous version
       (the previous DB user still has its old password -> safe rollback)

Usage:
    rotate_rds_secret.py \
        --secret-id rds/ekyc/uat \
        --region ap-southeast-1 \
        --health-url https://uat.ekyc.internal.example.com/healthz
"""
from __future__ import annotations

import argparse
import sys
import time
import urllib.request

import boto3


def current_version(client, secret_id: str) -> str:
    desc = client.describe_secret(SecretId=secret_id)
    if not desc.get("RotationEnabled"):
        raise RuntimeError(f"{secret_id}: rotation is not enabled")
    for version_id, stages in desc["VersionIdsToStages"].items():
        if "AWSCURRENT" in stages:
            return version_id
    raise RuntimeError(f"{secret_id}: no AWSCURRENT version found")


def wait_new_version(client, secret_id: str, previous: str,
                     timeout: int = 300, interval: int = 10) -> str:
    deadline = time.time() + timeout
    while time.time() < deadline:
        now = current_version(client, secret_id)
        if now != previous:
            return now
        print(f"... still rotating (current={previous})")
        time.sleep(interval)
    raise TimeoutError("Rotation did not complete in time")


def smoke_test(url: str, timeout: int = 120, interval: int = 10) -> None:
    deadline = time.time() + timeout
    last_err = None
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=5) as resp:
                if resp.status == 200:
                    print(f"Health OK: {url}")
                    return
                last_err = f"HTTP {resp.status}"
        except Exception as e:  # noqa: BLE001
            last_err = str(e)
        print(f"... health failing ({last_err}), retrying")
        time.sleep(interval)
    raise RuntimeError(f"Health check failed: {last_err}")


def rollback(client, secret_id: str, previous: str, new: str) -> None:
    print(f"Rolling back AWSCURRENT to {previous}")
    client.update_secret_version_stage(
        SecretId=secret_id,
        VersionStage="AWSCURRENT",
        MoveToVersionId=previous,
        RemoveFromVersionId=new,
    )


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--secret-id",  required=True)
    p.add_argument("--region",     required=True)
    p.add_argument("--health-url", required=True)
    args = p.parse_args()

    client = boto3.client("secretsmanager", region_name=args.region)

    previous = current_version(client, args.secret_id)
    print(f"Previous version: {previous}")

    print("Triggering rotation...")
    client.rotate_secret(SecretId=args.secret_id)

    new = wait_new_version(client, args.secret_id, previous)
    print(f"New version:      {new}")

    try:
        smoke_test(args.health_url)
    except Exception as e:  # noqa: BLE001
        print(f"Smoke test failed: {e}")
        rollback(client, args.secret_id, previous, new)
        return 1

    print("Rotation successful")
    return 0


if __name__ == "__main__":
    sys.exit(main())
