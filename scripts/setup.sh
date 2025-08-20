#!/usr/bin/env bash
set -euo pipefail

# Resolve directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Log file path (in the same folder as setup.sh)
LOG_FILE_PATH="$SCRIPT_DIR/../logs/"
LOG_FILE="$LOG_FILE_PATH/setup_output.log"

# Create log file directory if it doesn't exist
mkdir -p "$LOG_FILE_PATH"

# Redirect ALL output (stdout + stderr) to log file and console
exec > >(tee "$LOG_FILE") 2>&1

echo "===== Setup started at $(date) ====="

OS="$(uname -s)"
echo "Detected OS: $OS"

# Print a new line for formatting
echo ""


case "$OS" in
    Darwin*)
        . "$SCRIPT_DIR/setup_darwin.sh"
        ;;
    Linux*)
        . "$SCRIPT_DIR/setup_linux.sh"
        ;;
    *)
        echo "Unsupported OS: $OS"
        exit 1
        ;;
esac

echo "===== Setup finished at $(date) ====="

# TODO: Currently this will only be printed when the script
#       completes successfully. Make this so that even if the script
#       fails, this gets logged.
echo "Log file: ${LOG_FILE}" > /dev/tty