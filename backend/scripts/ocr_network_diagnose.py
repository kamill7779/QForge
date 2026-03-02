#!/usr/bin/env python3
"""Diagnose OCR outbound network path for api.z.ai layout_parsing endpoint.

Usage:
  python backend/scripts/ocr_network_diagnose.py
  $env:ZHIPU_API_KEY="xxx"; python backend/scripts/ocr_network_diagnose.py
"""

from __future__ import annotations

import argparse
import json
import os
import socket
import ssl
import urllib.error
import urllib.request


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Diagnose OCR network connectivity for api.z.ai")
    parser.add_argument("--host", default="api.z.ai", help="Target host to diagnose.")
    parser.add_argument("--path", default="/api/paas/v4/layout_parsing", help="HTTPS path for API check.")
    parser.add_argument("--model", default="glm-ocr", help="OCR model name.")
    parser.add_argument(
        "--file",
        default="https://cdn.bigmodel.cn/static/logo/introduction.png",
        help="Public image URL used for optional API POST check.",
    )
    parser.add_argument("--timeout", type=float, default=8.0, help="Connect/read timeout seconds.")
    parser.add_argument("--skip-api", action="store_true", help="Skip real API POST check.")
    parser.add_argument("--api-key-env", default="ZHIPU_API_KEY", help="Environment variable that stores API key.")
    return parser.parse_args()


def resolve_ips(host: str, family: socket.AddressFamily) -> list[str]:
    ips: list[str] = []
    try:
        infos = socket.getaddrinfo(host, 443, family, socket.SOCK_STREAM)
    except socket.gaierror:
        return ips
    for info in infos:
        ip = info[4][0]
        if ip not in ips:
            ips.append(ip)
    return ips


def tls_head_probe(host: str, ip: str, family: socket.AddressFamily, timeout: float) -> tuple[bool, str]:
    sock = socket.socket(family, socket.SOCK_STREAM)
    sock.settimeout(timeout)
    try:
        sock.connect((ip, 443))
        context = ssl.create_default_context()
        with context.wrap_socket(sock, server_hostname=host) as tls_sock:
            req = (
                f"HEAD / HTTP/1.1\r\n"
                f"Host: {host}\r\n"
                f"User-Agent: ocr-network-diagnose/1.0\r\n"
                "Connection: close\r\n\r\n"
            ).encode("ascii")
            tls_sock.sendall(req)
            data = tls_sock.recv(256)
            if not data:
                return False, "connected but no response bytes"
            first_line = data.splitlines()[0].decode("utf-8", errors="replace")
            return True, first_line
    except Exception as exc:  # noqa: BLE001
        return False, f"{type(exc).__name__}: {exc}"
    finally:
        try:
            sock.close()
        except Exception:  # noqa: BLE001
            pass


def call_layout_parsing(endpoint: str, api_key: str, model: str, file_ref: str, timeout: float) -> tuple[bool, str]:
    payload = json.dumps({"model": model, "file": file_ref}).encode("utf-8")
    req = urllib.request.Request(
        endpoint,
        data=payload,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read(512).decode("utf-8", errors="replace")
            return True, f"HTTP {resp.status}, body preview: {body[:160]}"
    except urllib.error.HTTPError as exc:
        body = exc.read(512).decode("utf-8", errors="replace")
        return False, f"HTTP {exc.code}, body preview: {body[:160]}"
    except Exception as exc:  # noqa: BLE001
        return False, f"{type(exc).__name__}: {exc}"


def main() -> int:
    args = parse_args()
    host = args.host
    endpoint = f"https://{host}{args.path}"

    ipv4s = resolve_ips(host, socket.AF_INET)
    ipv6s = resolve_ips(host, socket.AF_INET6)

    print(f"[dns] host={host}")
    print(f"[dns] A={ipv4s if ipv4s else 'NONE'}")
    print(f"[dns] AAAA={ipv6s if ipv6s else 'NONE'}")

    ipv4_ok = False
    ipv6_ok = False

    for ip in ipv4s:
        ok, msg = tls_head_probe(host, ip, socket.AF_INET, args.timeout)
        print(f"[probe] IPv4 {ip}: {'OK' if ok else 'FAIL'} - {msg}")
        ipv4_ok = ipv4_ok or ok

    for ip in ipv6s:
        ok, msg = tls_head_probe(host, ip, socket.AF_INET6, args.timeout)
        print(f"[probe] IPv6 {ip}: {'OK' if ok else 'FAIL'} - {msg}")
        ipv6_ok = ipv6_ok or ok

    if ipv4_ok and not ipv6_ok:
        print("[hint] IPv4 reachable but IPv6 failing. Enable IPv4 preference for OCR service.")
        print("[hint] Use GLM_OCR_PREFER_IPV4=true and JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true")

    if args.skip_api:
        return 0 if (ipv4_ok or ipv6_ok) else 1

    api_key = os.getenv(args.api_key_env, "")
    if not api_key:
        print(f"[api] skipped: missing env var {args.api_key_env}")
        return 0 if (ipv4_ok or ipv6_ok) else 1

    ok, msg = call_layout_parsing(endpoint, api_key, args.model, args.file, args.timeout)
    print(f"[api] POST {endpoint}: {'OK' if ok else 'FAIL'} - {msg}")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
