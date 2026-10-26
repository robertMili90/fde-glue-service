#!/usr/bin/env python3
"""Explicit sandbox fixture operations, kept outside the runtime synchronization path."""
import argparse
import base64
import json
import os
import re
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--erp", default="http://localhost:8081")
    commands = parser.add_subparsers(dest="command", required=True)
    pay = commands.add_parser("pay", help="create test PaymentIntent, link ERP invoice, then confirm")
    pay.add_argument("--invoice", required=True)
    pay.add_argument("--amount", type=int, default=1200)
    pay.add_argument("--currency", default="usd")
    refund = commands.add_parser("refund", help="create a full refund for an explicitly tagged test PaymentIntent")
    refund.add_argument("payment_intent")
    args = parser.parse_args()
    key = os.environ.get("STRIPE_SECRET_KEY", "")
    if not re.fullmatch(r"(?:sk|rk)_test_\S+", key):
        parser.error("Set STRIPE_SECRET_KEY to a test-mode key in your local environment")
    if args.command == "pay" and (args.amount < 1 or args.amount > 9223372036854775807 or not re.fullmatch(r"[a-zA-Z]{3}", args.currency)):
        parser.error("Use a positive integral minor-unit amount and three-letter currency")
    if args.command == "refund" and not re.fullmatch(r"pi_\w+", args.payment_intent):
        parser.error("Expected a PaymentIntent ID")

    def stripe(path, fields=None):
        headers = {"Authorization": "Basic " + base64.b64encode((key + ":").encode()).decode(),
                   "Stripe-Version": "2026-09-30.endive"}
        request = Request("https://api.stripe.com/v1/" + path,
                          data=None if fields is None else urlencode(fields).encode(), headers=headers)
        try:
            with urlopen(request, timeout=15) as response:
                return json.load(response)
        except HTTPError as error:
            # Do not echo response bodies, headers, or credentials.
            raise SystemExit("Stripe returned HTTP " + str(error.code)) from None

    def test_mode(resource):
        if resource.get("livemode") is not False:
            raise SystemExit("Refusing a resource without an explicit test-mode flag")

    if args.command == "pay":
        payment = stripe("payment_intents", {"amount": args.amount, "currency": args.currency.lower(),
                         "payment_method_types[]": "card", "metadata[fde_demo]": "true"})
        test_mode(payment)
        fixture = {"id": args.invoice, "expectedAmountMinor": args.amount, "currency": args.currency.upper(),
                   "paymentIntentId": payment["id"]}
        try:
            with urlopen(Request(args.erp.rstrip("/") + "/invoices", data=json.dumps(fixture).encode(),
                         headers={"Content-Type": "application/json"}), timeout=5) as response:
                assert response.status == 201
        except HTTPError as error:
            raise SystemExit("ERP link failed (HTTP " + str(error.code) + "); test payment remains unconfirmed") from None
        payment = stripe("payment_intents/" + payment["id"] + "/confirm", {"payment_method": "pm_card_visa"})
        test_mode(payment)
        print(json.dumps({"invoiceId": args.invoice, "paymentIntentId": payment["id"], "status": payment["status"]}))
    else:
        payment = stripe("payment_intents/" + args.payment_intent)
        test_mode(payment)
        if payment.get("metadata", {}).get("fde_demo") != "true":
            raise SystemExit("Refusing to refund a payment not tagged by this fixture tool")
        result = stripe("refunds", {"payment_intent": payment["id"], "metadata[fde_demo]": "true"})
        # Refund resources inherit the checked PaymentIntent/account mode and have no livemode field.
        print(json.dumps({"paymentIntentId": payment["id"], "refundId": result["id"], "status": result["status"]}))


if __name__ == "__main__":
    main()
