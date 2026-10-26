#!/usr/bin/env python3
"""Start a persistent, prepopulated Swagger demo, or verify it in isolated databases."""
import argparse
import fcntl
import hashlib
import hmac
import json
from pathlib import Path
import signal
import socket
import subprocess
import tempfile
import threading
import time
from http.server import ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from stripe_stub import PAYMENTS, SECRET, Payment, StripeStub

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = {
    "inv_demo": ("pi_demo", 1200, Payment()),
    "inv_refunded": ("pi_refunded", 1200, Payment(refunded=True)),
    "inv_unpaid": ("pi_unpaid", 1200, Payment(status="requires_payment_method")),
    "inv_review": ("pi_review", 1300, Payment()),
}


def request(base, path, method="GET", body=None, headers=None, expected=(200,)):
    raw = None if body is None else json.dumps(body).encode()
    req = Request(base + path, data=raw, method=method,
                  headers={"Content-Type": "application/json", **(headers or {})})
    try:
        response = urlopen(req, timeout=5)
    except HTTPError as error:
        response = error
    with response:
        raw = response.read()
        result = json.loads(raw) if raw else None
        if response.status not in expected:
            raise RuntimeError(f"{method} {path}: HTTP {response.status}: {result}")
        return result, response.headers, response.status


def wait_for(check, description, seconds=60):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            if check():
                return
        except ChildProcessError:
            raise
        except (OSError, RuntimeError):
            pass
        time.sleep(0.15)
    raise RuntimeError("Timed out: " + description)


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


