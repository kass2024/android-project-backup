import type { WelcomeCopy } from "@/lib/welcome-copy";

export function WelcomeContent({ copy, onGold = false }: { copy: WelcomeCopy; onGold?: boolean }) {
  return (
    <div>
      <p className={`text-sm font-medium ${onGold ? "text-[#20242C]/80" : "text-[#747780]"}`}>{copy.greeting}</p>
      <h1 className={`mt-1 text-[1.75rem] font-extrabold leading-[1.05] tracking-tight sm:text-4xl ${onGold ? "text-[#20242C]" : "text-[#20242C]"}`}>
        {copy.titleLead}
        <span className={`mt-1 block ${onGold ? "text-white" : "text-[#C99616]"}`}>{copy.titleAccent}</span>
      </h1>
      {onGold ? <p className="mt-4 max-w-sm text-base leading-relaxed text-[#20242C]/85">{copy.tagline}</p> : null}
      {onGold ? null : <p className="mt-3 text-[15px] leading-relaxed text-[#747780]">{copy.description}</p>}
    </div>
  );
}
