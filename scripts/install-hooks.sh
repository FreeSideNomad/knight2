#!/bin/bash
#
# Install git hooks for Knight platform
#
# Usage: ./scripts/install-hooks.sh
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOOKS_DIR="$SCRIPT_DIR/../.git/hooks"

echo "Installing git hooks..."

# Install pre-commit hook
cp "$SCRIPT_DIR/pre-commit" "$HOOKS_DIR/pre-commit"
chmod +x "$HOOKS_DIR/pre-commit"
echo "✅ Installed pre-commit hook"

echo ""
echo "Git hooks installed successfully!"
echo ""
echo "The pre-commit hook will enforce:"
echo "  - 80% line coverage (JaCoCo)"
echo "  - 80% branch coverage (JaCoCo)"
echo ""
echo "To skip the hook temporarily: git commit --no-verify"
