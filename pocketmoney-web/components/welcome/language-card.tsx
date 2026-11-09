import { Check } from "lucide-react";
import type { ReactNode } from "react";

export function LanguageCard({
  name,
  label,
  value,
  checked,
  flag,
  flagLabel,
  onSelect,
}: {
  name: string;
  label: string;
  value: "en" | "sw" | "fr";
  checked: boolean;
  flag: ReactNode;
  flagLabel: string;
  onSelect: () => void;
}) {
  return (
    <label
      className={`relative flex min-h-32 min-w-0 cursor-pointer flex-col items-center justify-center gap-2 rounded-3xl px-2 py-3 transition [@media(max-height:700px)]:min-h-28 [@media(max-height:700px)]:py-2 md:min-h-40 ${
        checked
          ? "border-2 border-[#C99616] bg-[#FFF6E4] shadow-[0_8px_20px_rgba(201,150,22,0.16)]"
          : "border border-[#EFE7DA] bg-white hover:border-[#E8C65A]"
      } focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-[#20242C]`}
    >
      <input type="radio" name={name} value={value} checked={checked} onChange={onSelect} className="sr-only" />
      {checked ? (
        <span className="absolute right-3 top-3 grid h-6 w-6 place-items-center rounded-full bg-[#E8C65A] text-[#20242C]">
          <Check className="h-3.5 w-3.5" aria-hidden />
        </span>
      ) : null}
      <span className="sr-only">{flagLabel}</span>
      {flag}
      <span className="w-full break-words px-1 text-center text-[13px] font-bold leading-tight text-[#20242C] sm:text-sm md:text-base">{label}</span>
    </label>
  );
}

export function UkFlag() {
  return (
    <svg viewBox="0 0 60 60" className="h-12 w-12 md:h-14 md:w-14" aria-hidden>
      <defs>
        <clipPath id="welcome-uk">
          <circle cx="30" cy="30" r="30" />
        </clipPath>
      </defs>
      <g clipPath="url(#welcome-uk)">
        <rect width="60" height="60" fill="#012169" />
        <path d="M0 0 L60 60 M60 0 L0 60" stroke="#fff" strokeWidth="14" />
        <path d="M0 0 L60 60 M60 0 L0 60" stroke="#C8102E" strokeWidth="7" />
        <path d="M30 0 V60 M0 30 H60" stroke="#fff" strokeWidth="22" />
        <path d="M30 0 V60 M0 30 H60" stroke="#C8102E" strokeWidth="12" />
      </g>
    </svg>
  );
}

export function FrFlag() {
  return (
    <svg viewBox="0 0 60 60" className="h-12 w-12 md:h-14 md:w-14" aria-hidden>
      <defs>
        <clipPath id="welcome-fr">
          <circle cx="30" cy="30" r="30" />
        </clipPath>
      </defs>
      <g clipPath="url(#welcome-fr)">
        <rect width="20" height="60" fill="#0055A4" />
        <rect x="20" width="20" height="60" fill="#FFFFFF" />
        <rect x="40" width="20" height="60" fill="#EF4135" />
      </g>
    </svg>
  );
}

export function SwFlag() {
  return (
    <svg viewBox="0 0 60 60" className="h-12 w-12 md:h-14 md:w-14" aria-hidden>
      <defs>
        <clipPath id="welcome-sw">
          <circle cx="30" cy="30" r="30" />
        </clipPath>
      </defs>
      <g clipPath="url(#welcome-sw)">
        <rect width="60" height="60" fill="#1EB53A" />
        <polygon points="0,60 60,60 60,0" fill="#00A3DD" />
        <line x1="0" y1="60" x2="60" y2="0" stroke="#FCD116" strokeWidth="16" />
        <line x1="0" y1="60" x2="60" y2="0" stroke="#000000" strokeWidth="9" />
      </g>
    </svg>
  );
}
