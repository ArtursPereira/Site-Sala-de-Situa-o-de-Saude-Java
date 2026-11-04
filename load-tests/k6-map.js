import http from "k6/http";
import { check } from "k6";

export const options = {
  scenarios: {
    map_500_vus: {
      executor: "per-vu-iterations",
      vus: Number(__ENV.NSS_K6_VUS || 500),
      iterations: 1,
      maxDuration: __ENV.K6_MAX_DURATION || "2m",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<2000"],
  },
};

const baseUrl = __ENV.K6_BASE_URL || "http://host.docker.internal:8080";
const mapPath =
  __ENV.K6_MAP_PATH ||
  "/api/v1/epidemiology/municipalities?disease=DENG&year=2026&month=1&municipalityCode=3304557";

function login(email, password) {
  const response = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { "Content-Type": "application/json" }, tags: { name: "login" } },
  );
  check(response, { "login returned 200": (res) => res.status === 200 });
  return response.json("accessToken");
}

export function setup() {
  const usersJson = __ENV.K6_USERS_JSON;
  if (usersJson) {
    const users = JSON.parse(usersJson);
    return { tokens: users.map((user) => login(user.email, user.password)) };
  }

  if (!__ENV.K6_EMAIL || !__ENV.K6_PASSWORD) {
    throw new Error("Configure K6_EMAIL/K6_PASSWORD ou K6_USERS_JSON");
  }

  return { tokens: [login(__ENV.K6_EMAIL, __ENV.K6_PASSWORD)] };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const response = http.get(`${baseUrl}${mapPath}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { name: "map_municipalities" },
  });

  check(response, {
    "map returned 200": (res) => res.status === 200,
  });
}
