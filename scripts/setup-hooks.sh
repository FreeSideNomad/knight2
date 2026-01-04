#!/bin/bash
#
# Setup script to install git hooks
# Run this after cloning the repository
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
HOOKS_SOURCE="$PROJECT_ROOT/.githooks"
HOOKS_TARGET="$PROJECT_ROOT/.git/hooks"

echo "🔧 Setting up git hooks..."

# Check if .githooks directory exists
if [ ! -d "$HOOKS_SOURCE" ]; then
    echo "❌ Error: .githooks directory not found at $HOOKS_SOURCE"
    exit 1
fi

# Install each hook
for hook in pre-commit pre-push commit-msg; do
    if [ -f "$HOOKS_SOURCE/$hook" ]; then
        cp "$HOOKS_SOURCE/$hook" "$HOOKS_TARGET/$hook"
        chmod +x "$HOOKS_TARGET/$hook"
        echo "  ✅ Installed $hook"
    fi
done

echo ""
echo "✅ Git hooks installed successfully!"
echo ""
echo "Hooks installed:"
echo "  - pre-commit: Compiles changed modules before commit"
echo "  - pre-push: Runs full test suite with coverage checks before push"
echo "  - commit-msg: Enforces conventional commit format"
echo ""
echo "To skip hooks temporarily, use:"
echo "  git commit --no-verify"
echo "  git push --no-verify"
echo ""
