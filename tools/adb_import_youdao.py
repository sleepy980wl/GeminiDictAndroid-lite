#!/usr/bin/env python3
"""
无 Root 手机通过 ADB 导入有道词典「我的单词本」到 Gemini词典 的辅助脚本。
支持两种模式：
1. 电脑端直接指定有道导出的 .xml / .txt / .csv / .db 文件推送到手机并自动唤起 App 导入：
   python3 adb_import_youdao.py --file my_youdao_wordbook.xml
2. 自动扫描手机外部存储中可访问的有道导出目录（/sdcard/Youdao、/sdcard/Download 等），
   找到最新的单词本文件后直接唤起 App 导入：
   python3 adb_import_youdao.py --scan-phone
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path

APP_PACKAGE = "com.geminidict.app"
APP_IMPORT_ACTION = "com.geminidict.app.ACTION_IMPORT_YOUDAO"
DEVICE_IMPORT_DIR = f"/sdcard/Android/data/{APP_PACKAGE}/files/import"
CANDIDATE_PHONE_DIRS = [
    "/sdcard/Youdao",
    "/sdcard/Download",
    "/sdcard/Documents",
    "/sdcard/Android/data/com.youdao.dict/files",
]
SUPPORTED_EXTS = (".xml", ".txt", ".csv", ".db")


def run_adb(args, check=True):
    cmd = ["adb"] + args
    proc = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and proc.returncode != 0:
        raise RuntimeError(f"ADB 命令执行失败: {' '.join(cmd)}\n{proc.stderr.strip()}")
    return proc


def ensure_device_connected():
    proc = run_adb(["devices"])
    lines = [ln.strip() for ln in proc.stdout.splitlines()[1:] if ln.strip()]
    online = [ln for ln in lines if ln.endswith("\tdevice") or " device" in ln]
    if not online:
        raise RuntimeError(
            "未检测到已授权的 Android 设备。\n"
            "请确认手机已开启「开发者选项 -> USB 调试」，用数据线连接电脑并在手机上点击「允许 USB 调试」。"
        )
    print(f"[OK] 已连接设备: {online[0]}")


def trigger_app_import(device_file_path: str):
    print(f"[INFO] 正在唤起 Gemini词典 自动导入: {device_file_path}")
    run_adb([
        "shell", "am", "start",
        "-a", APP_IMPORT_ACTION,
        "-n", f"{APP_PACKAGE}/.MainActivity",
        "--es", "import_path", device_file_path,
    ])
    print("[OK] 已发送导入指令，请在手机上查看导入结果提示。")


def push_local_file(local_path: str):
    path = Path(local_path).expanduser().resolve()
    if not path.is_file():
        raise FileNotFoundError(f"找不到本地文件: {path}")
    if path.suffix.lower() not in SUPPORTED_EXTS:
        raise ValueError(f"不支持的文件格式: {path.suffix}（仅支持 {', '.join(SUPPORTED_EXTS)}）")

    run_adb(["shell", "mkdir", "-p", DEVICE_IMPORT_DIR])
    target_path = f"{DEVICE_IMPORT_DIR}/{path.name}"
    print(f"[INFO] 正在推送 {path} -> {target_path}")
    run_adb(["push", str(path), target_path])
    trigger_app_import(target_path)


def scan_phone_for_youdao_exports():
    print("[INFO] 正在扫描手机外部存储中的有道单词本导出文件（无 Root 权限可访问目录）...")
    found = []
    for directory in CANDIDATE_PHONE_DIRS:
        proc = run_adb(
            ["shell", f"find '{directory}' -maxdepth 3 -type f 2>/dev/null"],
            check=False,
        )
        for line in proc.stdout.splitlines():
            fpath = line.strip()
            if not fpath:
                continue
            lower = fpath.lower()
            if lower.endswith(SUPPORTED_EXTS) and (
                "youdao" in lower or "wordbook" in lower or "单词" in lower or "dict" in lower
            ):
                found.append(fpath)

    if not found:
        print(
            "[提示] 未在手机公共存储目录自动发现有道单词本导出文件。\n"
            "由于未 Root 的 Android 系统禁止读取 /data/data/com.youdao.dict 私有目录，\n"
            "推荐步骤：\n"
            "  1) 在电脑版网易有道词典（或手机端导出）将「我的单词本」导出为 .xml 或 .txt 文件；\n"
            "  2) 运行: python3 adb_import_youdao.py --file <导出的文件路径>"
        )
        return

    print("发现以下候选文件：")
    for idx, fpath in enumerate(found, 1):
        print(f"  [{idx}] {fpath}")

    choice = input("请输入要导入的文件编号（默认 1）: ").strip() or "1"
    try:
        selected = found[int(choice) - 1]
    except (ValueError, IndexError):
        raise ValueError("输入的编号无效。")

    run_adb(["shell", "mkdir", "-p", DEVICE_IMPORT_DIR])
    basename = os.path.basename(selected)
    target_path = f"{DEVICE_IMPORT_DIR}/{basename}"
    run_adb(["shell", f"cp '{selected}' '{target_path}'"])
    trigger_app_import(target_path)


def main():
    parser = argparse.ArgumentParser(description="无 Root 手机 ADB 导入有道单词本工具")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--file", "-f", help="电脑本地有道导出的 .xml / .txt / .csv / .db 文件路径")
    group.add_argument("--scan-phone", "-s", action="store_true", help="扫描手机外部存储中的有道导出文件")
    args = parser.parse_args()

    try:
        ensure_device_connected()
        if args.file:
            push_local_file(args.file)
        else:
            scan_phone_for_youdao_exports()
    except Exception as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
