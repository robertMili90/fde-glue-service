// Public synthetic secret; this asset is installed only by the guarded swagger-demo profile.
window.fdeDemoSign = async function (request) {
  const target = new URL(request.url, window.location.origin);
  if (target.origin !== window.location.origin || target.pathname !== "/webhooks/stripe"
      || request.method.toUpperCase() !== "POST") {
    return request;
  }
  const bytes = new TextEncoder();
  const timestamp = Math.floor(Date.now() / 1000).toString();
  const key = await crypto.subtle.importKey("raw", bytes.encode("whsec_offline_demo"),
    {name: "HMAC", hash: "SHA-256"}, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, bytes.encode(timestamp + "." + request.body));
  const digest = Array.from(new Uint8Array(signature), value => value.toString(16).padStart(2, "0")).join("");
  request.headers["Stripe-Signature"] = "t=" + timestamp + ",v1=" + digest;
  return request;
};
