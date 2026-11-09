import http from "k6/http";
import exec from "k6/execution";
import { check } from "k6";
import { SharedArray } from "k6/data";

if (!__ENV.NSS_K6_TOKENS_FILE || !__ENV.NSS_K6_BASE_URL) {
  throw new Error("Configure NSS_K6_TOKENS_FILE e NSS_K6_BASE_URL para o ambiente de teste autorizado.");
}

// A lista deve conter tokens de contas distintas criadas para o ensaio.
const tokens = new SharedArray("session tokens", () => JSON.parse(open(__ENV.NSS_K6_TOKENS_FILE)));
if (!tokens.length || tokens.some((token) => typeof token !== "string" || !token)) {
  throw new Error("NSS_K6_TOKENS_FILE deve conter uma lista JSON não vazia de tokens.");
}

const rate = Number(__ENV.NSS_K6_RATE || 1);
const preAllocatedVUs = Number(__ENV.NSS_K6_PREALLOCATED_VUS || 5);
const maxVUs = Number(__ENV.NSS_K6_MAX_VUS || 20);
const path = __ENV.NSS_K6_MAP_PATH || "/api/v1/epidemiology/municipalities?disease=DENG&year=2024";

export const options = {
  scenarios: {
    map_arrival: {
      executor: "constant-arrival-rate",
      rate,
      timeUnit: "1s",
      duration: __ENV.NSS_K6_DURATION || "30s",
      preAllocatedVUs,
      maxVUs,
    },
  },
  thresholds: {
    "http_req_failed{scenario:map_arrival}": ["rate<0.01"],
    "http_req_duration{name:map_municipalities}": ["p(95)<2000"],
    dropped_iterations: ["count==0"],
    checks: ["rate>0.99"],
  },
};

export default function () {
  const token = tokens[exec.scenario.iterationInTest % tokens.length];
  const response = http.get(`${__ENV.NSS_K6_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: "map_municipalities" },
  });
  check(response, { "map returned 200": (value) => value.status === 200 });
}
