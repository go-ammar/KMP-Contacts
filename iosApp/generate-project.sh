#!/usr/bin/env bash
# Generates iosApp.xcodeproj from project.yml.
set -euo pipefail

if ! command -v xcodegen >/dev/null 2>&1; then
    echo "xcodegen is not installed. Run: brew install xcodegen" >&2
    exit 1
fi

cd "$(dirname "$0")"
xcodegen generate
echo "Generated iosApp.xcodeproj - open it in Xcode and run on a simulator."
