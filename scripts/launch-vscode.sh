#!/usr/bin/env bash
# launch-vscode.sh — Start VS Code as a writable app platform.
# Opens a new window with a blank untitled file, ready to accept code.
# No file path is required.
#
# Usage:
#   ./scripts/launch-vscode.sh                  # blank untitled file
#   echo 'print("hi")' | ./scripts/launch-vscode.sh   # pre-filled content from stdin

set -eu

if ! command -v code &>/dev/null; then
    echo "Error: 'code' command not found."
    echo "Install VS Code and make sure 'code' is in your PATH:"
    echo "  https://code.visualstudio.com/download"
    exit 1
fi

if [ ! -t 0 ]; then
    # Stdin has piped content — open it as an untitled file in VS Code.
    code --new-window -
else
    # No piped input — open VS Code with an empty untitled file.
    printf '' | code --new-window -
fi

