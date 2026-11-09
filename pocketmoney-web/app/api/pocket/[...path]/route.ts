import { NextRequest, NextResponse } from "next/server";

const UPSTREAM = (process.env.POCKET_API_BASE || "https://schoolmis.xanderglobalacademy.com").replace(/\/$/, "");
const COOKIE = "pocket_token";

const ALLOWED = new Set([
  "health",
  "register-start",
  "register-confirm",
  "login",
  "recover",
  "recover-confirm",
  "logout",
  "me",
  "lookup",
  "link",
  "unlink",
  "wallets",
  "ledger",
  "spend-pin",
  "topup",
]);

function allowed(parts: string[]): boolean {
  if (parts.length === 0 || parts.length > 2) return false;
  if (!ALLOWED.has(parts[0])) return false;
  if (parts.length === 2 && !/^\d+$/.test(parts[1])) return false;
  if (parts.length === 2 && parts[0] !== "ledger" && parts[0] !== "topup") return false;
  return true;
}

async function proxy(request: NextRequest, parts: string[]) {
  if (!allowed(parts)) {
    return NextResponse.json({ message: "That request is not available." }, { status: 404 });
  }
  const path = parts.join("/");
  const token = request.cookies.get(COOKIE)?.value || "";
  const headers: Record<string, string> = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  let body: string | undefined;
  if (request.method !== "GET" && request.method !== "HEAD") {
    headers["Content-Type"] = "application/json";
    body = await request.text();
  }
  let upstream: Response;
  try {
    upstream = await fetch(`${UPSTREAM}/api/pocket/${path}`, {
      method: request.method,
      headers,
      body,
      cache: "no-store",
    });
  } catch {
    return NextResponse.json({ message: "Cannot reach the school server." }, { status: 502 });
  }
  const text = await upstream.text();
  let json: Record<string, unknown> = {};
  try {
    json = text ? (JSON.parse(text) as Record<string, unknown>) : {};
  } catch {
    return NextResponse.json({ message: "Unexpected server response." }, { status: 502 });
  }
  const sessionToken = typeof json.token === "string" ? json.token : "";
  if (sessionToken) delete json.token;
  const response = NextResponse.json(json, { status: upstream.status });
  response.headers.set("Cache-Control", "no-store");
  if (sessionToken) {
    response.cookies.set(COOKIE, sessionToken, {
      httpOnly: true,
      sameSite: "lax",
      secure: process.env.NODE_ENV === "production",
      path: "/",
      maxAge: 60 * 60 * 24 * 90,
    });
  }
  if (parts[0] === "logout") {
    response.cookies.set(COOKIE, "", { httpOnly: true, path: "/", maxAge: 0 });
  }
  return response;
}

export async function GET(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  return proxy(request, path);
}

export async function POST(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  return proxy(request, path);
}
