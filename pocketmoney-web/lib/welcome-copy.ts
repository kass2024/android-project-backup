export type WelcomeLang = "en" | "sw" | "fr";

export type WelcomeCopy = {
  greeting: string;
  titleLead: string;
  titleAccent: string;
  description: string;
  selectLanguage: string;
  english: string;
  swahili: string;
  french: string;
  continue: string;
  tagline: string;
  languageName: string;
};

const english: WelcomeCopy = {
  greeting: "Welcome to",
  titleLead: "Xander",
  titleAccent: "Pocket Money",
  description: "Choose your language to continue and start managing your pocket money the smarter way.",
  selectLanguage: "Choose your language",
  english: "English",
  swahili: "Kiswahili",
  french: "Français",
  continue: "Continue",
  tagline: "Smart money habits start here.",
  languageName: "English",
};

const swahili: WelcomeCopy = {
  greeting: "Karibu",
  titleLead: "Xander",
  titleAccent: "Pocket Money",
  description: "Chagua lugha ili kuendelea na kuanza kusimamia pesa za mfukoni kwa urahisi zaidi.",
  selectLanguage: "Chagua lugha",
  english: "Kiingereza",
  swahili: "Kiswahili",
  french: "Kifaransa",
  continue: "Endelea",
  tagline: "Tabia nzuri za pesa zinaanza hapa.",
  languageName: "Kiswahili",
};

const french: WelcomeCopy = {
  greeting: "Bienvenue sur",
  titleLead: "Xander",
  titleAccent: "Pocket Money",
  description: "Choisissez votre langue pour continuer et gérer l'argent de poche plus simplement.",
  selectLanguage: "Choisissez votre langue",
  english: "Anglais",
  swahili: "Kiswahili",
  french: "Français",
  continue: "Continuer",
  tagline: "Les bonnes habitudes d'argent commencent ici.",
  languageName: "Français",
};

export function welcomeCopy(lang: string): WelcomeCopy {
  if (lang === "sw" || lang === "rw") return swahili;
  if (lang === "fr") return french;
  return english;
}
