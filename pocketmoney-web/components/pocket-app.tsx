"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Button } from "@/components/ui/button";
import { BrandLogo } from "@/components/welcome/brand-logo";
import { WelcomeScreen } from "@/components/welcome/welcome-screen";
import { copyFor, relationLabel, type Copy } from "@/lib/copy";
import { chargeQuote, localPhone, money } from "@/lib/money";
import type { LedgerItem, ParentAccount, Screen, StudentWallet } from "@/lib/types";

async function api<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method: body === undefined ? "GET" : "POST",
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const json = (await response.json().catch(() => ({}))) as T & { message?: string; reason?: string };
  if (!response.ok) throw new Error(json.message || "Request failed.");
  return json;
}

function dayPart(copy: Copy) {
  const hour = new Date().getHours();
  if (hour < 12) return copy.morning;
  if (hour < 17) return copy.afternoon;
  return copy.evening;
}

function PinBoxes({ value, onChange, length = 4 }: { value: string; onChange: (value: string) => void; length?: number }) {
  const slot = Math.min(value.length, length - 1);
  return (
    <div className="group">
      <input
        inputMode="numeric"
        autoComplete="one-time-code"
        value={value}
        maxLength={length}
        onChange={(event) => onChange(event.target.value.replace(/\D/g, "").slice(0, length))}
        className="sr-only"
        id="pin-boxes"
      />
      <label htmlFor="pin-boxes" className="grid grid-cols-4 gap-2">
        {Array.from({ length }).map((_, index) => {
          const filled = Boolean(value[index]);
          const on = index === slot;
          return (
            <span
              key={index}
              className={`flex h-12 items-center justify-center rounded-2xl border-2 text-2xl font-bold text-[#1C2430] transition ${
                filled ? "border-[#C9961B] bg-[#FFF8E6]" : "border-[#E4C56A] bg-white"
              } ${on ? "group-focus-within:border-[#C9961B] group-focus-within:bg-[#FFF4D4] group-focus-within:shadow-[0_0_0_4px_rgba(201,150,27,0.28)]" : ""}`}
            >
              {filled ? "•" : on ? <span className="hidden h-6 w-0.5 animate-pulse rounded-full bg-[#C9961B] group-focus-within:block" /> : ""}
            </span>
          );
        })}
      </label>
    </div>
  );
}

function Field({ label, value, onChange, placeholder, type = "text", maxLength }: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  type?: string;
  maxLength?: number;
}) {
  return (
    <label className="mt-3 block">
      <span className="mb-1 block text-sm font-semibold text-ink">{label}</span>
      <input
        value={value}
        type={type}
        inputMode={type === "tel" || type === "number" ? "numeric" : undefined}
        placeholder={placeholder}
        maxLength={maxLength}
        onChange={(event) => onChange(event.target.value)}
        className="h-12 w-full rounded-2xl border border-line bg-white px-4 outline-none ring-bluex focus:ring-2"
      />
    </label>
  );
}

function StudentBlock({ student, copy }: { student: StudentWallet; copy: Copy }) {
  const role = relationLabel(copy, student.relation);
  return (
    <div className="flex gap-3">
      <div className="h-14 w-14 shrink-0 overflow-hidden rounded-full bg-orangesoft">
        {student.photoUrl ? <img src={student.photoUrl} alt="" className="h-full w-full object-cover" /> : null}
      </div>
      <div className="min-w-0">
        <div className="truncate text-lg font-bold">{student.name}</div>
        {role ? <div className="mt-1 inline-block rounded-lg bg-orangesoft px-2 py-0.5 text-xs font-bold text-orange">{role}</div> : null}
        <div className="text-sm text-muted">{student.regno} · {student.className}</div>
        <div className="text-sm text-muted">{student.school}</div>
      </div>
    </div>
  );
}

function Shell({ title, hint, balance, onBack, lang, onLang, children }: {
  title: string;
  hint?: string;
  balance?: number;
  onBack?: () => void;
  lang: string;
  onLang: (code: "en" | "sw" | "fr") => void;
  children: React.ReactNode;
}) {
  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col bg-paper">
      <header className="bg-gradient-to-b from-bluex to-bluedeep px-5 pb-6 pt-5 text-white">
        <div className="mb-4 flex items-center justify-between">
          <div className="rounded-xl bg-white px-3 py-1 text-sm font-black tracking-wide text-ink">XANDER</div>
          <div className="flex overflow-hidden rounded-full bg-white/15 text-xs font-bold">
            {(["en", "sw", "fr"] as const).map((code) => (
              <button key={code} className={`px-3 py-1 uppercase ${lang === code ? "bg-white text-bluex" : ""}`} onClick={() => onLang(code)}>
                {code}
              </button>
            ))}
          </div>
        </div>
        <div className="flex items-start gap-3">
          {onBack ? (
            <button className="mt-1 text-2xl leading-none" onClick={onBack} aria-label="Back">‹</button>
          ) : null}
          <div>
            <h1 className="text-2xl font-bold">{title}</h1>
            {hint ? <p className="mt-1 text-sm text-white/90">{hint}</p> : null}
          </div>
        </div>
        {balance !== undefined ? <div className="mt-4 text-4xl font-extrabold">{money(balance)}</div> : null}
      </header>
      <main className="flex-1 px-4 py-4">{children}</main>
    </div>
  );
}

