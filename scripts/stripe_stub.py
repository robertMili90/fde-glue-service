"""Read-only, loopback Stripe fixtures shared by the offline demonstrations."""
import json
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlsplit

SECRET = "whsec_offline_demo"


@dataclass(frozen=True)
class Payment:
    status: str = "succeeded"
    refunded: bool = False


PAYMENTS = {}


class StripeStub(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def do_GET(self):
        path = urlsplit(self.path)
        payment_id = (path.path.rsplit("/", 1)[1] if path.path.startswith("/v1/payment_intents/")
                      else parse_qs(path.query).get("payment_intent", [None])[0])
        payment = PAYMENTS.get(payment_id)
        if payment is None or not (path.path.startswith("/v1/payment_intents/") or path.path == "/v1/refunds"):
            self.respond(404, {"error": {"type": "invalid_request_error", "code": "resource_missing",
                                      "message": "No such offline fixture PaymentIntent"}})
            return
        if path.path.startswith("/v1/payment_intents/"):
            result = {"id": payment_id, "object": "payment_intent", "amount": 1200,
                      "amount_received": 1200 if payment.status == "succeeded" else 0,
                      "currency": "usd", "status": payment.status,
                      "livemode": False, "capture_method": "automatic"}
        else:
            data = [] if not payment.refunded else [{"id": "re_" + payment_id, "object": "refund",
                    "payment_intent": payment_id, "amount": 1200, "currency": "usd", "status": "succeeded"}]
            result = {"object": "list", "url": "/v1/refunds", "data": data, "has_more": False}
        self.respond(200, result)

    def respond(self, status, result):
        body = json.dumps(result).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
