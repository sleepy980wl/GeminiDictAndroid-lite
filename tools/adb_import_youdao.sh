#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ $# -lt 1 ]; then
  echo "用法:"
  echo "  ./adb_import_youdao.sh /path/to/youdao_wordbook.xml"
  echo "  ./adb_import_youdao.sh --scan-phone"
  exit 1
fi
if [ "$1" = "--scan-phone" ]; then
  python3 "$SCRIPT_DIR/adb_import_youdao.py" --scan-phone
else
  python3 "$SCRIPT_DIR/adb_import_youdao.py" --file "$1"
fi