function SignInArt({
  lang,
  phone,
  pin,
  busy,
  error,
  onLang,
  onPhone,
  onPin,
  onSubmit,
  onForgot,
  onCreate,
  onBack,
}: {
  lang: string;
  phone: string;
  pin: string;
  busy: boolean;
  error: string;
  onLang: (code: "en" | "sw" | "fr") => void;
  onPhone: (value: string) => void;
  onPin: (value: string) => void;
  onSubmit: () => void;
  onForgot: () => void;
  onCreate: () => void;
  onBack: () => void;
}) {
  const copy = copyFor(lang);
  const [finger, setFinger] = useState("");
  const pinRef = useRef<HTMLInputElement>(null);
  const pinSlot = Math.min(pin.length, 3);
  const ready = phone.replace(/\D/g, "").length >= 10 && pin.length === 4 && !busy;
  return (
    <AuthFrame lang={lang} onLang={onLang} onBack={onBack}>
      <h1 className="text-[1.75rem] font-black leading-none text-[#1C2430] [@media(max-height:700px)]:text-[1.6rem]">
        {lang === "en" ? <>Sign <span className="text-[#C9961B]">in</span></> : copy.signIn}
      </h1>
      <p className="mt-2 text-sm leading-relaxed text-[#6A707C]">{copy.signInLead}</p>
      {error ? <p className="mt-3 rounded-xl bg-red-50 px-3 py-2 text-sm font-semibold text-[#B42318]">{error}</p> : null}
      <label className="mt-3 block text-sm font-bold text-[#12151C]">{copy.phone}</label>
      <div className="mt-2 flex h-12 items-center gap-3 rounded-2xl bg-[#F7F5F2] px-3">
        <span className="grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-[#F8E7C3] text-[#C9961B]">
          <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden><path d="M7 3h4l1 4-2 1a12 12 0 0 0 6 6l1-2 4 1v4a2 2 0 0 1-2 2A16 16 0 0 1 5 5a2 2 0 0 1 2-2z" /></svg>
        </span>
        <input
          aria-label={copy.phone}
          inputMode="tel"
          autoComplete="tel"
          value={phone}
          placeholder="07xxxxxxxx"
          onChange={(event) => onPhone(event.target.value.replace(/\D/g, "").slice(0, 12))}
          className="h-full min-w-0 w-full bg-transparent text-base font-medium text-[#1C1C1C] outline-none placeholder:text-[#A8ADB4]"
        />
      </div>
      <div className="mt-3 text-sm font-bold text-[#12151C]">PIN <span className="font-semibold text-[#6A707C]">{copy.pinHelp}</span></div>
      <div className="group">
      <input
        ref={pinRef}
        aria-label={copy.pin}
        inputMode="numeric"
        autoComplete="one-time-code"
        value={pin}
        onChange={(event) => onPin(event.target.value.replace(/\D/g, "").slice(0, 4))}
        className="sr-only"
      />
      <div className="mt-2 grid grid-cols-4 gap-2">
        {Array.from({ length: 4 }).map((_, index) => {
          const on = index === pinSlot;
          return (
            <button
              key={index}
              type="button"
              className={`flex h-11 items-center justify-center rounded-2xl border-2 border-[#E4C56A] bg-white ${
                on ? "group-focus-within:border-[#C9961B] group-focus-within:bg-[#FFF4D4] group-focus-within:shadow-[0_0_0_4px_rgba(201,150,27,0.28)]" : ""
              }`}
              onClick={() => pinRef.current?.focus()}
            >
              {pin[index] ? (
                <span className="block h-2.5 w-2.5 rounded-full bg-[#C79A27]" />
              ) : (
                <>
                  <span className={`block h-2.5 w-2.5 rounded-full bg-[#D5D8DE] ${on ? "group-focus-within:hidden" : ""}`} />
                  {on ? <span className="hidden h-5 w-0.5 animate-pulse rounded-full bg-[#C9961B] group-focus-within:block" /> : null}
                </>
              )}
            </button>
          );
        })}
      </div>
      </div>
      <button type="button" disabled={!ready} className="mt-4 flex h-12 w-full items-center justify-center rounded-full bg-gradient-to-r from-[#E0B44A] to-[#C9961B] text-base font-bold text-white disabled:opacity-60" onClick={onSubmit}>
        {busy ? copy.pleaseWait : `${copy.signIn} →`}
      </button>
      <div className="my-2 flex items-center gap-3 text-sm text-[#8A909C]">
        <span className="h-px flex-1 bg-[#E6E1D8]" />
        {copy.orWord}
        <span className="h-px flex-1 bg-[#E6E1D8]" />
      </div>
      <button type="button" className="flex h-12 w-full items-center justify-center gap-2 rounded-full bg-[#FDF6EB] text-sm font-bold text-[#141C28]" onClick={() => setFinger(copy.fingerprintLater)}>
        <svg viewBox="0 0 24 24" className="h-5 w-5 text-[#C9961B]" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden><path d="M8 11a4 4 0 0 1 8 0v2M6 14c0-3.3 2.7-6 6-6s6 2.7 6 6v1M9 16v-4M12 18v-7M15 17v-5" /></svg>
        {copy.useFingerprint}
        <span className="text-[#C9961B]">›</span>
      </button>
      {finger ? <p className="mt-2 text-center text-sm font-semibold text-[#8A8175]">{finger}</p> : null}
      <div className="mt-3 flex flex-wrap items-center justify-between gap-x-3 gap-y-1 border-t border-[#F0E8DA] pt-2 text-sm font-bold text-[#C4922A]">
        <button type="button" className="min-h-11 text-left" onClick={onForgot}>{copy.forgotPin}</button>
        <button type="button" className="min-h-11 text-right" onClick={onCreate}>{copy.switchToCreate}</button>
      </div>
    </AuthFrame>
  );
}

