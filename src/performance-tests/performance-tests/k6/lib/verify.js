import { check, sleep } from "k6";
import { getPurchase, getPurchases, getSale, jsonBody } from "./client.js";

export function verifySale(actor, saleId, remainingStock, status) {
  const response = getSale(saleId, actor);
  const body = jsonBody(response);
  check(response, {
    [`sale ${saleId}: verification endpoint returns 200`]: (r) => r.status === 200,
    [`sale ${saleId}: remaining stock is ${remainingStock}`]: () => body?.remainingStock === remainingStock,
    [`sale ${saleId}: status is ${status}`]: () => body?.status === status
  });
}

export function verifySaleEventually(
  actor,
  saleId,
  remainingStock,
  status,
  timeoutSeconds = 30,
  pollSeconds = 1
) {
  const deadline = Date.now() + (timeoutSeconds * 1000);
  let response;
  let body;

  do {
    response = getSale(saleId, actor);
    body = jsonBody(response);

    if (
      response.status === 200 &&
      body?.remainingStock === remainingStock &&
      body?.status === status
    ) {
      break;
    }

    if (Date.now() < deadline) {
      sleep(pollSeconds);
    }
  } while (Date.now() < deadline);

  check(response, {
    [`sale ${saleId}: eventual verification endpoint returns 200`]: (r) => r.status === 200,
    [`sale ${saleId}: remaining stock eventually becomes ${remainingStock}`]: () =>
      body?.remainingStock === remainingStock,
    [`sale ${saleId}: status eventually becomes ${status}`]: () => body?.status === status
  });
}

export function verifyActivePurchaseCount(actor, saleId, expectedCount) {
  const response = getPurchases(actor);
  const body = jsonBody(response);
  const purchases = Array.isArray(body?.purchases) ? body.purchases : [];
  const actual = purchases.filter(
    (purchase) => purchase.saleId === saleId && purchase.status !== "CANCELED"
  ).length;
  check(response, {
    "purchase list endpoint returns 200": (r) => r.status === 200,
    [`sale ${saleId}: active purchase count is ${expectedCount}`]: () => actual === expectedCount
  });
}

export function verifyPurchaseStatus(actor, purchaseId, expectedStatus) {
  const response = getPurchase(purchaseId, actor);
  const body = jsonBody(response);
  check(response, {
    [`purchase ${purchaseId}: verification endpoint returns 200`]: (r) => r.status === 200,
    [`purchase ${purchaseId}: status is ${expectedStatus}`]: () => body?.status === expectedStatus
  });
}
