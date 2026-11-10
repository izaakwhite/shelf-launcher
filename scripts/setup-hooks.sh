#!/usr/bin/env bash
# Point git at the repo's hooks (.githooks/). Run once after cloning.
cd "$(dirname "$0")/.." && git config core.hooksPath .githooks && chmod +x .githooks/* && echo "Hooks enabled: lint + unit tests run before each commit."
