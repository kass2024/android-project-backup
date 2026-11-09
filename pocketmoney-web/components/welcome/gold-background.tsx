export function GoldBackground() {
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden>
      <div className="absolute -left-16 -top-10 h-36 w-64 -rotate-12 rounded-full bg-gradient-to-r from-[#E8C65A] to-[#D4A017] opacity-80" />
      <div className="absolute -right-10 top-6 h-28 w-48 rotate-6 rounded-full bg-[#C99616]/40" />
      <div className="absolute bottom-0 left-0 h-24 w-full bg-gradient-to-r from-[#E8C65A]/70 via-transparent to-[#C99616]/50" />
      <div className="absolute bottom-16 right-10 h-24 w-24 rounded-full border border-white/30" />
      <div className="absolute bottom-28 right-24 h-10 w-10 rounded-full bg-white/20" />
    </div>
  );
}
