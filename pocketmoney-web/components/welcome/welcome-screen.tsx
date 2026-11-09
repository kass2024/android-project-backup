"use client";

import { useEffect } from "react";
import { Geist } from "next/font/google";
import { Globe } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";

const geist = Geist({ subsets: ["latin"] });
import { BrandLogo } from "@/components/welcome/brand-logo";
import { GoldBackground } from "@/components/welcome/gold-background";
import { LanguageSelector } from "@/components/welcome/language-selector";
import { WelcomeContent } from "@/components/welcome/welcome-content";
import { welcomeCopy, type WelcomeLang } from "@/lib/welcome-copy";

export function WelcomeScreen({
  lang,
  onPick,
  onContinue,
}: {
  lang: string;
  onPick: (code: WelcomeLang) => void;
  onContinue: () => void;
}) {
  const code: WelcomeLang = lang === "sw" || lang === "rw" ? "sw" : lang === "fr" ? "fr" : "en";
  const copy = welcomeCopy(code);
  const reduce = useReducedMotion();
  const rise = reduce ? undefined : { opacity: 0, y: 12 };

  useEffect(() => {
    document.documentElement.lang = code;
  }, [code]);

  return (
    <div className={`${geist.className} min-h-dvh overflow-x-hidden bg-[#FFF9F0] text-[#20242C] [&_img]:h-auto [&_img]:max-w-full`}>
      <p className="sr-only" aria-live="polite">
        {copy.selectLanguage}: {copy.languageName}
      </p>
      <div className="lg:grid lg:min-h-dvh lg:grid-cols-2">
        <section className="relative hidden overflow-hidden bg-[#D3AF37] lg:flex lg:flex-col lg:justify-center lg:px-14 xl:px-20">
          <GoldBackground />
          <motion.div className="relative z-10 max-w-xl" initial={rise} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.45 }}>
            <BrandLogo />
            <div className="mt-8">
              <WelcomeContent copy={copy} onGold />
            </div>
          </motion.div>
        </section>

        <main className="relative mx-auto flex min-h-dvh w-full min-w-0 max-w-[560px] flex-col px-4 pb-[max(1.25rem,env(safe-area-inset-bottom))] pt-[max(0.75rem,env(safe-area-inset-top))] sm:px-6 lg:max-w-none lg:justify-center lg:px-12 xl:px-20">
          <header className="relative min-w-0 overflow-hidden rounded-[28px] bg-[#D3AF37] px-4 pb-6 pt-3 lg:hidden [@media(max-height:700px)]:pb-5">
            <GoldBackground />
            <div className="relative z-10">
              <div className="flex justify-end">
                <LanguageIndicator lang={code} label={copy.languageName} onPick={onPick} />
              </div>
              <motion.div className="mt-2 flex justify-center" initial={rise} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }}>
                <BrandLogo />
              </motion.div>
            </div>
          </header>

          <motion.div
            className="relative z-10 -mt-5 min-w-0 rounded-[28px] bg-white p-4 shadow-[0_16px_40px_rgba(32,36,44,0.08)] sm:p-6 lg:mt-0 lg:bg-transparent lg:p-0 lg:shadow-none"
            initial={rise}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.45, delay: reduce ? 0 : 0.08 }}
          >
            <div className="mb-6 hidden justify-end lg:flex">
              <LanguageIndicator lang={code} label={copy.languageName} onPick={onPick} />
            </div>
            <div className="lg:hidden">
              <WelcomeContent copy={copy} />
            </div>
            <p className="mb-5 hidden text-[15px] leading-relaxed text-[#747780] lg:block">{copy.description}</p>
            <div className="mt-4 lg:mt-0">
              <LanguageSelector lang={code} copy={copy} onPick={onPick} />
            </div>
            <motion.button
              type="button"
              className="mt-4 flex h-12 w-full items-center justify-center rounded-full bg-gradient-to-r from-[#E8C65A] to-[#C99616] text-base font-bold text-[#20242C] shadow-[0_10px_24px_rgba(201,150,22,0.28)] transition hover:brightness-105 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#20242C] active:scale-[0.98]"
              onClick={onContinue}
              whileTap={reduce ? undefined : { scale: 0.98 }}
            >
              {copy.continue}
              <span aria-hidden className="ml-2">→</span>
            </motion.button>
          </motion.div>
        </main>
      </div>
    </div>
  );
}

function LanguageIndicator({
  lang,
  label,
  onPick,
}: {
  lang: WelcomeLang;
  label: string;
  onPick: (lang: WelcomeLang) => void;
}) {
  return (
    <div className="inline-flex rounded-full bg-white/90 p-1 text-sm font-bold text-[#20242C] shadow-sm" role="group" aria-label={label}>
      <button
        type="button"
        aria-pressed={lang === "en"}
        className={`min-h-11 rounded-full px-3 ${lang === "en" ? "bg-[#20242C] text-white" : ""} focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#20242C]`}
        onClick={() => onPick("en")}
      >
        <Globe className="mr-1 inline h-3.5 w-3.5" aria-hidden />
        EN
      </button>
      <button
        type="button"
        aria-pressed={lang === "sw"}
        className={`min-h-11 rounded-full px-3 ${lang === "sw" ? "bg-[#20242C] text-white" : ""} focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#20242C]`}
        onClick={() => onPick("sw")}
      >
        SW
      </button>
      <button
        type="button"
        aria-pressed={lang === "fr"}
        className={`min-h-11 rounded-full px-3 ${lang === "fr" ? "bg-[#20242C] text-white" : ""} focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#20242C]`}
        onClick={() => onPick("fr")}
      >
        FR
      </button>
    </div>
  );
}