function AuthFrame({
  lang,
  onLang,
  onBack,
  children,
}: {
  lang: string;
  onLang: (code: "en" | "sw" | "fr") => void;
  onBack: () => void;
  children: React.ReactNode;
}) {
  const copy = copyFor(lang);
  const [open, setOpen] = useState(false);
  return (
    <div className="min-h-dvh overflow-x-hidden bg-[#FFF9F0] text-[#20242C] [&_img]:h-auto [&_img]:max-w-full">
      <div className="mx-auto flex w-full max-w-[480px] flex-col px-4 pb-[max(1.25rem,env(safe-area-inset-bottom))] pt-[max(0.75rem,env(safe-area-inset-top))]">
        <header className="relative overflow-hidden rounded-[28px] bg-[#D3AF37] px-3 pb-5 pt-2">
          <div className="pointer-events-none absolute inset-0" aria-hidden>
            <div className="absolute -left-8 -top-6 h-20 w-36 -rotate-12 rounded-full bg-white/20" />
            <div className="absolute -right-6 top-2 h-16 w-28 rounded-full bg-[#C99616]/25" />
          </div>
          <div className="relative z-10 flex items-center justify-between gap-3">
            <button type="button" aria-label={copy.backWelcome} className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-white text-2xl font-bold leading-none text-[#1C1C1C] shadow" onClick={onBack}>‹</button>
            <LangPill lang={lang} open={open} onToggle={() => setOpen((value) => !value)} onPick={(code) => { onLang(code); setOpen(false); }} />
          </div>
          <div className="relative z-10 mt-1 flex justify-center">
            <BrandLogo size="sm" className="[@media(max-height:680px)]:w-20" />
          </div>
        </header>
        <div className="relative z-10 -mt-3 min-w-0 rounded-[28px] bg-white px-4 py-3 shadow-[0_16px_40px_rgba(32,36,44,0.08)]">
          {children}
        </div>
      </div>
    </div>
  );
}

function LangPill({ lang, open, onToggle, onPick }: { lang: string; open: boolean; onToggle: () => void; onPick: (code: "en" | "sw" | "fr") => void }) {
  return (
    <div className="relative">
      <button type="button" className="flex min-h-11 items-center gap-2 rounded-full border border-white/70 bg-white/90 px-3 text-sm font-bold text-[#1C1C1C] shadow-sm" onClick={onToggle}>
        <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c2.5 2.8 2.5 15.2 0 18M12 3c-2.5 2.8-2.5 15.2 0 18" /></svg>
        {lang === "sw" ? "SW" : lang === "fr" ? "FR" : "EN"}
        <span className="text-xs">▾</span>
      </button>
      {open ? (
        <div className="absolute right-0 top-11 z-30 w-44 overflow-hidden rounded-2xl border border-[#E7E1D6] bg-white shadow-lg">
          <button type="button" className="block w-full px-4 py-3 text-left text-sm font-semibold" onClick={() => onPick("en")}>English</button>
          <button type="button" className="block w-full px-4 py-3 text-left text-sm font-semibold" onClick={() => onPick("sw")}>Kiswahili</button>
          <button type="button" className="block w-full px-4 py-3 text-left text-sm font-semibold" onClick={() => onPick("fr")}>Français</button>
        </div>
      ) : null}
    </div>
  );
}

function BrandMark() {
  return (
    <div className="flex items-center gap-2 text-white">
      <svg viewBox="0 0 36 36" className="h-9 w-9" aria-hidden>
        <path d="M7 8 L18 18 L7 28" fill="none" stroke="#F2C14E" strokeWidth="3.4" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M29 8 L18 18 L29 28" fill="none" stroke="#fff" strokeWidth="3.4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <div className="leading-none">
        <div className="text-[15px] font-black tracking-[0.16em]">XANDER</div>
        <div className="mt-1 text-[10px] font-medium tracking-wide text-white/80">Pocket Money</div>
      </div>
    </div>
  );
}

function HomeDash({
  lang,
  parent,
  students,
  balance,
  copy,
  error,
  notice,
  adding,
  regno,
  matches,
  found,
  percent,
  busy,
  onLang,
  onAdding,
  onRegno,
  onChoose,
  onPayScreen,
  onActivity,
  onLogout,
  onPay,
}: {
  lang: string;
  parent: ParentAccount;
  students: StudentWallet[];
  balance: number;
  copy: Copy;
  error: string;
  notice: string;
  adding: boolean;
  regno: string;
  matches: StudentWallet[];
  found: StudentWallet | null;
  percent: number;
  busy: boolean;
  onLang: (code: "en" | "sw" | "fr") => void;
  onAdding: (value: boolean) => void;
  onRegno: (value: string) => void;
  onChoose: (student: StudentWallet) => void;
  onPayScreen: (studentId: number) => void;
  onActivity: (studentId: number) => void;
  onLogout: () => void;
  onPay: (student: StudentWallet, amount: number, momo: string, mno: string) => Promise<void>;
}) {
  const [tab, setTab] = useState<"home" | "students" | "activity" | "settings">("home");
  const showList = tab === "home" || tab === "students";
  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-[430px] flex-col bg-[#F3F6FB]">
      <header className="bg-gradient-to-b from-[#2B7BF2] to-[#1565D8] px-5 pb-14 pt-4 text-white">
        <div className="flex items-center justify-between">
          <BrandMark />
          <div className="flex rounded-full bg-white/20 p-1 text-xs font-bold">
            <button type="button" className={`rounded-full px-3 py-1 ${lang === "en" ? "bg-white text-bluex" : "text-white"}`} onClick={() => onLang("en")}>EN</button>
            <button type="button" className={`rounded-full px-3 py-1 ${lang === "sw" ? "bg-white text-bluex" : "text-white"}`} onClick={() => onLang("sw")}>SW</button>
            <button type="button" className={`rounded-full px-3 py-1 ${lang === "fr" ? "bg-white text-bluex" : "text-white"}`} onClick={() => onLang("fr")}>FR</button>
          </div>
        </div>
        <h1 className="mt-5 text-[26px] font-bold leading-tight">{dayPart(copy)}, {parent.name} 👋</h1>
        <p className="mt-1 text-sm text-white/85">{copy.homeHint}</p>
      </header>
      <div className="relative z-10 -mt-8 px-4">
        <div className="flex items-center justify-between rounded-[22px] bg-white px-4 py-3 shadow-[0_10px_24px_rgba(21,101,216,0.12)]">
          <div className="flex items-center gap-3">
            <span className="grid h-11 w-11 place-items-center rounded-2xl bg-[#E8F1FF] text-bluex">
              <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
                <rect x="3" y="6" width="18" height="13" rx="2.5" />
                <path d="M3 10h18" />
                <path d="M7 15h4" />
              </svg>
            </span>
            <div>
              <div className="text-sm text-muted">{copy.walletBalance}</div>
              <div className="text-[26px] font-extrabold leading-none text-ink">{money(balance)}</div>
            </div>
          </div>
          <div className="relative h-16 w-[4.5rem]" aria-hidden>
            <div className="absolute right-1 top-3 h-11 w-14 rounded-2xl bg-[#E7F0FF]" />
            <div className="absolute right-3 top-1 flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-[#8EBEFF] to-[#1565D8] text-white shadow-md">
              <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.7">
                <rect x="3" y="7" width="18" height="12" rx="2" />
                <path d="M3 11h18M16 15h2" />
              </svg>
            </div>
            <span className="absolute bottom-0 right-0 grid h-6 w-6 place-items-center rounded-full bg-[#3B82F6] text-sm font-bold text-white">+</span>
          </div>
        </div>
      </div>
      <main className="flex-1 space-y-3 px-4 pb-24 pt-5">
        {error ? <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-danger">{error}</p> : null}
        {notice ? <p className="whitespace-pre-line rounded-xl bg-emerald-50 px-3 py-2 text-sm text-ok">{notice}</p> : null}
        {showList ? (
          <>
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-lg font-bold text-ink">{parent.role === "agent" ? copy.agentWallets : copy.yourStudents}</h2>
              <button type="button" className="inline-flex items-center gap-1 rounded-full bg-[#E8F1FF] px-3 py-1.5 text-xs font-bold text-bluex" onClick={() => onAdding(true)}>
                <span className="text-base leading-none">+</span> {copy.addAnother}
              </button>
            </div>
            {students.map((student) => (
              <article key={student.id} className="rounded-[22px] bg-white p-4 shadow-[0_8px_24px_rgba(20,32,51,0.06)]">
                <button type="button" className="flex w-full items-center gap-3 text-left" onClick={() => { if (!student.cardDisabled && student.cardLinked) onPayScreen(student.id); }}>
                  <span className="h-12 w-12 shrink-0 overflow-hidden rounded-full bg-[#E7EEF8]">
                    {student.photoUrl ? <img src={student.photoUrl} alt="" className="h-full w-full object-cover" /> : <span className="grid h-full w-full place-items-center font-bold text-bluex">{student.name.slice(0, 1)}</span>}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-[15px] font-bold leading-tight text-ink">{student.name}</span>
                    <span className="mt-0.5 block truncate text-xs text-muted">{student.regno}{student.className ? ` · ${student.className}` : ""}</span>
                    <span className="block truncate text-[11px] font-semibold uppercase tracking-wide text-[#8AA0B8]">{student.school}</span>
                  </span>
                  <span className="text-xl text-[#B7C3D4]">›</span>
                </button>
                <div className="mt-3 flex items-center gap-3 rounded-2xl bg-[#F4F8FF] px-3 py-2.5">
                  <span className="grid h-9 w-9 place-items-center rounded-xl bg-white text-bluex">
                    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
                      <rect x="3" y="6" width="18" height="13" rx="2.5" />
                      <path d="M3 10h18" />
                    </svg>
                  </span>
                  <span>
                    <span className="block text-xs text-muted">{copy.cardBalance}</span>
                    <span className="block text-lg font-extrabold text-ink">{money(student.balance || 0)}</span>
                  </span>
                </div>
                {student.cardDisabled ? <p className="mt-2 text-sm font-bold text-danger">{copy.cardDisabled}</p> : !student.cardLinked ? <p className="mt-2 text-sm text-danger">{copy.noCard}</p> : null}
                <div className="mt-3 grid grid-cols-2 gap-2">
                  <button type="button" className="inline-flex h-11 items-center justify-center gap-2 rounded-2xl bg-orange text-sm font-bold text-white disabled:opacity-50" disabled={student.cardDisabled || !student.cardLinked} onClick={() => onPayScreen(student.id)}>
                    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="currentColor" aria-hidden><path d="M3 11l18-8-8 18-2-7z" /></svg>
                    {copy.sendMoney}
                  </button>
                  <button type="button" className="inline-flex h-11 items-center justify-center gap-2 rounded-2xl border border-[#C9DBF7] bg-white text-sm font-bold text-bluex" onClick={() => onActivity(student.id)}>
                    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden><path d="M4 19V10M10 19V5M16 19v-7M22 19H2" /></svg>
                    {copy.activity}
                  </button>
                </div>
              </article>
            ))}
            {(adding || students.length === 0) ? (
              <section className="rounded-[22px] bg-white p-4 shadow-[0_8px_24px_rgba(20,32,51,0.06)]">
                <h3 className="text-lg font-bold">{students.length === 0 ? copy.addStudent : copy.addAnother}</h3>
                {students.length === 0 ? <p className="mt-1 text-sm text-muted">{copy.addByReg}</p> : null}
                <Field label={copy.regNo} value={regno} onChange={(value) => onRegno(value.replace(/\D/g, "").slice(0, 12))} placeholder="260270886" type="tel" />
                <div className="mt-3 space-y-2">
                  {matches.map((student) => (
                    <button key={student.id} type="button" className="block w-full rounded-2xl border border-line px-3 py-2 text-left" onClick={() => onChoose(student)}>
                      <div className="font-bold">{student.name}</div>
                      <div className="text-xs text-muted">{student.regno} · {student.className}</div>
                    </button>
                  ))}
                </div>
                {found ? (
                  <div className="mt-3">
                    <StudentBlock student={found} copy={copy} />
                    {found.cardDisabled ? <p className="mt-2 text-sm font-bold text-danger">{copy.cardDisabled}</p> : found.cardLinked ? <PayBox copy={copy} student={found} phone={localPhone(parent.phone)} percent={percent} busy={busy} onPay={onPay} /> : <p className="mt-2 text-sm text-danger">{copy.noCard}</p>}
                  </div>
                ) : null}
              </section>
            ) : null}
          </>
        ) : null}
        {tab === "activity" ? (
          <div className="space-y-3">
            <h2 className="text-lg font-bold">{copy.activity}</h2>
            {students.length === 0 ? <p className="text-sm text-muted">{copy.noActivity}</p> : null}
            {students.map((student) => (
              <button key={student.id} type="button" className="flex w-full items-center gap-3 rounded-[22px] bg-white p-4 text-left shadow-[0_8px_24px_rgba(20,32,51,0.06)]" onClick={() => onActivity(student.id)}>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-bold">{student.name}</span>
                  <span className="text-xs text-muted">{student.regno}</span>
                </span>
                <span className="font-bold text-bluex">{money(student.balance || 0)}</span>
              </button>
            ))}
          </div>
        ) : null}
        {tab === "settings" ? (
          <section className="rounded-[22px] bg-white p-4 shadow-[0_8px_24px_rgba(20,32,51,0.06)]">
            <h2 className="text-lg font-bold">{copy.navSettings}</h2>
            <p className="mt-2 font-bold">{parent.name}</p>
            <p className="text-sm text-muted">{localPhone(parent.phone)}</p>
          </section>
        ) : null}
        {tab === "home" ? (
          <button type="button" className="flex w-full items-center gap-3 rounded-[22px] bg-[#FFF6EE] px-4 py-3 text-left" onClick={() => { setTab("students"); onAdding(true); }}>
            <span className="grid h-11 w-11 place-items-center rounded-2xl bg-[#FFE7D2] text-orange">
              <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden><rect x="3" y="6" width="18" height="13" rx="2" /><path d="M3 10h18" /></svg>
            </span>
            <span className="min-w-0 flex-1">
              <span className="block font-bold text-ink">{copy.sendEasily}</span>
              <span className="block text-sm text-muted">{copy.sendEasilyBody}</span>
            </span>
            <span className="text-xl text-[#C9B6A6]">›</span>
          </button>
        ) : null}
        {tab === "home" || tab === "settings" ? (
          <button type="button" className="flex w-full items-center gap-3 rounded-[22px] bg-white px-4 py-3 text-left text-[#E11D48] shadow-[0_8px_24px_rgba(20,32,51,0.06)]" onClick={onLogout}>
            <span className="grid h-10 w-10 place-items-center rounded-xl bg-[#FFF1F3]">
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden><path d="M10 7V5a2 2 0 0 1 2-2h7v18h-7a2 2 0 0 1-2-2v-2" /><path d="M4 12h11M12 8l4 4-4 4" /></svg>
            </span>
            <span className="flex-1 font-bold">{copy.signOut}</span>
            <span className="text-xl text-[#F0B8C2]">›</span>
          </button>
        ) : null}
      </main>
      <nav className="sticky bottom-0 grid grid-cols-4 border-t border-[#E6EDF5] bg-white px-2 pb-2 pt-2 text-[11px] font-semibold text-[#8AA0B8]">
        {([
          ["home", copy.navHome],
          ["students", copy.navStudents],
          ["activity", copy.activity],
          ["settings", copy.navSettings],
        ] as const).map(([id, label]) => (
          <button key={id} type="button" className={`flex flex-col items-center gap-1 py-1 ${tab === id ? "text-bluex" : ""}`} onClick={() => setTab(id)}>
            <NavIcon name={id} />
            <span>{label}</span>
            <span className={`h-0.5 w-6 rounded-full ${tab === id ? "bg-bluex" : "bg-transparent"}`} />
          </button>
        ))}
      </nav>
    </div>
  );
}

function NavIcon({ name }: { name: "home" | "students" | "activity" | "settings" }) {
  const common = "h-5 w-5";
  if (name === "home") {
    return <svg viewBox="0 0 24 24" className={common} fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden><path d="M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1z" /></svg>;
  }
  if (name === "students") {
    return <svg viewBox="0 0 24 24" className={common} fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden><circle cx="9" cy="8" r="3" /><circle cx="17" cy="9" r="2.5" /><path d="M3.5 19c.6-3 2.8-4.5 5.5-4.5S14 16 14.5 19M15 14.5c1.8 0 3.6.8 4.5 3" /></svg>;
  }
  if (name === "activity") {
    return <svg viewBox="0 0 24 24" className={common} fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden><path d="M4 19V10M10 19V5M16 19v-7M22 19H2" /></svg>;
  }
  return <svg viewBox="0 0 24 24" className={common} fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden><circle cx="12" cy="12" r="3" /><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M18.4 5.6 17 7M7 17l-1.4 1.4" /></svg>;
}

export function PocketApp() {
  const [lang, setLang] = useState("en");
  const [ready, setReady] = useState(false);
  const [screen, setScreen] = useState<Screen>({ name: "lang" });
  const [parent, setParent] = useState<ParentAccount | null>(null);
  const [students, setStudents] = useState<StudentWallet[]>([]);
  const [ledger, setLedger] = useState<LedgerItem[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [percent, setPercent] = useState(3);
  const [mode, setMode] = useState<"in" | "create" | "code" | "reset" | "resetCode">("in");
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [pin, setPin] = useState("");
  const [code, setCode] = useState("");
  const [familyCount, setFamilyCount] = useState(0);
  const [adding, setAdding] = useState(false);
  const [regno, setRegno] = useState("");
  const [matches, setMatches] = useState<StudentWallet[]>([]);
  const [found, setFound] = useState<StudentWallet | null>(null);

  const copy = copyFor(lang);

  function chooseLang(codeName: "en" | "sw" | "fr") {
    setLang(codeName);
    localStorage.setItem("pocket-lang", codeName);
    localStorage.setItem("pocket-lang-chosen", "1");
    document.documentElement.lang = codeName;
  }

  async function loadWallets() {
    const json = await api<{ students: StudentWallet[] }>("/api/pocket/wallets");
    setStudents(json.students || []);
    return json.students || [];
  }

  useEffect(() => {
    const stored = localStorage.getItem("pocket-lang");
    const saved = stored === "sw" || stored === "rw" ? "sw" : stored === "fr" ? "fr" : "en";
    setLang(saved);
    api<{ chargePercent?: number }>("/api/pocket/health")
      .then((health) => {
        if (typeof health.chargePercent === "number") setPercent(health.chargePercent);
      })
      .catch(() => undefined);
    api<{ parent: ParentAccount }>("/api/pocket/me")
      .then(async (json) => {
        setParent(json.parent);
        setScreen({ name: "home" });
        await loadWallets().catch(() => undefined);
      })
      .catch(() => setScreen({ name: "lang" }))
      .finally(() => setReady(true));
  }, []);

  useEffect(() => {
    if (screen.name !== "home" && screen.name !== "pay" && screen.name !== "activity") return;
    let stopped = false;
    const tick = () => {
      loadWallets()
        .then((fresh) => {
          if (stopped) return;
          if ((screen.name === "pay" || screen.name === "activity") && !fresh.some((student) => student.id === screen.studentId)) {
            setScreen({ name: "home" });
          }
        })
        .catch((reason: Error) => {
          if (/sign in/i.test(reason.message)) {
            setParent(null);
            setScreen({ name: "auth" });
          }
        });
      if (screen.name === "activity") {
        api<{ transactions: LedgerItem[] }>(`/api/pocket/ledger/${screen.studentId}`)
          .then((json) => {
            if (!stopped) setLedger(json.transactions || []);
          })
          .catch(() => undefined);
      }
    };
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, [screen]);

  useEffect(() => {
    const clean = regno.replace(/\D/g, "");
    if (clean.length < 4 || screen.name !== "home") {
      setMatches([]);
      setFound(null);
      return;
    }
    const timer = window.setTimeout(() => {
      api<{ student: StudentWallet | null; matches: StudentWallet[] }>("/api/pocket/lookup", { regno: clean })
        .then((json) => {
          setFound(json.student);
          setMatches(json.student ? [] : json.matches || []);
        })
        .catch(() => undefined);
    }, 180);
    return () => window.clearTimeout(timer);
  }, [regno, screen.name]);

  const balance = useMemo(() => students.reduce((sum, student) => sum + (student.balance || 0), 0), [students]);
  const activeStudent = students.find((student) => (screen.name === "pay" || screen.name === "activity") && student.id === screen.studentId) || found;

  async function run(task: () => Promise<void>) {
    setBusy(true);
    setError("");
    try {
      await task();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Request failed.");
    } finally {
      setBusy(false);
    }
  }

  async function afterAuth(json: { parent: ParentAccount; students?: StudentWallet[]; registeredAs?: string; relation?: string }) {
    setParent(json.parent);
    setPin("");
    setCode("");
    const fresh = await loadWallets();
    if (json.students && json.students.length > 0 && fresh.length > 0) {
      setScreen({
        name: "family",
        registeredAs: json.registeredAs || "",
        relation: json.relation || "",
        students: fresh,
      });
      return;
    }
    if (fresh.length === 0) setNotice(copy.addByReg);
    setScreen({ name: "home" });
  }

  function submitAuth(event: FormEvent) {
    event.preventDefault();
    if (mode === "in") {
      void run(async () => afterAuth(await api("/api/pocket/login", { phone, pin })));
      return;
    }
    if (mode === "create") {
      void run(async () => afterAuth(await api("/api/pocket/register-start", { name, phone, pin })));
      return;
    }
    if (mode === "code") {
      void run(async () => afterAuth(await api("/api/pocket/register-confirm", { phone, code })));
      return;
    }
    if (mode === "reset") {
      void run(async () => {
        await api("/api/pocket/recover", { phone, appHash: "" });
        setNotice(copy.codeSent);
        setMode("resetCode");
      });
      return;
    }
    void run(async () => afterAuth(await api("/api/pocket/recover-confirm", { phone, code, pin })));
  }

  async function openActivity(studentId: number) {
    setScreen({ name: "activity", studentId });
    setLedger([]);
    const json = await api<{ transactions: LedgerItem[] }>(`/api/pocket/ledger/${studentId}`);
    setLedger(json.transactions || []);
  }

  async function pay(student: StudentWallet, amount: number, momo: string, mno: string) {
    await run(async () => {
      await api("/api/pocket/link", { studentId: student.id, spendPin: "" });
      const started = await api<{ paymentId: number; total?: number; transferFee?: number; withdrawFee?: number; amount?: number; message?: string }>(
        "/api/pocket/topup",
        { studentId: student.id, amount, phone: momo, mno },
      );
      const receive = started.amount || amount;
      const transferFee = started.transferFee || 0;
      const withdrawFee = started.withdrawFee || 0;
      const total = started.total || receive + transferFee + withdrawFee;
      setNotice(copy.feeLine(receive, transferFee, withdrawFee, total));
      for (let attempt = 0; attempt < 40; attempt += 1) {
        await new Promise((resolve) => window.setTimeout(resolve, 3000));
        const status = await api<{ status: string; message?: string }>(`/api/pocket/topup/${started.paymentId}`);
        if (status.status === "paid") {
          setNotice(copy.paidNotice);
          setRegno("");
          setFound(null);
          setAdding(false);
          await loadWallets();
          setScreen({ name: "home" });
          return;
        }
        if (status.status === "failed") throw new Error(status.message || "The payment was not approved.");
      }
      setNotice("Still waiting for Mobile Money. If you approved it, the card balance will update in the student list.");
      setScreen({ name: "home" });
    });
  }

  if (!ready) {
    return <div className="grid min-h-dvh place-items-center text-muted">{copy.pleaseWait}</div>;
  }

  return (
    <AnimatePresence mode="wait">
      <motion.div key={screen.name + mode} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} transition={{ duration: 0.22 }}>
        {screen.name === "lang" ? <WelcomeScreen lang={lang} onPick={chooseLang} onContinue={() => setScreen({ name: "auth" })} /> : null}

        {screen.name === "auth" && mode === "in" ? (
          <SignInArt
            lang={lang}
            phone={phone}
            pin={pin}
            busy={busy}
            error={error}
            onLang={chooseLang}
            onPhone={setPhone}
            onPin={setPin}
            onSubmit={() => void run(async () => afterAuth(await api("/api/pocket/login", { phone, pin })))}
            onForgot={() => { setMode("reset"); setError(""); setNotice(""); setPin(""); setCode(""); }}
            onCreate={() => { setMode("create"); setError(""); setNotice(""); setPin(""); setCode(""); }}
            onBack={() => setScreen({ name: "lang" })}
          />
        ) : null}

        {screen.name === "auth" && mode !== "in" ? (
          <AuthFrame
            lang={lang}
            onLang={chooseLang}
            onBack={() => { setMode("in"); setError(""); setNotice(""); }}
          >
            <h1 className="text-[1.65rem] font-extrabold leading-tight text-[#1C2430]">
              {mode.startsWith("reset") ? copy.resetTitle : copy.createAccount}
            </h1>
            <p className="mt-2 text-sm leading-relaxed text-[#6A707C]">
              {mode === "code" ? copy.createCodeHelp : mode === "reset" ? copy.resetHelp : mode === "resetCode" ? copy.resetCodeHelp : copy.createHelp}
            </p>
            <form onSubmit={submitAuth} className="mt-4">
              {error ? <p className="mb-3 rounded-xl bg-red-50 px-3 py-2 text-sm text-danger">{error}</p> : null}
              {notice ? <p className="mb-3 whitespace-pre-line rounded-xl bg-emerald-50 px-3 py-2 text-sm text-ok">{notice}</p> : null}
              {mode === "code" && familyCount > 0 ? <p className="mb-3 text-sm text-muted">{copy.familyCount(familyCount)}</p> : null}
              {mode === "code" && familyCount === 0 ? <p className="mb-3 text-sm text-muted">{copy.openNumberHint}</p> : null}
              {mode === "create" ? <Field label={copy.yourName} value={name} onChange={setName} /> : null}
              {mode !== "code" && mode !== "resetCode" ? <Field label={copy.phone} value={phone} onChange={setPhone} placeholder="07xxxxxxxx" type="tel" /> : null}
              {mode === "create" || mode === "resetCode" ? (
                <div className="mt-3">
                  <div className="mb-1 text-sm font-semibold">{copy.pin} <span className="font-normal text-muted">{copy.pinHelp}</span></div>
                  <PinBoxes value={pin} onChange={setPin} />
                </div>
              ) : null}
              {mode === "code" || mode === "resetCode" ? <Field label={copy.smsCode} value={code} onChange={(value) => setCode(value.replace(/\D/g, "").slice(0, 6))} type="tel" maxLength={6} /> : null}
              <Button className="mt-4 rounded-full bg-gradient-to-r from-[#E0B44A] to-[#C9961B] text-white" type="submit" disabled={busy}>
                {busy ? copy.pleaseWait : mode === "create" ? copy.createAccount : mode === "code" ? copy.verifyCreate : mode === "reset" ? copy.sendCode : copy.savePin}
              </Button>
              <div className="mt-2 border-t border-[#F0E8DA] pt-2 text-sm font-bold">
                <button type="button" className="min-h-11 text-[#C4922A]" onClick={() => { setMode("in"); setError(""); setNotice(""); }}>{copy.backToSignIn}</button>
              </div>
            </form>
          </AuthFrame>
        ) : null}

        {screen.name === "family" ? (
          <Shell title={copy.familyTitle} hint={copy.familyHint} lang={lang} onLang={chooseLang}>
            <div className="space-y-3">
              {screen.students.map((student) => (
                <article key={student.id} className="rounded-3xl bg-white p-4 shadow-card">
                  <StudentBlock student={student} copy={copy} />
                  <div className="mt-3 text-2xl font-extrabold text-bluex">{money(student.balance || 0)}</div>
                  {student.cardDisabled ? <p className="mt-2 text-sm font-bold text-danger">{copy.cardDisabled}</p> : !student.cardLinked ? <p className="mt-2 text-sm text-danger">{copy.noCard}</p> : null}
                </article>
              ))}
            </div>
            <Button className="mt-4" onClick={() => setScreen({ name: "home" })}>{copy.continueAction}</Button>
          </Shell>
        ) : null}

        {screen.name === "home" && parent ? (
          <HomeDash
            lang={lang}
            parent={parent}
            students={students}
            balance={balance}
            copy={copy}
            error={error}
            notice={notice}
            adding={adding}
            regno={regno}
            matches={matches}
            found={found}
            percent={percent}
            busy={busy}
            onLang={chooseLang}
            onAdding={setAdding}
            onRegno={setRegno}
            onChoose={(student) => { setFound(student); setRegno(student.regno); }}
            onPayScreen={(studentId) => setScreen({ name: "pay", studentId })}
            onActivity={(studentId) => void openActivity(studentId)}
            onLogout={() => void run(async () => {
              await api("/api/pocket/logout", {});
              setParent(null);
              setStudents([]);
              setScreen({ name: "auth" });
            })}
            onPay={pay}
          />
        ) : null}

        {screen.name === "pay" && activeStudent && parent ? (
          <Shell title={activeStudent.name} hint={activeStudent.regno} balance={activeStudent.balance || 0} onBack={() => setScreen({ name: "home" })} lang={lang} onLang={chooseLang}>
            <section className="rounded-3xl bg-white p-4 shadow-card">
              {error ? <p className="mb-3 rounded-xl bg-red-50 px-3 py-2 text-sm text-danger">{error}</p> : null}
              {notice ? <p className="mb-3 whitespace-pre-line rounded-xl bg-emerald-50 px-3 py-2 text-sm text-ok">{notice}</p> : null}
              <StudentBlock student={activeStudent} copy={copy} />
              <p className="mt-3 text-sm text-muted">{copy.cardOnly}</p>
              {activeStudent.cardDisabled ? <p className="mt-2 text-sm font-bold text-danger">{copy.cardDisabled}</p> : activeStudent.cardLinked ? <PayBox copy={copy} student={activeStudent} phone={localPhone(parent.phone)} percent={percent} busy={busy} onPay={pay} /> : <p className="mt-2 text-sm text-danger">{copy.noCard}</p>}
            </section>
          </Shell>
        ) : null}

        {screen.name === "activity" && activeStudent ? (
          <Shell title={activeStudent.name} hint={copy.activity} balance={activeStudent.balance || 0} onBack={() => setScreen({ name: "home" })} lang={lang} onLang={chooseLang}>
            <section className="rounded-3xl bg-white p-4 shadow-card">
              <StudentBlock student={activeStudent} copy={copy} />
              {activeStudent.cardDisabled ? <p className="mt-2 text-sm font-bold text-danger">{copy.cardDisabled}</p> : !activeStudent.cardLinked ? <p className="mt-2 text-sm text-danger">{copy.noCard}</p> : null}
            </section>
            <section className="mt-3 rounded-3xl bg-white p-4 shadow-card">
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-lg font-bold">{copy.recentActivity}</h2>
                <button type="button" className="text-sm font-bold text-bluex" onClick={() => setScreen({ name: "home" })}>{copy.backToDashboard}</button>
              </div>
              {ledger.length === 0 ? <p className="mt-2 text-sm text-muted">{busy ? copy.loading : copy.noActivity}</p> : null}
              <div className="mt-2 space-y-2">
                {ledger.map((item) => (
                  <article key={item.id} className="rounded-2xl border border-line p-3">
                    <div className="flex justify-between gap-3 font-bold">
                      <span>{item.label}</span>
                      <span className={item.type === 0 || item.type === 3 ? "text-ok" : "text-danger"}>{item.type === 0 || item.type === 3 ? "+" : "−"}{money(item.amount)}</span>
                    </div>
                    {item.lines && item.lines.length > 0 ? (
                      <ul className="mt-1 text-sm text-ink">
                        {item.lines.map((line, index) => (
                          <li key={`${item.id}-${index}`}>{copy.took}: {line.name}{line.unit ? ` ${line.unit}` : ""} × {line.qty}</li>
                        ))}
                      </ul>
                    ) : item.detail ? <p className="mt-1 text-sm text-ink">{item.type === 2 ? `${copy.withdrawReason}: ${item.detail}` : item.detail}</p> : null}
                    {item.type === 2 && item.reason && item.reason !== item.detail ? <p className="mt-1 text-sm text-ink">{copy.withdrawReason}: {item.reason}</p> : null}
                    <p className="text-xs text-muted">{item.createdAt}{item.merchant ? ` · ${item.merchant}` : ""}</p>
                    <p className="text-xs capitalize text-muted">{item.status}{item.balance !== null ? ` · ${money(item.balance)}` : ""}</p>
                    {item.status === "failed" && item.message ? <p className="text-xs text-danger">{item.message}</p> : null}
                  </article>
                ))}
              </div>
              <button type="button" className="mt-4 h-12 w-full rounded-2xl border border-[#C9DBF7] text-sm font-bold text-bluex" onClick={() => setScreen({ name: "home" })}>{copy.backToDashboard}</button>
            </section>
          </Shell>
        ) : null}
      </motion.div>
    </AnimatePresence>
  );
}

function PayBox({ copy, student, phone, percent, busy, onPay }: {
  copy: Copy;
  student: StudentWallet;
  phone: string;
  percent: number;
  busy: boolean;
  onPay: (student: StudentWallet, amount: number, phone: string, mno: string) => Promise<void>;
}) {
  const [custom, setCustom] = useState("");
  const [momo, setMomo] = useState(phone);
  const amount = Number(custom) || 0;
  const quote = chargeQuote(amount, percent);
  const ready = amount >= 200 && amount <= 500000 && momo.trim() !== "";
  return (
    <div className="mt-3">
      <label className="block">
        <span className="mb-1 block text-sm font-semibold text-ink">{copy.amount}</span>
        <span className="flex h-14 items-center rounded-2xl border border-line bg-white px-4 ring-bluex focus-within:ring-2">
          <input
            value={custom}
            inputMode="numeric"
            autoComplete="off"
            placeholder="0"
            maxLength={6}
            onChange={(event) => setCustom(event.target.value.replace(/\D/g, "").slice(0, 6))}
            className="h-full w-full bg-transparent text-lg font-bold outline-none"
          />
          <span className="font-bold text-muted">RWF</span>
        </span>
      </label>
      <p className="mt-1 text-xs text-muted">{copy.amountHint}</p>
      <div className="mt-3 flex items-center justify-center rounded-2xl border border-[#FFCC00] bg-[#FFCC00] px-3 py-3 font-bold">MTN MoMo</div>
      <Field label={copy.momoNumber} value={momo} onChange={setMomo} placeholder="07xxxxxxxx" type="tel" />
      {amount >= 200 && amount <= 500000 && quote.fee > 0 ? <p className="mt-2 whitespace-pre-line text-sm text-muted">{copy.feeLine(amount, quote.transferFee, quote.withdrawFee, quote.debit)}</p> : null}
      <Button className="mt-3" disabled={busy || !ready} onClick={() => void onPay(student, amount, momo, "mtn")}>
        {busy ? copy.pleaseWait : `${copy.sendMoney} ${ready ? money(amount) : ""}`}
      </Button>
    </div>
  );
}
