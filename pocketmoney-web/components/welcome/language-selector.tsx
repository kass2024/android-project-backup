import type { WelcomeCopy, WelcomeLang } from "@/lib/welcome-copy";
import { FrFlag, LanguageCard, SwFlag, UkFlag } from "@/components/welcome/language-card";

export function LanguageSelector({
  lang,
  copy,
  onPick,
}: {
  lang: WelcomeLang;
  copy: WelcomeCopy;
  onPick: (lang: WelcomeLang) => void;
}) {
  return (
    <fieldset className="min-w-0 border-0 p-0">
      <legend className="mb-3 text-sm font-bold text-[#20242C]">{copy.selectLanguage}</legend>
      <div className="grid grid-cols-3 gap-2 sm:gap-3">
        <LanguageCard
          name="welcome-language"
          value="en"
          label={copy.english}
          flagLabel="United Kingdom"
          checked={lang === "en"}
          onSelect={() => onPick("en")}
          flag={<UkFlag />}
        />
        <LanguageCard
          name="welcome-language"
          value="sw"
          label={copy.swahili}
          flagLabel="Tanzania"
          checked={lang === "sw"}
          onSelect={() => onPick("sw")}
          flag={<SwFlag />}
        />
        <LanguageCard
          name="welcome-language"
          value="fr"
          label={copy.french}
          flagLabel="France"
          checked={lang === "fr"}
          onSelect={() => onPick("fr")}
          flag={<FrFlag />}
        />
      </div>
    </fieldset>
  );
}