class Demo:
    def __init__(self, directory, glue_port, erp_port):
        self.directory = directory
        self.ports = {"glue-service": glue_port, "erp-simulator": erp_port}
        self.glue = f"http://localhost:{glue_port}"
        self.erp = f"http://localhost:{erp_port}"
        self.processes = {}
        self.logs = {}
        # Reject occupied ports before starting children; never replace an existing application.
        for port in self.ports.values():
            with socket.socket() as sock:
                # Match Tomcat's reuse behavior so TIME_WAIT does not reject an immediate restart.
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                sock.bind(("127.0.0.1", port))
        PAYMENTS.clear()
        PAYMENTS.update({payment: facts for payment, _, facts in FIXTURES.values()})
        PAYMENTS["pi_create"] = Payment()
        self.stripe = ThreadingHTTPServer(("127.0.0.1", 0), StripeStub)
        threading.Thread(target=self.stripe.serve_forever, daemon=True).start()

    def start(self, module):
        jar = ROOT / module / "target" / f"{module}-0.1.0-SNAPSHOT-exec.jar"
        database = self.directory / module / ("erp" if module == "erp-simulator" else "glue")
        database.parent.mkdir(parents=True, exist_ok=True)
        log = open(self.directory / (module + ".log"), "a+")
        self.logs[module] = log
        arguments = ["java", "-jar", str(jar), "--spring.profiles.active=swagger-demo",
                     "--server.address=127.0.0.1", f"--server.port={self.ports[module]}",
                     f"--spring.datasource.url=jdbc:h2:file:{database};DB_CLOSE_ON_EXIT=FALSE",
                     f"--stripe.api-base=http://127.0.0.1:{self.stripe.server_port}",
                     "--stripe.secret-key=sk_test_offline_demo", f"--stripe.webhook-secret={SECRET}",
                     f"--erp.base-url={self.erp}", "--sync.enabled=true",
                     "--sync.reconciliation-initial-ms=3600000"]
        self.processes[module] = subprocess.Popen(arguments, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        base = self.erp if module == "erp-simulator" else self.glue

        def healthy():
            if self.processes[module].poll() is not None:
                raise ChildProcessError(f"{module} exited; inspect its demo log")
            return request(base, "/actuator/health")[0]["status"] == "UP"

        wait_for(healthy, module + " startup")

    def stop(self, module):
        process = self.processes.pop(module, None)
        if process is not None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
        log = self.logs.pop(module, None)
        if log:
            log.close()

    def close(self):
        for module in list(self.processes):
            self.stop(module)
        self.stripe.shutdown()
        self.stripe.server_close()

    def seed(self):
        """Add missing fixtures through public APIs; preserve existing invoice facts and history."""
        restored_payments = []
        for invoice_id, (payment_id, amount, _) in FIXTURES.items():
            invoice, _, status = request(self.erp, "/invoices/" + invoice_id, expected=(200, 404))
            if status == 404:
                restored_payments.append(payment_id)
                invoice = request(self.erp, "/invoices", "POST", {
                    "id": invoice_id, "paymentIntentId": payment_id,
                    "expectedAmountMinor": amount, "currency": "USD"}, expected=(201,))[0]
            if (invoice["paymentIntentId"], invoice["expectedAmountMinor"], invoice["currency"]) != (payment_id, amount, "USD"):
                raise RuntimeError(f"Existing {invoice_id} differs from the fixture; no data was overwritten")
            if invoice_id == "inv_demo" and invoice["financialStatus"] in ("UNPAID", "PAID"):
                request(self.erp, "/invoices/inv_demo/payment-projection", "PUT", {
                    "paymentIntentId": "pi_demo", "currency": "USD", "amountCollectedMinor": 1200,
                    "amountRefundedMinor": 0, "status": "PAID"},
                    {"Idempotency-Key": "demo-paid-v1", "If-Match": f'"{invoice["version"]}"'})
        run = request(self.glue, "/operations/reconciliations", "POST", expected=(202,))[0]
        wait_for(lambda: request(self.glue, "/operations/reconciliations/" + run["id"])[0]["state"] == "COMPLETE",
                 "fixture reconciliation")
        # A reset removes only ERP data. Release held work only for invoices just restored;
        # existing invoices with operator edits retain their review state.
        for payment_id in restored_payments:
            request(self.glue, "/operations/work/" + payment_id + "/requeue", "POST", expected=(202,))
        wait_for(lambda: all(request(self.glue, "/operations/work/" + payment)[0]["totalAttempts"] > 0
                             and request(self.glue, "/operations/work/" + payment)[0]["state"] not in ("READY", "RUNNING")
                             for payment, _, _ in FIXTURES.values()), "fixture processing")
        return run["id"]


def example(document, path, method, name):
    return document["paths"][path][method]["requestBody"]["content"]["application/json"]["examples"][name]["value"]


def verify(demo):
    glue = request(demo.glue, "/v3/api-docs")[0]
    erp = request(demo.erp, "/v3/api-docs")[0]
    for base, document in ((demo.glue, glue), (demo.erp, erp)):
        for path, methods in document["paths"].items():
            if "get" in methods:
                actual_path = path
                for parameter in methods["get"].get("parameters", []):
                    if parameter["in"] == "path":
                        actual_path = actual_path.replace("{" + parameter["name"] + "}", str(parameter["example"]))
                request(base, actual_path)
    assert request(demo.glue, "/operations/work/pi_demo")[0]["state"] == "DONE"
    attempts = request(demo.glue, "/operations/work/pi_demo/attempts")[0]
    assert attempts
    attempt_examples = glue["paths"]["/operations/work/{paymentIntentId}/attempts"]["get"]["responses"]["200"]["content"]["application/json"]["examples"]
    assert {item["id"] for item in next(iter(attempt_examples.values()))["value"]} <= {item["id"] for item in attempts}
    for path in ("/operations/reconciliations", "/operations/reconciliations/{id}"):
        for operation in glue["paths"][path].values():
            status = "200" if "200" in operation["responses"] else "202"
            examples = operation["responses"][status]["content"]["application/json"]["examples"]
            for entry in examples.values():
                values = entry["value"] if isinstance(entry["value"], list) else [entry["value"]]
                for value in values:
                    request(demo.glue, "/operations/reconciliations/" + value["id"])
    assert request(demo.glue, "/operations/work/pi_review")[0]["state"] == "NEEDS_REVIEW"
    assert request(demo.glue, "/operations/work/pi_unpaid")[0]["state"] == "WAITING_PROVIDER"
    assert request(demo.erp, "/invoices/inv_refunded")[0]["financialStatus"] == "REFUNDED"
    initial = request(demo.erp, "/demo/state")[0]
    assert initial == {"invoiceCount": 4, "paymentProjectionCount": 2, "appliedUpdateCount": 2}
    demo.seed()
    assert request(demo.erp, "/demo/state")[0] == initial

    # The bundled browser signer must be served only in the explicit demo profile.
    initializer = urlopen(demo.glue + "/swagger-ui/swagger-initializer.js").read().decode()
    assert "requestInterceptor: window.fdeDemoSign" in initializer
    for name in ("Payment succeeded", "Refund updated"):
        body = example(glue, "/webhooks/stripe", "post", name)
        timestamp = str(int(time.time()))
        signature = hmac.new(SECRET.encode(), (timestamp + "." + json.dumps(body)).encode(), hashlib.sha256).hexdigest()
        headers = {"Stripe-Signature": f"t={timestamp},v1={signature}"}
        assert request(demo.glue, "/webhooks/stripe", "POST", body, headers)[0] == {"status": "recorded"}
        assert request(demo.glue, "/webhooks/stripe", "POST", body, headers)[0] == {"status": "duplicate"}
    request(demo.glue, "/webhooks/stripe", "POST", body, {"Stripe-Signature": "invalid"}, expected=(400,))
    request(demo.glue, "/operations/work/pi_review/requeue", "POST", expected=(202,))

    create = example(erp, "/invoices", "post", "Linked USD invoice")
    request(demo.erp, "/invoices", "POST", create, expected=(201,))
    request(demo.erp, "/invoices", "POST", create, expected=(409,))
    demo.seed()
    wait_for(lambda: request(demo.glue, "/operations/work/pi_create")[0]["state"] == "DONE",
             "created invoice convergence before restart assertions")
    projection_path = "/invoices/inv_demo/payment-projection"
    paid = example(erp, "/invoices/{invoiceId}/payment-projection", "put", "Paid")
    invoice, _, _ = request(demo.erp, projection_path, "PUT", paid,
                             {"Idempotency-Key": "demo-paid-v1", "If-Match": '"1"'})
    assert invoice["financialStatus"] == "PAID"
    refunded = example(erp, "/invoices/{invoiceId}/payment-projection", "put", "Refunded")
    request(demo.erp, projection_path, "PUT", refunded,
            {"Idempotency-Key": "demo-refunded-v1", "If-Match": f'"{invoice["version"]}"'})
    counts = request(demo.erp, "/demo/state")[0]

    # Restart both JVMs on the same files: seeding must retain edits and idempotency results.
    for module in ("glue-service", "erp-simulator"):
        demo.stop(module)
    for module in ("erp-simulator", "glue-service"):
        demo.start(module)
    assert request(demo.erp, "/invoices/inv_demo")[0]["financialStatus"] == "REFUNDED"
    assert request(demo.erp, "/demo/state")[0] == counts
    demo.seed()
    assert request(demo.erp, "/invoices/inv_demo")[0]["financialStatus"] == "REFUNDED"
    assert request(demo.glue, "/operations/work/pi_demo")[0]["state"] == "NEEDS_REVIEW"
    assert request(demo.erp, "/demo/state")[0] == counts

    faults = erp["paths"]["/demo/faults"]["post"]["requestBody"]["content"]["application/json"]["examples"]
    for fault in faults.values():
        request(demo.erp, "/demo/faults", "POST", fault["value"], expected=(204,))
    request(demo.erp, "/demo/faults", "POST", {"mode": "NONE"}, expected=(204,))
    request(demo.erp, "/demo/reset", "POST", expected=(204,))
    assert request(demo.erp, "/demo/state")[0]["invoiceCount"] == 0
    demo.seed()
    assert request(demo.erp, "/demo/state")[0] == initial
    print("PASS: every documented endpoint, signature/deduplication, reseeding, file restart, edit preservation and reset recovery", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--glue-port", type=int, default=18080)
    parser.add_argument("--erp-port", type=int, default=18081)
    parser.add_argument("--data-dir", type=Path, default=ROOT / "data" / "swagger-demo")
    parser.add_argument("--check", action="store_true", help="Verify all endpoints in temporary databases and exit")
    args = parser.parse_args()
    if args.glue_port == args.erp_port:
        parser.error("Each application needs a separate port")
    for module in ("erp-simulator", "glue-service"):
        if not (ROOT / module / "target" / f"{module}-0.1.0-SNAPSHOT-exec.jar").is_file():
            parser.error("Build first: ./mvnw verify")
    temporary = tempfile.TemporaryDirectory(prefix="fde-swagger-check-") if args.check else None
    directory = Path(temporary.name) if temporary else args.data_dir.resolve()
    directory.mkdir(parents=True, exist_ok=True)
    demo = None
    lock = open(directory / ".runner.lock", "w")
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        demo = Demo(directory, free_port() if args.check else args.glue_port,
                    free_port() if args.check else args.erp_port)
        demo.start("erp-simulator")
        demo.start("glue-service")
        run_id = demo.seed()
        if args.check:
            verify(demo)
        else:
            print(f"Seeded demo ready. Glue: {demo.glue}/swagger-ui.html\n"
                  f"ERP: {demo.erp}/swagger-ui.html\nReconciliation: {run_id}\n"
                  f"Persistent data/logs: {directory}\nCtrl-C stops only this demo; restart the command to reuse its data.", flush=True)
            while True:
                time.sleep(1)
                for module, process in demo.processes.items():
                    if process.poll() is not None:
                        raise ChildProcessError(f"{module} exited; inspect its demo log")
    except KeyboardInterrupt:
        pass
    finally:
        if demo:
            demo.close()
        lock.close()
        if temporary:
            temporary.cleanup()


def stop_requested(*_):
    raise KeyboardInterrupt


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, stop_requested)
    main()
