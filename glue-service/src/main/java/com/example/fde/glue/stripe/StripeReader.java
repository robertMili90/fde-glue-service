package com.example.fde.glue.stripe;

import com.example.fde.glue.config.StripeProperties;
import com.example.fde.glue.config.SyncSettings;
import com.example.fde.glue.work.PaymentFacts;
import com.example.fde.glue.work.RetryAfter;
import com.example.fde.glue.work.SyncFailure;
import com.example.fde.glue.work.WorkState;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.HttpURLConnectionClient;
import com.stripe.net.StripeRequest;
import com.stripe.net.StripeResponse;
import com.stripe.param.RefundListParams;
import java.time.Clock;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripeReader {
  private static final int MAX_REFUND_PAGES = 1000;

  private enum Reason {
    STRIPE_NOT_CONFIGURED,
    STRIPE_CONNECTION,
    LIVE_OR_UNKNOWN_MODE_RESOURCE,
    INVALID_STRIPE_PAYMENT,
    REFUND_ENUMERATION_LIMIT,
    INVALID_STRIPE_REFUND,
    UNKNOWN_REFUND_STATUS,
    INVALID_REFUND_PAGINATION,
    REFUND_AMOUNT_OVERFLOW
  }

  private record RefundTotals(long succeededMinor, boolean pending) {}

  private final StripeClient client;
  private final CapturingTransport transport = new CapturingTransport();
  private final Clock clock;

  public StripeReader(
      StripeProperties credentials,
      SyncSettings settings,
      Clock clock,
      @Value("${stripe.api-base:https://api.stripe.com}") String apiBase) {
    this.clock = clock;
    String secretKey = credentials.secretKey();
    if (secretKey == null || secretKey.isEmpty()) {
      client = null;
    } else {
      client =
          StripeClient.builder()
              .setApiKey(secretKey)
              .setApiBase(apiBase)
              .setMaxNetworkRetries(0)
              .setHttpClient(transport)
              .setConnectTimeout(settings.httpTimeoutMs())
              .setReadTimeout(settings.httpTimeoutMs())
              .build();
    }
  }

  public PaymentFacts read(String paymentIntentId) {
    if (client == null) {
      throw new SyncFailure(WorkState.FAILED, Reason.STRIPE_NOT_CONFIGURED.name());
    }
    transport.retryAfter.remove();
    try {
      PaymentIntent paymentIntent = client.v1().paymentIntents().retrieve(paymentIntentId);
      validatePayment(paymentIntentId, paymentIntent);
      String currency = paymentIntent.getCurrency().toUpperCase(Locale.ROOT);
      RefundTotals refunds = readRefunds(paymentIntentId, currency);
      return new PaymentFacts(
          paymentIntentId,
          paymentIntent.getStatus(),
          currency,
          paymentIntent.getAmount(),
          paymentIntent.getAmountReceived(),
          refunds.succeededMinor(),
          refunds.pending(),
          paymentIntent.getCaptureMethod());
    } catch (StripeException failure) {
      Integer status = failure.getStatusCode();
      if (status == null || status == 0) {
        throw new SyncFailure(WorkState.RETRY_SCHEDULED, Reason.STRIPE_CONNECTION.name(), failure);
      }
      throw SyncFailure.http(
          "STRIPE", status, RetryAfter.millis(transport.retryAfter.get(), clock), failure);
    } catch (ArithmeticException overflow) {
      throw new SyncFailure(WorkState.NEEDS_REVIEW, Reason.REFUND_AMOUNT_OVERFLOW.name(), overflow);
    } finally {
      transport.retryAfter.remove();
    }
  }

  private void validatePayment(String paymentIntentId, PaymentIntent paymentIntent) {
    if (!Boolean.FALSE.equals(paymentIntent.getLivemode())) {
      throw review(Reason.LIVE_OR_UNKNOWN_MODE_RESOURCE);
    }
    if (!paymentIntentId.equals(paymentIntent.getId())
        || paymentIntent.getAmountReceived() == null
        || paymentIntent.getAmount() == null
        || paymentIntent.getCurrency() == null
        || paymentIntent.getStatus() == null) {
      throw review(Reason.INVALID_STRIPE_PAYMENT);
    }
  }

  private RefundTotals readRefunds(String paymentIntentId, String currency) throws StripeException {
    long refundedMinor = 0;
    boolean pendingRefund = false;
    String cursor = null;
    Set<String> refundIds = new HashSet<>();
    for (int pageNumber = 0; ; pageNumber++) {
      if (pageNumber >= MAX_REFUND_PAGES) {
        throw review(Reason.REFUND_ENUMERATION_LIMIT);
      }
      var parameters = RefundListParams.builder().setPaymentIntent(paymentIntentId).setLimit(100L);
      if (cursor != null) {
        parameters.setStartingAfter(cursor);
      }
      var refunds = client.v1().refunds().list(parameters.build());
      for (Refund refund : refunds.getData()) {
        validateRefund(paymentIntentId, currency, refund, refundIds);
        switch (refund.getStatus()) {
          case "succeeded" -> refundedMinor = Math.addExact(refundedMinor, refund.getAmount());
          case "pending", "requires_action" -> pendingRefund = true;
          case "failed", "canceled" -> {
            /* These do not reduce collected funds. */
          }
          default -> throw review(Reason.UNKNOWN_REFUND_STATUS);
        }
        cursor = refund.getId();
      }
      if (Boolean.FALSE.equals(refunds.getHasMore())) {
        return new RefundTotals(refundedMinor, pendingRefund);
      }
      if (!Boolean.TRUE.equals(refunds.getHasMore()) || refunds.getData().isEmpty()) {
        throw review(Reason.INVALID_REFUND_PAGINATION);
      }
    }
  }

  private void validateRefund(
      String paymentIntentId, String currency, Refund refund, Set<String> refundIds) {
    if (refund.getId() == null
        || !refundIds.add(refund.getId())
        || !paymentIntentId.equals(refund.getPaymentIntent())
        || refund.getCurrency() == null
        || !currency.equals(refund.getCurrency().toUpperCase(Locale.ROOT))
        || refund.getAmount() == null
        || refund.getAmount() < 0
        || refund.getStatus() == null) {
      throw review(Reason.INVALID_STRIPE_REFUND);
    }
  }

  private SyncFailure review(Reason reason) {
    return new SyncFailure(WorkState.NEEDS_REVIEW, reason.name());
  }

  private static class CapturingTransport extends HttpURLConnectionClient {
    private final ThreadLocal<String> retryAfter = new ThreadLocal<>();

    @Override
    public StripeResponse request(StripeRequest request) throws ApiConnectionException {
      StripeResponse response = super.request(request);
      retryAfter.set(response.headers().firstValue("Retry-After").orElse(null));
      return response;
    }
  }
}
