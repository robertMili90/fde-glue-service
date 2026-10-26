#!/usr/bin/env python3
"""Exercise the two packaged applications with a local Stripe stub; no credentials."""
import hashlib
import hmac
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import time
from http.server import ThreadingHTTPServer
from urllib.request import Request, urlopen

from stripe_stub import PAYMENTS, SECRET, Payment, StripeStub

ROOT = Path(__file__).resolve().parents[1]


def call(base, path, body=None, headers=None):
    request = Request(base + path, data=None if body is None else json.dumps(body).encode(),
                      headers={"Content-Type": "application/json", **(headers or {})})
    with urlopen(request, timeout=5) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


def wait_for(check, description, seconds=40):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            if check():
                return
        except (OSError, ValueError):
            pass
        time.sleep(0.1)
    raise RuntimeError("Timed out: " + description)


def port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def event(glue, event_id, payment):
    body = json.dumps({"id": event_id, "object": "event", "type": "payment_intent.succeeded",
                       "livemode": False, "data": {"object": {"id": payment, "object": "payment_intent"}}})
    timestamp = str(int(time.time()))
    signature = hmac.new(SECRET.encode(), (timestamp + "." + body).encode(), hashlib.sha256).hexdigest()
    request = Request(glue + "/webhooks/stripe", data=body.encode(), headers={
        "Content-Type": "application/json", "Stripe-Signature": "t=" + timestamp + ",v1=" + signature})
    with urlopen(request, timeout=5) as response:
        assert response.status == 200


def main():
    jars = {module: ROOT / module / "target" / (module + "-0.1.0-SNAPSHOT-exec.jar")
            for module in ("erp-simulator", "glue-service")}
    if not all(jar.is_file() for jar in jars.values()):
        raise SystemExit("Build first: ./mvnw verify")
    server = ThreadingHTTPServer(("127.0.0.1", 0), StripeStub)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    processes = {}
    logs = []
    with tempfile.TemporaryDirectory(prefix="fde-demo-") as temporary:
        directory = Path(temporary)
        ports = {module: port() for module in jars}
        erp = "http://localhost:" + str(ports["erp-simulator"])
        glue = "http://localhost:" + str(ports["glue-service"])

        def start(module):
            env = os.environ.copy()
            prefix = "ERP" if module == "erp-simulator" else "GLUE"
            env.update({prefix + "_PORT": str(ports[module]), prefix + "_DATA_DIR": str(directory / module),
                        "STRIPE_SECRET_KEY": "sk_test_offline_demo", "STRIPE_WEBHOOK_SECRET": SECRET,
                        "ERP_BASE_URL": erp, "SYNC_ENABLED": "true"})
            log = open(directory / (module + ".log"), "a+")
            logs.append(log)
            processes[module] = subprocess.Popen(["java", "-jar", str(jars[module]),
                "--stripe.api-base=http://127.0.0.1:" + str(server.server_port)],
                cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
            url = erp if module == "erp-simulator" else glue
            wait_for(lambda: processes[module].poll() is None and call(url, "/actuator/health")["status"] == "UP", module + " startup")

        def stop(module):
            process = processes.pop(module, None)
            if process is not None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
                    raise RuntimeError(module + " did not stop normally")

        def seed(index):
            payment = "pi_demo_" + str(index)
            PAYMENTS[payment] = Payment()
            call(erp, "/invoices", {"id": "invoice_" + str(index), "expectedAmountMinor": 1200,
                 "currency": "USD", "paymentIntentId": payment})
            return payment

        try:
            start("erp-simulator")
            start("glue-service")
            started = time.monotonic()
            for index in range(10):
                payment = seed(index)
                if index != 0:  # Deliberately suppress the very first notification.
                    event(glue, "evt_demo_" + str(index), payment)
            call(glue, "/operations/reconciliations", {})
            wait_for(lambda: len(call(glue, "/operations/work")) == 10 and
                     all(item["state"] == "DONE" for item in call(glue, "/operations/work")), "burst convergence")
            elapsed = round(time.monotonic() - started, 3)
            assert call(erp, "/demo/state") == {"invoiceCount": 10, "paymentProjectionCount": 10, "appliedUpdateCount": 10}
            PAYMENTS["pi_demo_9"] = Payment(refunded=True)
            event(glue, "evt_old_success_after_refund", "pi_demo_9")
            wait_for(lambda: call(erp, "/invoices/invoice_9")["financialStatus"] == "REFUNDED", "late refund")
            call(erp, "/demo/faults", {"mode": "FAIL_BEFORE_WRITE", "remainingRequests": 1})
            payment = seed(10)
            event(glue, "evt_restart", payment)
            wait_for(lambda: call(glue, "/operations/work/" + payment)["state"] == "RETRY_SCHEDULED", "persisted retry")
            stop("glue-service")
            start("glue-service")
            wait_for(lambda: call(glue, "/operations/work/" + payment)["state"] == "DONE", "restart recovery")
            assert call(erp, "/demo/state")["paymentProjectionCount"] == 11
            print(json.dumps({"burst_invoices": 10, "burst_seconds": elapsed, "worker_poll_ms": 500,
                  "missing_first_webhook": "passed", "late_refund": "passed", "retry_after_process_restart": "passed",
                  "erp": call(erp, "/demo/state")}, indent=2))
        except Exception:
            for log in logs:
                log.flush()
                log.seek(0)
                print(log.read()[-5000:])  # Only synthetic credentials are used by these child processes.
            raise
        finally:
            for module in list(processes):
                stop(module)
            for log in logs:
                log.close()
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    main()
